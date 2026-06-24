package com.tonic.live.agent;

import com.tonic.live.protocol.LiveProtocol;

import java.lang.instrument.Instrumentation;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The agent-resident value scanner: walks the reachable object graph from application static roots and retains live
 * {@code (object, field)} handles whose value matches, so later scans just re-read those exact handles (stable
 * "addresses" across scans - no heap dumps, no identity-matching). Handles are weak so the scan never pins the heap.
 *
 * <p>Two sets: {@code active} (the volatile candidate set narrowed by next-scans) and {@code pinned} (the watch /
 * freeze list, which survives narrowing). Frozen locations are re-written on a timer.
 */
final class ScanEngine {

    /** Hard ceilings so a runaway walk can never wedge the target. */
    private static final long DEFAULT_TIME_BUDGET_MS = 6000;

    private final AtomicLong ids = new AtomicLong(1);
    private final Map<Long, Location> active = new LinkedHashMap<>();
    private final Map<Long, Location> pinned = new LinkedHashMap<>();
    private final Map<Long, Object[]> frozen = new LinkedHashMap<>();   // id -> [Location, boxed value to re-apply]
    private ScheduledExecutorService freezeTimer;
    private boolean truncated;

    /** One retained value location: a weak handle to the owner plus how to read/write the slot. */
    private static final class Location {
        final long id;
        final WeakReference<Object> owner;
        final Field field;          // null for an array element
        final int index;            // array index when field == null
        final int valueType;        // LiveProtocol.SCAN_*
        final String declaringClass; // internal name of the field's declaring class (for the static launchpad)
        final String fieldName;
        final String fieldDesc;
        final String displayPath;
        Object last;

        Location(long id, Object owner, Field field, int index, int valueType,
                 String declaringClass, String fieldName, String fieldDesc, String displayPath, Object last) {
            this.id = id;
            this.owner = new WeakReference<>(owner);
            this.field = field;
            this.index = index;
            this.valueType = valueType;
            this.declaringClass = declaringClass;
            this.fieldName = fieldName;
            this.fieldDesc = fieldDesc;
            this.displayPath = displayPath;
            this.last = last;
        }

        /** Current value, or null if the owner was collected or the read failed. */
        Object read() {
            Object o = owner.get();
            if (o == null) {
                return null;
            }
            try {
                return field != null ? field.get(o) : Array.get(o, index);
            } catch (Throwable t) {
                return null;
            }
        }

        boolean collected() {
            return owner.get() == null;
        }
    }

    // ---- scans ------------------------------------------------------------------------------------

    /** First scan: walk app roots, retain matching locations as the new active set. */
    synchronized void firstScan(Instrumentation inst, int valueType, int scanKind, String value, String value2,
                                String pkgFilter, int maxVisited, int maxMatches) {
        clear();
        truncated = false;
        Class<?> wanted = primitiveFor(valueType);
        Object target = scanKind == LiveProtocol.SCANKIND_UNKNOWN ? null : parse(valueType, value);
        Object target2 = scanKind == LiveProtocol.SCANKIND_BETWEEN ? parse(valueType, value2) : null;
        long deadline = System.currentTimeMillis() + DEFAULT_TIME_BUDGET_MS;

        Deque<Object> queue = new ArrayDeque<>();
        Map<Object, Boolean> visited = new IdentityHashMap<>();
        int visitedCount = 0;

        for (Class<?> c : inst.getAllLoadedClasses()) {
            if (!includeClass(c, pkgFilter)) {
                continue;
            }
            for (Field f : safeFields(c)) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                Class<?> t = f.getType();
                if (t.isPrimitive()) {
                    if (matchesType(t, wanted)) {
                        Object v = safeGet(f, null);
                        if (v != null && predicate(scanKind, valueType, v, target, target2)) {
                            record(recType(valueType, t), null, f, -1, internal(c), f.getName(), descriptor(t),
                                    c.getSimpleName() + "." + f.getName(), v, maxMatches);
                        }
                    }
                } else {
                    Object v = safeGet(f, null);
                    if (v == null) {
                        continue;
                    }
                    if (refMatches(valueType, v)) {
                        if (predicate(scanKind, valueType, v, target, target2)) {
                            record(valueType, null, f, -1, internal(c), f.getName(), descriptor(t),
                                    c.getSimpleName() + "." + f.getName(), v, maxMatches);
                        }
                    } else {
                        enqueue(queue, visited, v);
                    }
                }
            }
        }

