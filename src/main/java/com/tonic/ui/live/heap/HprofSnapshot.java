package com.tonic.ui.live.heap;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A parsed HPROF heap dump that indexes the file in one pass and decodes instance fields on demand, so memory stays bounded; it owns and deletes the dump on close. */
public final class HprofSnapshot implements Closeable
{

    private static final int T_OBJECT = 2, T_BOOLEAN = 4, T_CHAR = 5, T_FLOAT = 6, T_DOUBLE = 7,
            T_BYTE = 8, T_SHORT = 9, T_INT = 10, T_LONG = 11;

    private final File file;
    private final RandomAccessFile raf;
    private final int idSize;

    private final Map<Long, String> strings = new HashMap<>();
    private final Map<Long, Long> classNameStringId = new HashMap<>();
    private final Map<Long, ClassDef> classDefs = new HashMap<>();
    private final Map<Long, long[]> instanceIndex = new HashMap<>();
    private final Map<Long, List<Long>> instancesByClass = new HashMap<>();
    private final Map<Long, long[]> primArrayIndex = new HashMap<>();
    private final Map<String, Long> nameToClassObjId = new HashMap<>();
    private final Map<Long, String> classNameByObjId = new HashMap<>();

    private static final class ClassDef
    {
        long superId;
        long[] fieldNameIds;
        int[] fieldTypes;
    }

    /** A decoded field value for display. */
    public static final class FieldValue
    {
        public final String name;
        public final String type;
        public final String display;
        public final long refId;

        FieldValue(String name, String type, String display, long refId)
        {
            this.name = name;
            this.type = type;
            this.display = display;
            this.refId = refId;
        }
    }

    /** A decoded instance: its class name and ordered field values. */
    public static final class InstanceData
    {
        public final String className;
        public final List<FieldValue> fields;

        InstanceData(String className, List<FieldValue> fields)
        {
            this.className = className;
            this.fields = fields;
        }
    }

    /**
     * Indexes a heap dump and keeps the file open for field decoding.
     *
     * @param hprof the HPROF file, deleted when the snapshot is closed
     * @throws IOException if the file cannot be read or parsed
     */
    public HprofSnapshot(File hprof) throws IOException
    {
        this.file = hprof;
        try (InputStream raw = new BufferedInputStream(new FileInputStream(hprof), 1 << 20))
        {
            Reader r = new Reader(raw);
            this.idSize = readHeader(r);
            indexRecords(r);
        }
        resolveClassNames();
        this.raf = new RandomAccessFile(hprof, "r");
    }

    /**
     * Lists the instances of exactly one class, not its subclasses.
     *
     * @param internalName the class's internal name, with slashes
     * @return the instances' object ids; empty if the class is not in the dump
     */
    public List<Long> instancesOf(String internalName)
    {
        Long cid = nameToClassObjId.get(internalName);
        if (cid == null)
        {
            return Collections.emptyList();
        }
        return instancesByClass.getOrDefault(cid, Collections.emptyList());
    }

    /**
     * Counts the instances of exactly one class, not its subclasses.
     *
     * @param internalName the class's internal name, with slashes
     * @return the instance count; 0 if the class is not in the dump
     */
    public int countOf(String internalName)
    {
        return instancesOf(internalName).size();
    }

    /**
     * Decodes an instance's fields, including those declared by its superclasses.
     *
     * @param objId the instance's object id
     * @return the instance's class name and field values; no fields if the id is not an indexed instance
     * @throws IOException if reading the dump fails
     */
    public synchronized InstanceData decode(long objId) throws IOException
    {
        long[] idx = instanceIndex.get(objId);
        if (idx == null)
        {
            return new InstanceData(labelClass(objId), Collections.emptyList());
        }
        byte[] blob = readBlob(idx[1], (int) idx[2]);
        Cursor c = new Cursor(blob);
        List<FieldValue> out = new ArrayList<>();
        long cid = idx[0];
        while (cid != 0)
        {
            ClassDef def = classDefs.get(cid);
            if (def == null)
            {
                break;
            }
            for (int i = 0; i < def.fieldTypes.length; i++)
            {
                String name = strings.getOrDefault(def.fieldNameIds[i], "?");
                out.add(readField(name, def.fieldTypes[i], c));
            }
            cid = def.superId;
        }
        return new InstanceData(classNameByObjId.getOrDefault(idx[0], "?"), out);
    }

