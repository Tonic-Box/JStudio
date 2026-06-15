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

/**
 * A parsed HPROF heap-dump snapshot. The dump file stays on disk; one streaming pass builds an in-memory
 * index (string table, class field layouts, and an object index of {@code objId -> class + file offset}),
 * and an instance's field values are decoded on demand by seeking into the file. So memory stays bounded
 * regardless of heap size, and listing instances of a class is an index lookup.
 *
 * <p>Supports browsing instances of a class, decoding their fields (primitives, references, and
 * {@code java.lang.String} text), and resolving references for navigation. {@link #close()} closes the file
 * and deletes it (the snapshot owns the temp dump).
 */
public final class HprofSnapshot implements Closeable {

    // HPROF basic type tags.
    private static final int T_OBJECT = 2, T_BOOLEAN = 4, T_CHAR = 5, T_FLOAT = 6, T_DOUBLE = 7,
            T_BYTE = 8, T_SHORT = 9, T_INT = 10, T_LONG = 11;

    private final File file;
    private final RandomAccessFile raf;
    private final int idSize;

    private final Map<Long, String> strings = new HashMap<>();
    private final Map<Long, Long> classNameStringId = new HashMap<>();   // classObjId -> nameStringId
    private final Map<Long, ClassDef> classDefs = new HashMap<>();       // classObjId -> layout
    private final Map<Long, long[]> instanceIndex = new HashMap<>();     // objId -> {classObjId, offset, len}
    private final Map<Long, List<Long>> instancesByClass = new HashMap<>();
    private final Map<Long, long[]> primArrayIndex = new HashMap<>();    // objId -> {offset, count, elemType}
    private final Map<String, Long> nameToClassObjId = new HashMap<>();  // slashed internal name -> classObjId
    private final Map<Long, String> classNameByObjId = new HashMap<>();  // classObjId -> slashed internal name

    /** Field layout of a class: its own instance fields (in declared order) and its superclass. */
    private static final class ClassDef {
        long superId;
        long[] fieldNameIds;
        int[] fieldTypes;
    }

    /** A decoded field value for display. */
    public static final class FieldValue {
        public final String name;
        public final String type;
        public final String display;
        public final long refId; // referenced object id (0 = not a reference / null)

        FieldValue(String name, String type, String display, long refId) {
            this.name = name;
            this.type = type;
            this.display = display;
            this.refId = refId;
        }
    }

    /** A decoded instance: its class name and ordered field values. */
    public static final class InstanceData {
        public final String className;
        public final List<FieldValue> fields;

        InstanceData(String className, List<FieldValue> fields) {
            this.className = className;
            this.fields = fields;
        }
    }

    public HprofSnapshot(File hprof) throws IOException {
        this.file = hprof;
        try (InputStream raw = new BufferedInputStream(new FileInputStream(hprof), 1 << 20)) {
            Reader r = new Reader(raw);
            this.idSize = readHeader(r);
            indexRecords(r);
        }
        resolveClassNames();
        this.raf = new RandomAccessFile(hprof, "r");
    }

    // ---- public API -------------------------------------------------------------------------------

    /** Instance object ids of {@code internalName} (exact class, slashed form). */
    public List<Long> instancesOf(String internalName) {
        Long cid = nameToClassObjId.get(internalName);
        if (cid == null) {
            return Collections.emptyList();
        }
        return instancesByClass.getOrDefault(cid, Collections.emptyList());
    }

    public int countOf(String internalName) {
        return instancesOf(internalName).size();
    }

    /** Decode an instance's fields (walking the superclass chain). */
    public synchronized InstanceData decode(long objId) throws IOException {
        long[] idx = instanceIndex.get(objId);
        if (idx == null) {
            return new InstanceData(labelClass(objId), Collections.emptyList());
        }
        byte[] blob = readBlob(idx[1], (int) idx[2]);
        Cursor c = new Cursor(blob);
        List<FieldValue> out = new ArrayList<>();
        long cid = idx[0];
        while (cid != 0) {
            ClassDef def = classDefs.get(cid);
            if (def == null) {
                break;
            }
            for (int i = 0; i < def.fieldTypes.length; i++) {
                String name = strings.getOrDefault(def.fieldNameIds[i], "?");
                out.add(readField(name, def.fieldTypes[i], c));
            }
            cid = def.superId;
        }
        return new InstanceData(classNameByObjId.getOrDefault(idx[0], "?"), out);
    }