        // Beyond app static fields, two more pure-Java root sets reach state no app static holds:
        //  - every live thread (Runnable targets, thread-local maps), and
        //  - every AWT/Swing window: a GUI app's data models hang off its visible windows, which the JDK owns
        //    (the AppContext window list), not the app's static fields.
        for (Thread th : Thread.getAllStackTraces().keySet()) {
            enqueue(queue, visited, th);
        }
        Object[] windows = awtRoots();
        for (Object w : windows) {
            enqueue(queue, visited, w);
        }

        while (!queue.isEmpty()) {
            if (visitedCount >= maxVisited || System.currentTimeMillis() > deadline || active.size() >= maxMatches) {
                truncated = !queue.isEmpty();
                break;
            }
            Object o = queue.poll();
            visitedCount++;
            Class<?> oc = o.getClass();
            if (oc.isArray()) {
                walkArray(o, oc, valueType, wanted, scanKind, target, target2, queue, visited, maxMatches);
                continue;
            }
            for (Class<?> k = oc; k != null && k != Object.class; k = k.getSuperclass()) {
                for (Field f : safeFields(k)) {
                    if (Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    Class<?> t = f.getType();
                    if (t.isPrimitive()) {
                        if (matchesType(t, wanted)) {
                            Object v = safeGet(f, o);
                            if (v != null && predicate(scanKind, valueType, v, target, target2)) {
                                record(recType(valueType, t), o, f, -1, internal(k), f.getName(), descriptor(t),
                                        pathLabel(oc) + "." + f.getName(), v, maxMatches);
                            }
                        }
                    } else {
                        Object v = safeGet(f, o);
                        if (v == null) {
                            continue;
                        }
                        if (refMatches(valueType, v)) {
                            if (predicate(scanKind, valueType, v, target, target2)) {
                                record(valueType, o, f, -1, internal(k), f.getName(), descriptor(t),
                                        pathLabel(oc) + "." + f.getName(), v, maxMatches);
                            }
                        } else {
                            enqueue(queue, visited, v);
                        }
                    }
                }
            }
        }
        System.err.println("[SCAN-DIAG] valueType=" + valueType + " kind=" + scanKind + " target=" + target
                + " windows=" + windows.length + " visited=" + visitedCount
                + " matches=" + active.size() + " truncated=" + truncated);
    }

    private void walkArray(Object arr, Class<?> arrClass, int valueType, Class<?> wanted, int scanKind,
                           Object target, Object target2, Deque<Object> queue, Map<Object, Boolean> visited,
                           int maxMatches) {
        Class<?> comp = arrClass.getComponentType();
        int len = Array.getLength(arr);
        if (comp.isPrimitive()) {
            if (matchesType(comp, wanted)) {
                for (int i = 0; i < len; i++) {
                    Object v = Array.get(arr, i);
                    if (v != null && predicate(scanKind, valueType, v, target, target2)) {
                        record(recType(valueType, comp), arr, null, i, internal(arrClass),
                                "", descriptor(comp), pathLabel(arrClass) + "[" + i + "]", v, maxMatches);
                    }
                }
            }
        } else {
            for (int i = 0; i < len; i++) {
                Object v = Array.get(arr, i);
                if (v == null) {
                    continue;
                }
                if (refMatches(valueType, v)) {
                    if (predicate(scanKind, valueType, v, target, target2)) {
                        record(valueType, arr, null, i, internal(String.class),
                                "", descriptor(String.class), pathLabel(arrClass) + "[" + i + "]", v, maxMatches);
                    }
                } else {
                    enqueue(queue, visited, v);
                }
            }
        }
    }

    /** Next scan: re-read the active set and keep only locations matching the comparator vs their last value. */
    synchronized void nextScan(int comparator, String value, String value2) {
        Object target = value == null || value.isEmpty() ? null : parseFor(value);
        Object target2 = parseMaybe(value2);
        List<Long> drop = new ArrayList<>();
        for (Location loc : active.values()) {
            Object cur = loc.read();
            if (cur == null || !compare(comparator, loc.valueType, cur, loc.last, target, target2)) {
                drop.add(loc.id);
            } else {
                loc.last = cur;
            }
        }
        for (Long id : drop) {
            active.remove(id);
        }
        truncated = false;
    }

    // ---- mutate / pin / freeze --------------------------------------------------------------------