    /**
     * Returns a short display label for an object.
     *
     * @param objId the object id
     * @return "null" for 0, quoted text for a string, a type and length for a primitive array, else the simple class name and hex id
     */
    public String labelFor(long objId)
    {
        if (objId == 0)
        {
            return "null";
        }
        long[] idx = instanceIndex.get(objId);
        if (idx != null)
        {
            String cls = classNameByObjId.getOrDefault(idx[0], "?");
            if (cls.equals("java/lang/String"))
            {
                String s = stringText(objId);
                return s == null ? "String@" + Long.toHexString(objId) : '"' + truncate(s) + '"';
            }
            return simpleName(cls) + "@" + Long.toHexString(objId);
        }
        long[] arr = primArrayIndex.get(objId);
        if (arr != null)
        {
            return typeName((int) arr[2]) + "[" + arr[1] + "]";
        }
        return "@" + Long.toHexString(objId);
    }

    @Override
    public void close()
    {
        try
        {
            raf.close();
        }
        catch (IOException ignored)
        {
        }
        file.delete();
    }

    private FieldValue readField(String name, int type, Cursor c)
    {
        switch (type)
        {
            case T_OBJECT:
            {
                long ref = c.id(idSize);
                if (ref == 0)
                {
                    return new FieldValue(name, "ref", "null", 0);
                }
                return new FieldValue(name, "ref", labelFor(ref), ref);
            }
            case T_BOOLEAN:
                return new FieldValue(name, "boolean", c.u1() != 0 ? "true" : "false", 0);
            case T_BYTE:
                return new FieldValue(name, "byte", Byte.toString((byte) c.u1()), 0);
            case T_CHAR:
                return new FieldValue(name, "char", "'" + (char) c.u2() + "'", 0);
            case T_SHORT:
                return new FieldValue(name, "short", Short.toString((short) c.u2()), 0);
            case T_INT:
                return new FieldValue(name, "int", Integer.toString(c.i4()), 0);
            case T_LONG:
                return new FieldValue(name, "long", Long.toString(c.i8()), 0);
            case T_FLOAT:
                return new FieldValue(name, "float", Float.toString(Float.intBitsToFloat(c.i4())), 0);
            case T_DOUBLE:
                return new FieldValue(name, "double", Double.toString(Double.longBitsToDouble(c.i8())), 0);
            default:
                return new FieldValue(name, "?", "?", 0);
        }
    }

    private String stringText(long objId)
    {
        try
        {
            long[] idx = instanceIndex.get(objId);
            if (idx == null)
            {
                return null;
            }
            byte[] blob = readBlob(idx[1], (int) idx[2]);
            Cursor c = new Cursor(blob);
            long valueRef = 0;
            int coder = -1;
            long cid = idx[0];
            while (cid != 0)
            {
                ClassDef def = classDefs.get(cid);
                if (def == null)
                {
                    break;
                }
                for (int i = 0; i < def.fieldTypes.length; i++)
                {
                    String fn = strings.get(def.fieldNameIds[i]);
                    int t = def.fieldTypes[i];
                    if (t == T_OBJECT)
                    {
                        long ref = c.id(idSize);
                        if ("value".equals(fn))
                        {
                            valueRef = ref;
                        }
                    }
                    else if (t == T_BYTE)
                    {
                        int v = c.u1();
                        if ("coder".equals(fn))
                        {
                            coder = v;
                        }
                    }
                    else
                    {
                        c.skip(typeSize(t, idSize));
                    }
                }
                cid = def.superId;
            }
            if (valueRef == 0)
            {
                return null;
            }
            long[] arr = primArrayIndex.get(valueRef);
            if (arr == null)
            {
                return null;
            }
            int count = (int) arr[1];
            int elem = (int) arr[2];
            byte[] data = readBlob(arr[0], count * typeSize(elem, idSize));
            if (elem == T_CHAR)
            {
                char[] chars = new char[count];
                for (int i = 0; i < count; i++)
                {
                    chars[i] = (char) (((data[i * 2] & 0xFF) << 8) | (data[i * 2 + 1] & 0xFF));
                }
                return new String(chars);
            }
            if (coder == 1)
            {
                return new String(data, StandardCharsets.UTF_16LE);
            }
            return new String(data, StandardCharsets.ISO_8859_1);
        }
        catch (IOException e)
        {
            return null;
        }
    }

