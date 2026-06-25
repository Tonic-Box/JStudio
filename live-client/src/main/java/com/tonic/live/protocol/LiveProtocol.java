package com.tonic.live.protocol;

/**
 * Wire-protocol constants for the JStudio Live agent (a pure-Java {@code java.lang.instrument} agent).
 *
 * <p>Framing: each message is {@code [uint32 big-endian length][payload]}, {@code payload[0]} = type, the
 * rest is the body. Integers big-endian; strings {@code [uint16 len][UTF-8]}; class names internal form
 * ({@code com/foo/Bar}). Types {@code >= 0x40} are unsolicited events; {@code MSG_ERROR} (0x7F) is a
 * response to the in-flight request (the client demuxes events as {@code [0x40, 0x7F)}).
 */
public final class LiveProtocol {

    private LiveProtocol() {
    }

    public static final int MSG_HELLO = 0x01;            // resp: u32 version, u32 capBits, u32 classCount
    public static final int MSG_LIST_CLASSES = 0x02;     // resp: u32 count, [string name, u16 accessFlags]*
    public static final int MSG_GET_CLASS_BYTES = 0x03;  // req: string name; resp: u32 len, bytes
    public static final int MSG_GET_THREADS = 0x04;      // resp: u32 count, [u64 id, string name, u32 state]*
    public static final int MSG_REDEFINE_CLASS = 0x0B;   // req: string name, u32 len, bytes; resp: u8 ok
    public static final int MSG_SET_CAPTURE_LOADS = 0x0E;// req: u8 on; resp: u8 ok (push EVT_CLASS_LOADED)
    public static final int MSG_GET_CONTENTION = 0x16;   // resp: u32 count, [u64 tid, str tname, str monCls, u64 ownerId, str ownerName]*
    public static final int MSG_HEAP_DUMP = 0x17;        // req: empty; resp: str hprofFilePath (HotSpot heap dump)
    public static final int MSG_GET_STATICS = 0x18;      // req: str class; resp: u32 count, [str name, str typeDesc, str value, u8 kind]*
    public static final int MSG_SET_STATIC = 0x19;       // req: str class, str field, u8 isNull, str value; resp: str newValue
    public static final int MSG_LIST_STATIC_METHODS = 0x1A; // req: str class; resp: u32 count, [str name, str desc]*
    public static final int MSG_INVOKE_STATIC = 0x1B;    // req: str class, str name, str desc, u32 argc, [str arg]*; resp: str result
    public static final int MSG_GET_THREAD_STACKS = 0x1C;// req: u32 maxDepth; resp: u32 count, [u64 tid, str name, u32 state, u32 frames, [str cls, str method, str file, i32 line]*]*
    public static final int MSG_GET_METRICS = 0x1D;      // req: empty; resp: VM metrics snapshot (see MetricsSnapshot)
    public static final int MSG_EVAL = 0x1E;             // req: u32 classCount, [str name, u32 len, bytes]*, str mainName, str contextClass; resp: str output
    public static final int MSG_JFR_START = 0x1F;        // req: str profile, u32 categoryMask, u32 maxSizeMb; resp: u8 ok
    public static final int MSG_JFR_STOP = 0x20;         // req: empty; resp: str localJfrPath (stops + clears the active recording)
    public static final int MSG_JFR_SNAPSHOT = 0x21;     // req: empty; resp: str localJfrPath (recording keeps running)

    // Live value scanner (Cheat-Engine-style): an agent-resident scan session holding live (object,field) handles.
    // A "location" wire record is: u64 id, str declaringClass, str fieldName, str fieldDesc, str displayPath,
    // str type, str value, u8 flags (FLAG_PINNED|FLAG_FROZEN|FLAG_COLLECTED). A "page" is: u32 total, u8 truncated,
    // u32 returned, [location]*. Scalar values travel as strings parsed agent-side (mirroring MSG_SET_STATIC).
    public static final int MSG_SCAN_FIRST = 0x30;  // req: u8 valueType,u8 scanKind,str value,str value2,str pkgFilter,u32 maxVisited,u32 maxMatches,u32 limit,u8 userClassesOnly; resp: page
    public static final int MSG_SCAN_NEXT = 0x31;   // req: u8 comparator,str value,str value2,u32 offset,u32 limit; resp: page
    public static final int MSG_SCAN_READ = 0x32;   // req: u8 pinnedOnly,u32 offset,u32 limit; resp: page
    public static final int MSG_SCAN_WRITE = 0x33;  // req: u64 id,u8 isNull,str value; resp: str newValue
    public static final int MSG_SCAN_FREEZE = 0x34; // req: u64 id,u8 on,str value; resp: u8 ok
    public static final int MSG_SCAN_PIN = 0x35;    // req: u64 id,u8 on; resp: u8 ok
    public static final int MSG_SCAN_CLEAR = 0x36;  // req: empty; resp: u8 ok