    synchronized String write(long id, boolean isNull, String value) {
        Location loc = find(id);
        if (loc == null) {
            throw new IllegalStateException("no such location");
        }
        Object o = loc.owner.get();
        if (o == null) {
            throw new IllegalStateException("the object was garbage-collected");
        }
        if (loc.field != null) {
            if (Modifier.isFinal(loc.field.getModifiers())) {
                throw new IllegalStateException("field is final");
            }
            Object v = isNull ? null : parse(loc.valueType, value);
            try {
                loc.field.set(o, v);
                loc.last = loc.field.get(o);
            } catch (Exception e) {
                throw new IllegalStateException("set failed: " + e.getMessage());
            }
        } else {
            Object v = parse(loc.valueType, value);
            Array.set(o, loc.index, v);
            loc.last = Array.get(o, loc.index);
        }
        return format(loc.last);
    }

    synchronized void freeze(long id, boolean on, String value) {
        Location loc = find(id);
        if (loc == null) {
            return;
        }
        if (on) {
            pinned.putIfAbsent(loc.id, loc);
            frozen.put(loc.id, new Object[]{loc, parse(loc.valueType, value)});
            ensureFreezeTimer();
        } else {
            frozen.remove(loc.id);
        }
    }

    synchronized void pin(long id, boolean on) {
        if (on) {
            Location loc = active.get(id);
            if (loc != null) {
                pinned.putIfAbsent(id, loc);
            }
        } else {
            pinned.remove(id);
            frozen.remove(id);
        }
    }

    synchronized void clear() {
        active.clear();
        pinned.clear();
        frozen.clear();
        truncated = false;
        if (freezeTimer != null) {
            freezeTimer.shutdownNow();
            freezeTimer = null;
        }
    }