    private synchronized byte[] readBlob(long offset, int len) throws IOException
    {
        raf.seek(offset);
        byte[] b = new byte[len];
        raf.readFully(b);
        return b;
    }

    private int readHeader(Reader r) throws IOException
    {
        int b;
        while ((b = r.u1()) != 0)
        {
            if (b < 0)
            {
                throw new EOFException();
            }
        }
        int size = (int) r.u4();
        r.skip(8);
        return size;
    }

    private void indexRecords(Reader r) throws IOException
    {
        while (true)
        {
            int tag;
            try
            {
                tag = r.u1();
            }
            catch (EOFException eof)
            {
                return;
            }
            r.u4();
            long len = r.u4();
            switch (tag)
            {
                case 0x01:
                {
                    long id = r.id(idSize);
                    strings.put(id, r.utf8((int) (len - idSize)));
                    break;
                }
                case 0x02:
                {
                    r.u4();
                    long classObjId = r.id(idSize);
                    r.u4();
                    long nameId = r.id(idSize);
                    classNameStringId.put(classObjId, nameId);
                    break;
                }
                case 0x0C:
                case 0x1C:
                    indexHeapSegment(r, len);
                    break;
                default:
                    r.skip(len);
                    break;
            }
        }
    }

    private void indexHeapSegment(Reader r, long len) throws IOException
    {
        long end = r.pos + len;
        while (r.pos < end)
        {
            int sub = r.u1();
            switch (sub)
            {
                case 0x21:
                {
                    long objId = r.id(idSize);
                    r.u4();
                    long classObjId = r.id(idSize);
                    int numBytes = (int) r.u4();
                    long offset = r.pos;
                    instanceIndex.put(objId, new long[]{classObjId, offset, numBytes});
                    instancesByClass.computeIfAbsent(classObjId, k -> new ArrayList<>()).add(objId);
                    r.skip(numBytes);
                    break;
                }
                case 0x23:
                {
                    long objId = r.id(idSize);
                    r.u4();
                    int count = (int) r.u4();
                    int elemType = r.u1();
                    long offset = r.pos;
                    primArrayIndex.put(objId, new long[]{offset, count, elemType});
                    r.skip((long) count * typeSize(elemType, idSize));
                    break;
                }
                case 0x22:
                {
                    r.id(idSize);
                    r.u4();
                    int count = (int) r.u4();
                    r.id(idSize);
                    r.skip((long) count * idSize);
                    break;
                }
                case 0x20:
                    readClassDump(r);
                    break;
                case 0xFF:
                    r.id(idSize);
                    break;
                case 0x01:
                    r.id(idSize);
                    r.id(idSize);
                    break;
                case 0x02:
                case 0x03:
                case 0x08:
                    r.id(idSize);
                    r.u4();
                    r.u4();
                    break;
                case 0x04:
                case 0x06:
                    r.id(idSize);
                    r.u4();
                    break;
                case 0x05:
                case 0x07:
                    r.id(idSize);
                    break;
                default:
                    throw new IOException("unknown heap sub-record 0x" + Integer.toHexString(sub) + " at " + r.pos);
            }
        }
    }

    private void readClassDump(Reader r) throws IOException
    {
        long classObjId = r.id(idSize);
        r.u4();
        long superId = r.id(idSize);
        r.id(idSize);
        r.id(idSize);
        r.id(idSize);
        r.id(idSize);
        r.id(idSize);
        r.u4();
        int cpCount = r.u2();
        for (int i = 0; i < cpCount; i++)
        {
            r.u2();
            int type = r.u1();
            r.skip(typeSize(type, idSize));
        }
        int staticCount = r.u2();
        for (int i = 0; i < staticCount; i++)
        {
            r.id(idSize);
            int type = r.u1();
            r.skip(typeSize(type, idSize));
        }
        int instCount = r.u2();
        long[] names = new long[instCount];
        int[] types = new int[instCount];
        for (int i = 0; i < instCount; i++)
        {
            names[i] = r.id(idSize);
            types[i] = r.u1();
        }
        ClassDef def = new ClassDef();
        def.superId = superId;
        def.fieldNameIds = names;
        def.fieldTypes = types;
        classDefs.put(classObjId, def);
    }