    // Live instances (the instances view): walk the heap for live instances of a class, then read/write their
    // fields by handle. Handles are weak refs the agent retains, so a write hits the real live object.
    public static final int MSG_LIST_INSTANCES = 0x37;     // req: str class,u32 maxInstances,u32 maxVisited; resp: u32 count,[u64 id,str label]*
    public static final int MSG_INSTANCE_FIELDS = 0x38;    // req: u64 handleId; resp: u32 count,[str name,str typeDesc,str display,u64 refId,u8 editable]*
    public static final int MSG_SET_INSTANCE_FIELD = 0x39; // req: u64 handleId,str field,u8 isNull,str value; resp: str newValue

    public static final int MSG_ERROR = 0x7F;            // resp only: string message

    // Scanner value types (u8) - which kind of field to scan + how to parse the value strings.
    public static final int SCAN_INT = 0;
    public static final int SCAN_LONG = 1;
    public static final int SCAN_SHORT = 2;
    public static final int SCAN_BYTE = 3;
    public static final int SCAN_CHAR = 4;
    public static final int SCAN_FLOAT = 5;
    public static final int SCAN_DOUBLE = 6;
    public static final int SCAN_BOOLEAN = 7;
    public static final int SCAN_STRING = 8;
    public static final int SCAN_NUMBER = 9;   // any numeric field (byte/short/int/long/float/double); matches store their real type

    // First-scan predicate (u8). UNKNOWN records every field of the type (+ current value) for later narrowing.
    public static final int SCANKIND_EXACT = 0;
    public static final int SCANKIND_GREATER = 1;
    public static final int SCANKIND_LESS = 2;
    public static final int SCANKIND_BETWEEN = 3;
    public static final int SCANKIND_UNKNOWN = 4;

    // Next-scan comparator (u8) - compares each retained location's current value to its previous value.
    public static final int CMP_EXACT = 0;
    public static final int CMP_CHANGED = 1;
    public static final int CMP_UNCHANGED = 2;
    public static final int CMP_INCREASED = 3;
    public static final int CMP_DECREASED = 4;
    public static final int CMP_INCREASED_BY = 5;
    public static final int CMP_DECREASED_BY = 6;
    public static final int CMP_GREATER = 7;
    public static final int CMP_LESS = 8;
    public static final int CMP_BETWEEN = 9;

    // Scanner location flags (u8 bitset).
    public static final int FLAG_PINNED = 1;
    public static final int FLAG_FROZEN = 1 << 1;
    public static final int FLAG_COLLECTED = 1 << 2;

    // MSG_GET_STATICS field kinds: how the UI may edit the value.
    public static final int STATIC_READONLY = 0;         // final - not editable
    public static final int STATIC_PRIMITIVE = 1;        // editable via parsed text
    public static final int STATIC_STRING = 2;           // editable via text, or null
    public static final int STATIC_REFERENCE = 3;        // object - only settable to null

    public static final int EVT_CLASS_LOADED = 0x43;     // string name, u32 len, bytes (runtime class capture)

    // Capability bits reported in MSG_HELLO (the Java agent supports redefine/retransform/get-bytecode).
    public static final int CAP_REDEFINE = 1;
    public static final int CAP_RETRANSFORM = 1 << 1;
    public static final int CAP_BYTECODES = 1 << 2;
    public static final int CAP_JFR = 1 << 3;            // agent can drive Flight Recorder (MSG_JFR_*)

    // MSG_JFR_START event-category bits: which JFR event families to record (on top of the base profile).
    public static final int JFR_CAT_CPU = 1;             // execution sampling
    public static final int JFR_CAT_ALLOC = 1 << 1;      // object allocation
    public static final int JFR_CAT_LOCKS = 1 << 2;      // monitor/park contention
    public static final int JFR_CAT_EXCEPTIONS = 1 << 3; // thrown exceptions/errors
}