    private void ensureFreezeTimer() {
        if (freezeTimer == null) {
            freezeTimer = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "jstudio-scan-freeze");
                t.setDaemon(true);
                return t;
            });
            freezeTimer.scheduleAtFixedRate(this::applyFreezes, 80, 80, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void applyFreezes() {
        for (Object[] entry : frozen.values()) {
            Location loc = (Location) entry[0];
            Object o = loc.owner.get();
            if (o == null) {
                continue;
            }
            try {
                if (loc.field != null) {
                    loc.field.set(o, entry[1]);
                } else {
                    Array.set(o, loc.index, entry[1]);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    // ---- serialization (a "page" of locations) ----------------------------------------------------

    synchronized byte[] page(int messageType, boolean pinnedOnly, int offset, int limit) throws java.io.IOException {
        Map<Long, Location> src = pinnedOnly ? pinned : active;
        List<Location> all = new ArrayList<>(src.values());
        int total = all.size();
        int from = Math.max(0, offset);
        int to = Math.min(total, from + Math.max(0, limit));
        Frame f = new Frame(messageType);
        f.u32(total);
        f.u8(truncated ? 1 : 0);
        f.u32(Math.max(0, to - from));
        for (int i = from; i < to; i++) {
            Location loc = all.get(i);
            Object cur = loc.read();
            int flags = 0;
            if (pinned.containsKey(loc.id)) flags |= LiveProtocol.FLAG_PINNED;
            if (frozen.containsKey(loc.id)) flags |= LiveProtocol.FLAG_FROZEN;
            if (loc.collected()) flags |= LiveProtocol.FLAG_COLLECTED;
            f.u64(loc.id);
            f.str(loc.declaringClass);
            f.str(loc.fieldName);
            f.str(loc.fieldDesc);
            f.str(loc.displayPath);
            f.str(typeName(loc.valueType));
            f.str(cur == null ? (loc.collected() ? "<collected>" : "<unreadable>") : format(cur));
            f.u8(flags);
        }
        return f.toBytes();
    }

    // ---- predicates / comparisons -----------------------------------------------------------------

    private boolean predicate(int scanKind, int valueType, Object v, Object target, Object target2) {
        if (scanKind == LiveProtocol.SCANKIND_UNKNOWN) {
            return true;
        }
        if (valueType == LiveProtocol.SCAN_STRING) {
            return scanKind == LiveProtocol.SCANKIND_EXACT && v.equals(target);
        }
        if (valueType == LiveProtocol.SCAN_BOOLEAN) {
            return v.equals(target);
        }
        double a = num(v);
        switch (scanKind) {
            case LiveProtocol.SCANKIND_EXACT: return a == num(target);
            case LiveProtocol.SCANKIND_GREATER: return a > num(target);
            case LiveProtocol.SCANKIND_LESS: return a < num(target);
            case LiveProtocol.SCANKIND_BETWEEN: return a >= num(target) && a <= num(target2);
            default: return false;
        }
    }

    private boolean compare(int cmp, int valueType, Object cur, Object last, Object target, Object target2) {
        if (valueType == LiveProtocol.SCAN_STRING || valueType == LiveProtocol.SCAN_BOOLEAN) {
            switch (cmp) {
                case LiveProtocol.CMP_EXACT: return cur.equals(target);
                case LiveProtocol.CMP_CHANGED: return !cur.equals(last);
                case LiveProtocol.CMP_UNCHANGED: return cur.equals(last);
                default: return false;
            }
        }
        double a = num(cur);
        double b = last == null ? a : num(last);
        switch (cmp) {
            case LiveProtocol.CMP_EXACT: return target != null && a == num(target);
            case LiveProtocol.CMP_CHANGED: return a != b;
            case LiveProtocol.CMP_UNCHANGED: return a == b;
            case LiveProtocol.CMP_INCREASED: return a > b;
            case LiveProtocol.CMP_DECREASED: return a < b;
            case LiveProtocol.CMP_INCREASED_BY: return target != null && a == b + num(target);
            case LiveProtocol.CMP_DECREASED_BY: return target != null && a == b - num(target);
            case LiveProtocol.CMP_GREATER: return target != null && a > num(target);
            case LiveProtocol.CMP_LESS: return target != null && a < num(target);
            case LiveProtocol.CMP_BETWEEN: return target != null && target2 != null && a >= num(target) && a <= num(target2);
            default: return false;
        }
    }

    private static double num(Object o) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof Character) return (char) (Character) o;
        if (o instanceof Boolean) return (Boolean) o ? 1 : 0;
        return Double.NaN;
    }

    // ---- helpers ----------------------------------------------------------------------------------

    private void record(int valueType, Object owner, Field field, int index, String declaringClass,
                        String fieldName, String fieldDesc, String displayPath, Object value, int maxMatches) {
        if (active.size() >= maxMatches) {
            truncated = true;
            return;
        }
        long id = ids.getAndIncrement();
        active.put(id, new Location(id, owner == null ? field.getDeclaringClass() : owner,
                field, index, valueType, declaringClass, fieldName, fieldDesc, displayPath, value));
    }

    private Location find(long id) {
        Location loc = active.get(id);
        return loc != null ? loc : pinned.get(id);
    }

    private static void enqueue(Deque<Object> queue, Map<Object, Boolean> visited, Object o) {
        if (o == null || visited.containsKey(o)) {
            return;
        }
        Class<?> c = o.getClass();
        if (c.getName().startsWith("java.lang.") && !(c.isArray())) {
            // Boxed primitives / String have no useful child refs and are interned/shared; don't traverse them.
            if (c == String.class || Number.class.isAssignableFrom(c) || c == Boolean.class || c == Character.class) {
                visited.put(o, Boolean.TRUE);
                return;
            }
        }
        visited.put(o, Boolean.TRUE);
        queue.add(o);
    }

    /** All AWT/Swing windows (frames + dialogs) as live roots, via reflection so a headless/AWT-less target is fine. */
    private static Object[] awtRoots() {
        try {
            Class<?> window = Class.forName("java.awt.Window");
            Object windows = window.getMethod("getWindows").invoke(null);
            return windows instanceof Object[] ? (Object[]) windows : new Object[0];
        } catch (Throwable t) {
            return new Object[0];
        }
    }

    private static boolean includeClass(Class<?> c, String pkgFilter) {
        if (c.isArray() || c.isPrimitive() || c.isSynthetic()) {
            return false;
        }
        String n = c.getName();
        if (n.startsWith("java.") || n.startsWith("jdk.") || n.startsWith("sun.")
                || n.startsWith("com.sun.") || n.startsWith("javax.") || n.startsWith("com.tonic.live.")) {
            return false;
        }
        return pkgFilter == null || pkgFilter.isEmpty() || n.replace('.', '/').startsWith(pkgFilter);
    }

    private static Field[] safeFields(Class<?> c) {
        try {
            return c.getDeclaredFields();
        } catch (Throwable t) {
            return new Field[0];
        }
    }

    private static Object safeGet(Field f, Object owner) {
        try {
            f.setAccessible(true);
            return f.get(owner);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean matchesType(Class<?> declared, Class<?> wanted) {
        if (wanted == null) {
            return isNumeric(declared);
        }
        if (wanted == String.class) {
            return declared == String.class;
        }
        return declared == wanted;
    }

    private static boolean isNumeric(Class<?> c) {
        return c == byte.class || c == short.class || c == int.class || c == long.class
                || c == float.class || c == double.class;
    }

    /**
     * Reference-typed scans (String) match on the value's RUNTIME type, not the declared field/array type, so a
     * String held in an {@code Object}/{@code CharSequence} field or an {@code Object[]} (List/Map backing) is found.
     */
    private static boolean refMatches(int valueType, Object v) {
        return valueType == LiveProtocol.SCAN_STRING && v instanceof String;
    }

    /** The concrete SCAN_* code a number-mode match should be recorded under (so writes/format use the real type). */
    private static int recType(int valueType, Class<?> declared) {
        if (valueType != LiveProtocol.SCAN_NUMBER) {
            return valueType;
        }
        if (declared == long.class) return LiveProtocol.SCAN_LONG;
        if (declared == short.class) return LiveProtocol.SCAN_SHORT;
        if (declared == byte.class) return LiveProtocol.SCAN_BYTE;
        if (declared == float.class) return LiveProtocol.SCAN_FLOAT;
        if (declared == double.class) return LiveProtocol.SCAN_DOUBLE;
        return LiveProtocol.SCAN_INT;
    }

    private static Class<?> primitiveFor(int valueType) {
        switch (valueType) {
            case LiveProtocol.SCAN_INT: return int.class;
            case LiveProtocol.SCAN_LONG: return long.class;
            case LiveProtocol.SCAN_SHORT: return short.class;
            case LiveProtocol.SCAN_BYTE: return byte.class;
            case LiveProtocol.SCAN_CHAR: return char.class;
            case LiveProtocol.SCAN_FLOAT: return float.class;
            case LiveProtocol.SCAN_DOUBLE: return double.class;
            case LiveProtocol.SCAN_BOOLEAN: return boolean.class;
            case LiveProtocol.SCAN_NUMBER: return null;
            default: return String.class;
        }
    }

    private static Object parse(int valueType, String s) {
        switch (valueType) {
            case LiveProtocol.SCAN_INT: return Integer.parseInt(s.trim());
            case LiveProtocol.SCAN_LONG: return Long.parseLong(s.trim());
            case LiveProtocol.SCAN_SHORT: return Short.parseShort(s.trim());
            case LiveProtocol.SCAN_BYTE: return Byte.parseByte(s.trim());
            case LiveProtocol.SCAN_CHAR: return s.isEmpty() ? '\0' : s.charAt(0);
            case LiveProtocol.SCAN_FLOAT: return Float.parseFloat(s.trim());
            case LiveProtocol.SCAN_DOUBLE:
            case LiveProtocol.SCAN_NUMBER: return Double.parseDouble(s.trim());
            case LiveProtocol.SCAN_BOOLEAN: return Boolean.parseBoolean(s.trim());
            default: return s;
        }
    }

    private Object parseFor(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private Object parseMaybe(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static String format(Object v) {
        return v == null ? "null" : String.valueOf(v);
    }

    private static String internal(Class<?> c) {
        return c.getName().replace('.', '/');
    }

    private static String pathLabel(Class<?> c) {
        return c.getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(c));
    }

    private static String descriptor(Class<?> c) {
        if (c == boolean.class) return "Z";
        if (c == byte.class) return "B";
        if (c == char.class) return "C";
        if (c == short.class) return "S";
        if (c == int.class) return "I";
        if (c == long.class) return "J";
        if (c == float.class) return "F";
        if (c == double.class) return "D";
        if (c.isArray()) return "[" + descriptor(c.getComponentType());
        return "L" + c.getName().replace('.', '/') + ";";
    }

    private static String typeName(int valueType) {
        switch (valueType) {
            case LiveProtocol.SCAN_INT: return "int";
            case LiveProtocol.SCAN_LONG: return "long";
            case LiveProtocol.SCAN_SHORT: return "short";
            case LiveProtocol.SCAN_BYTE: return "byte";
            case LiveProtocol.SCAN_CHAR: return "char";
            case LiveProtocol.SCAN_FLOAT: return "float";
            case LiveProtocol.SCAN_DOUBLE: return "double";
            case LiveProtocol.SCAN_BOOLEAN: return "boolean";
            default: return "String";
        }
    }

    /** Big-endian response builder, first byte = message type (mirrors {@code JavaAgent.Buf}). */
    private static final class Frame {
        private final java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        private final java.io.DataOutputStream d = new java.io.DataOutputStream(bo);

        Frame(int type) throws java.io.IOException {
            d.writeByte(type);
        }

        void u8(int v) throws java.io.IOException {
            d.writeByte(v);
        }

        void u32(int v) throws java.io.IOException {
            d.writeInt(v);
        }

        void u64(long v) throws java.io.IOException {
            d.writeLong(v);
        }

        void str(String s) throws java.io.IOException {
            byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            d.writeShort(b.length);
            d.write(b);
        }

        byte[] toBytes() throws java.io.IOException {
            d.flush();
            return bo.toByteArray();
        }
    }
}