    /** A short label for an instance (class@hexId, or the text for java.lang.String). */
    public String labelFor(long objId) {
        if (objId == 0) {
            return "null";
        }
        long[] idx = instanceIndex.get(objId);
        if (idx != null) {
            String cls = classNameByObjId.getOrDefault(idx[0], "?");
            if (cls.equals("java/lang/String")) {
                String s = stringText(objId);
                return s == null ? "String@" + Long.toHexString(objId) : '"' + truncate(s) + '"';
            }
            return simpleName(cls) + "@" + Long.toHexString(objId);
        }
        long[] arr = primArrayIndex.get(objId);
        if (arr != null) {
            return typeName((int) arr[2]) + "[" + arr[1] + "]";
        }
        return "@" + Long.toHexString(objId);
    }

    @Override
    public void close() {
        try {
            raf.close();
        } catch (IOException ignored) {
        }
        file.delete();
    }

    // ---- field decoding ---------------------------------------------------------------------------

    private FieldValue readField(String name, int type, Cursor c) {
        switch (type) {
            case T_OBJECT: {
                long ref = c.id(idSize);
                if (ref == 0) {
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

    /** Decode a java.lang.String's text from its char[]/byte[] value array. Null on failure. */
    private String stringText(long objId) {
        try {
            long[] idx = instanceIndex.get(objId);
            if (idx == null) {
                return null;
            }
            byte[] blob = readBlob(idx[1], (int) idx[2]);
            Cursor c = new Cursor(blob);
            long valueRef = 0;
            int coder = -1;
            long cid = idx[0];
            while (cid != 0) {
                ClassDef def = classDefs.get(cid);
                if (def == null) {
                    break;
                }
                for (int i = 0; i < def.fieldTypes.length; i++) {
                    String fn = strings.get(def.fieldNameIds[i]);
                    int t = def.fieldTypes[i];
                    if (t == T_OBJECT) {
                        long ref = c.id(idSize);
                        if ("value".equals(fn)) {
                            valueRef = ref;
                        }
                    } else if (t == T_BYTE) {
                        int v = c.u1();
                        if ("coder".equals(fn)) {
                            coder = v;
                        }
                    } else {
                        c.skip(typeSize(t, idSize));
                    }
                }
                cid = def.superId;
            }
            if (valueRef == 0) {
                return null;
            }
            long[] arr = primArrayIndex.get(valueRef);
            if (arr == null) {
                return null;
            }
            int count = (int) arr[1];
            int elem = (int) arr[2];
            byte[] data = readBlob(arr[0], count * typeSize(elem, idSize));
            if (elem == T_CHAR) {
                char[] chars = new char[count];
                for (int i = 0; i < count; i++) {
                    chars[i] = (char) (((data[i * 2] & 0xFF) << 8) | (data[i * 2 + 1] & 0xFF));
                }
                return new String(chars);
            }
            // byte[] (JDK9+): coder 0 = LATIN1, 1 = UTF16
            if (coder == 1) {
                return new String(data, StandardCharsets.UTF_16LE);
            }
            return new String(data, StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            return null;
        }
    }

    private synchronized byte[] readBlob(long offset, int len) throws IOException {
        raf.seek(offset);
        byte[] b = new byte[len];
        raf.readFully(b);
        return b;
    }

    // ---- parsing ----------------------------------------------------------------------------------

    private int readHeader(Reader r) throws IOException {
        int b;
        while ((b = r.u1()) != 0) {
            // version string up to NUL
            if (b < 0) {
                throw new EOFException();
            }
        }
        int size = (int) r.u4();
        r.skip(8); // timestamp
        return size;
    }

    private void indexRecords(Reader r) throws IOException {
        while (true) {
            int tag;
            try {
                tag = r.u1();
            } catch (EOFException eof) {
                return;
            }
            r.u4(); // time
            long len = r.u4();
            switch (tag) {
                case 0x01: { // STRING_IN_UTF8
                    long id = r.id(idSize);
                    strings.put(id, r.utf8((int) (len - idSize)));
                    break;
                }
                case 0x02: { // LOAD_CLASS
                    r.u4();
                    long classObjId = r.id(idSize);
                    r.u4();
                    long nameId = r.id(idSize);
                    classNameStringId.put(classObjId, nameId);
                    break;
                }
                case 0x0C: // HEAP_DUMP
                case 0x1C: // HEAP_DUMP_SEGMENT
                    indexHeapSegment(r, len);
                    break;
                default:
                    r.skip(len);
                    break;
            }
        }
    }

    private void indexHeapSegment(Reader r, long len) throws IOException {
        long end = r.pos + len;
        while (r.pos < end) {
            int sub = r.u1();
            switch (sub) {
                case 0x21: { // INSTANCE_DUMP
                    long objId = r.id(idSize);
                    r.u4(); // stack serial
                    long classObjId = r.id(idSize);
                    int numBytes = (int) r.u4();
                    long offset = r.pos;
                    instanceIndex.put(objId, new long[]{classObjId, offset, numBytes});
                    instancesByClass.computeIfAbsent(classObjId, k -> new ArrayList<>()).add(objId);
                    r.skip(numBytes);
                    break;
                }
                case 0x23: { // PRIMITIVE_ARRAY_DUMP
                    long objId = r.id(idSize);
                    r.u4();
                    int count = (int) r.u4();
                    int elemType = r.u1();
                    long offset = r.pos;
                    primArrayIndex.put(objId, new long[]{offset, count, elemType});
                    r.skip((long) count * typeSize(elemType, idSize));
                    break;
                }
                case 0x22: { // OBJECT_ARRAY_DUMP
                    r.id(idSize);
                    r.u4();
                    int count = (int) r.u4();
                    r.id(idSize); // array class
                    r.skip((long) count * idSize);
                    break;
                }
                case 0x20: // CLASS_DUMP
                    readClassDump(r);
                    break;
                case 0xFF: // ROOT_UNKNOWN: objId
                    r.id(idSize);
                    break;
                case 0x01: // ROOT_JNI_GLOBAL: objId, jniGlobalRefId
                    r.id(idSize);
                    r.id(idSize);
                    break;
                case 0x02: // ROOT_JNI_LOCAL: objId, threadSerial, frameNum
                case 0x03: // ROOT_JAVA_FRAME: objId, threadSerial, frameNum
                case 0x08: // ROOT_THREAD_OBJECT: objId, threadSerial, stackTraceSerial
                    r.id(idSize);
                    r.u4();
                    r.u4();
                    break;
                case 0x04: // ROOT_NATIVE_STACK: objId, threadSerial
                case 0x06: // ROOT_THREAD_BLOCK: objId, threadSerial
                    r.id(idSize);
                    r.u4();
                    break;
                case 0x05: // ROOT_STICKY_CLASS: objId
                case 0x07: // ROOT_MONITOR_USED: objId
                    r.id(idSize);
                    break;
                default:
                    throw new IOException("unknown heap sub-record 0x" + Integer.toHexString(sub)
                            + " at " + r.pos);
            }
        }
    }

    private void readClassDump(Reader r) throws IOException {
        long classObjId = r.id(idSize);
        r.u4(); // stack serial
        long superId = r.id(idSize);
        r.id(idSize); // class loader
        r.id(idSize); // signers
        r.id(idSize); // protection domain
        r.id(idSize); // reserved
        r.id(idSize); // reserved
        r.u4(); // instance size
        int cpCount = r.u2();
        for (int i = 0; i < cpCount; i++) {
            r.u2(); // cp index
            int type = r.u1();
            r.skip(typeSize(type, idSize));
        }
        int staticCount = r.u2();
        for (int i = 0; i < staticCount; i++) {
            r.id(idSize); // name string id
            int type = r.u1();
            r.skip(typeSize(type, idSize));
        }
        int instCount = r.u2();
        long[] names = new long[instCount];
        int[] types = new int[instCount];
        for (int i = 0; i < instCount; i++) {
            names[i] = r.id(idSize);
            types[i] = r.u1();
        }
        ClassDef def = new ClassDef();
        def.superId = superId;
        def.fieldNameIds = names;
        def.fieldTypes = types;
        classDefs.put(classObjId, def);
    }

    private void resolveClassNames() {
        for (Map.Entry<Long, Long> e : classNameStringId.entrySet()) {
            String name = strings.get(e.getValue());
            if (name == null) {
                continue;
            }
            String slashed = name.replace('.', '/');
            classNameByObjId.put(e.getKey(), slashed);
            nameToClassObjId.put(slashed, e.getKey());
        }
    }

    private String labelClass(long objId) {
        long[] idx = instanceIndex.get(objId);
        return idx == null ? "?" : classNameByObjId.getOrDefault(idx[0], "?");
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private static int typeSize(int type, int idSize) {
        switch (type) {
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

    private static String typeName(int type) {
        switch (type) {
            case T_OBJECT: return "object";
            case T_BOOLEAN: return "boolean";
            case T_CHAR: return "char";
            case T_FLOAT: return "float";
            case T_DOUBLE: return "double";
            case T_BYTE: return "byte";
            case T_SHORT: return "short";
            case T_INT: return "int";
            case T_LONG: return "long";
            default: return "?";
        }
    }

    private static String simpleName(String internal) {
        int slash = internal.lastIndexOf('/');
        return slash >= 0 ? internal.substring(slash + 1) : internal;
    }

    private static String truncate(String s) {
        return s.length() <= 64 ? s : s.substring(0, 64) + "...";
    }

    /** Position-tracking big-endian reader over the streaming parse. */
    private static final class Reader {
        private final DataInputStream in;
        long pos;

        Reader(InputStream s) {
            this.in = new DataInputStream(s);
        }

        int u1() throws IOException {
            int b = in.read();
            if (b < 0) {
                throw new EOFException();
            }
            pos++;
            return b;
        }

        int u2() throws IOException {
            int v = in.readUnsignedShort();
            pos += 2;
            return v;
        }

        long u4() throws IOException {
            long v = in.readInt() & 0xFFFFFFFFL;
            pos += 4;
            return v;
        }

        long u8() throws IOException {
            long v = in.readLong();
            pos += 8;
            return v;
        }

        long id(int idSize) throws IOException {
            return idSize == 8 ? u8() : u4();
        }

        void skip(long n) throws IOException {
            long left = n;
            while (left > 0) {
                long s = in.skip(left);
                if (s <= 0) {
                    if (in.read() < 0) {
                        throw new EOFException();
                    }
                    s = 1;
                }
                left -= s;
            }
            pos += n;
        }

        String utf8(int n) throws IOException {
            byte[] b = new byte[n];
            in.readFully(b);
            pos += n;
            return new String(b, StandardCharsets.UTF_8);
        }
    }

    /** Big-endian cursor over a decoded field-value blob. */
    private static final class Cursor {
        private final byte[] b;
        private int p;

        Cursor(byte[] b) {
            this.b = b;
        }

        int u1() {
            return b[p++] & 0xFF;
        }

        int u2() {
            return ((b[p++] & 0xFF) << 8) | (b[p++] & 0xFF);
        }

        int i4() {
            return ((b[p++] & 0xFF) << 24) | ((b[p++] & 0xFF) << 16) | ((b[p++] & 0xFF) << 8) | (b[p++] & 0xFF);
        }

        long i8() {
            long v = 0;
            for (int i = 0; i < 8; i++) {
                v = (v << 8) | (b[p++] & 0xFF);
            }
            return v;
        }

        long id(int idSize) {
            return idSize == 8 ? i8() : (i4() & 0xFFFFFFFFL);
        }

        void skip(int n) {
            p += n;
        }
    }
}