    private void resolveClassNames()
    {
        for (Map.Entry<Long, Long> e : classNameStringId.entrySet())
        {
            String name = strings.get(e.getValue());
            if (name == null)
            {
                continue;
            }
            String slashed = name.replace('.', '/');
            classNameByObjId.put(e.getKey(), slashed);
            nameToClassObjId.put(slashed, e.getKey());
        }
    }

    private String labelClass(long objId)
    {
        long[] idx = instanceIndex.get(objId);
        return idx == null ? "?" : classNameByObjId.getOrDefault(idx[0], "?");
    }

    private static int typeSize(int type, int idSize)
    {
        switch (type)
        {
            case T_OBJECT:
                return idSize;
            case T_BOOLEAN:
            case T_BYTE:
                return 1;
            case T_CHAR:
            case T_SHORT:
                return 2;
            case T_FLOAT:
            case T_INT:
                return 4;
            case T_DOUBLE:
            case T_LONG:
                return 8;
            default:
                return 0;
        }
    }

    private static String typeName(int type)
    {
        switch (type)
        {
            case T_OBJECT:
                return "object";
            case T_BOOLEAN:
                return "boolean";
            case T_CHAR:
                return "char";
            case T_FLOAT:
                return "float";
            case T_DOUBLE:
                return "double";
            case T_BYTE:
                return "byte";
            case T_SHORT:
                return "short";
            case T_INT:
                return "int";
            case T_LONG:
                return "long";
            default:
                return "?";
        }
    }

    private static String simpleName(String internal)
    {
        int slash = internal.lastIndexOf('/');
        return slash >= 0 ? internal.substring(slash + 1) : internal;
    }

    private static String truncate(String s)
    {
        return s.length() <= 64 ? s : s.substring(0, 64) + "...";
    }

    private static final class Reader
    {
        private final DataInputStream in;
        long pos;

        Reader(InputStream s)
        {
            this.in = new DataInputStream(s);
        }

        int u1() throws IOException
        {
            int b = in.read();
            if (b < 0)
            {
                throw new EOFException();
            }
            pos++;
            return b;
        }

        int u2() throws IOException
        {
            int v = in.readUnsignedShort();
            pos += 2;
            return v;
        }

        long u4() throws IOException
        {
            long v = in.readInt() & 0xFFFFFFFFL;
            pos += 4;
            return v;
        }

        long u8() throws IOException
        {
            long v = in.readLong();
            pos += 8;
            return v;
        }

        long id(int idSize) throws IOException
        {
            return idSize == 8 ? u8() : u4();
        }

        void skip(long n) throws IOException
        {
            long left = n;
            while (left > 0)
            {
                long s = in.skip(left);
                if (s <= 0)
                {
                    if (in.read() < 0)
                    {
                        throw new EOFException();
                    }
                    s = 1;
                }
                left -= s;
            }
            pos += n;
        }

        String utf8(int n) throws IOException
        {
            byte[] b = new byte[n];
            in.readFully(b);
            pos += n;
            return new String(b, StandardCharsets.UTF_8);
        }
    }

    private static final class Cursor
    {
        private final byte[] b;
        private int p;

        Cursor(byte[] b)
        {
            this.b = b;
        }

        int u1()
        {
            return b[p++] & 0xFF;
        }

        int u2()
        {
            return ((b[p++] & 0xFF) << 8) | (b[p++] & 0xFF);
        }

        int i4()
        {
            return ((b[p++] & 0xFF) << 24) | ((b[p++] & 0xFF) << 16) | ((b[p++] & 0xFF) << 8) | (b[p++] & 0xFF);
        }

        long i8()
        {
            long v = 0;
            for (int i = 0; i < 8; i++)
            {
                v = (v << 8) | (b[p++] & 0xFF);
            }
            return v;
        }

        long id(int idSize)
        {
            return idSize == 8 ? i8() : (i4() & 0xFFFFFFFFL);
        }

        void skip(int n)
        {
            p += n;
        }
    }
}
