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
    public static final int MSG_ERROR = 0x7F;            // resp only: string message

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
}
