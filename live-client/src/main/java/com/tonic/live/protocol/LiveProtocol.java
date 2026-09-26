package com.tonic.live.protocol;

/** Wire-protocol constants shared by the JStudio Live agent and its client; framing is described in docs/live.md. */
public final class LiveProtocol
{

    private LiveProtocol()
    {
    }

    /** Response: u32 version, u32 capability bits, u32 class count. */
    public static final int MSG_HELLO = 0x01;

    /** Response: u32 count, then per class a string name and u16 access flags. */
    public static final int MSG_LIST_CLASSES = 0x02;

    /** Request: string name. Response: u32 length, then the class bytes. */
    public static final int MSG_GET_CLASS_BYTES = 0x03;

    /** Response: u32 count, then per thread a u64 id, string name and u32 state. */
    public static final int MSG_GET_THREADS = 0x04;

    /** Request: string name, u32 length, class bytes. Response: u8 ok. */
    public static final int MSG_REDEFINE_CLASS = 0x0B;

    /** Request: u8 on. Response: u8 ok; while on, EVT_CLASS_LOADED is pushed for each load. */
    public static final int MSG_SET_CAPTURE_LOADS = 0x0E;

    /** Response: u32 count, then per blocked thread a u64 id, string thread name, string monitor class, u64 owner id and string owner name. */
    public static final int MSG_GET_CONTENTION = 0x16;

    /** Request: empty. Response: string path of a HotSpot heap dump. */
    public static final int MSG_HEAP_DUMP = 0x17;

    /** Request: string class. Response: u32 count, then per field a string name, string type descriptor, string value and u8 kind (STATIC_*). */
    public static final int MSG_GET_STATICS = 0x18;

    /** Request: string class, string field, u8 is-null, string value. Response: string new value. */
    public static final int MSG_SET_STATIC = 0x19;

    /** Request: string class. Response: u32 count, then per method a string name and string descriptor. */
    public static final int MSG_LIST_STATIC_METHODS = 0x1A;

    /** Request: string class, string name, string descriptor, u32 argument count, then each argument as a string. Response: string result. */
    public static final int MSG_INVOKE_STATIC = 0x1B;

    /** Request: u32 max depth. Response: u32 count, then per thread a u64 id, string name, u32 state, u32 frame count and per frame a string class, string method, string file and i32 line. */
    public static final int MSG_GET_THREAD_STACKS = 0x1C;

    /** Request: empty. Response: a VM metrics snapshot, as read by MetricsSnapshot. */
    public static final int MSG_GET_METRICS = 0x1D;

    /** Request: u32 class count, then per class a string name, u32 length and bytes, then string main class and string context class. Response: string output. */
    public static final int MSG_EVAL = 0x1E;

    /** Request: string profile, u32 category mask (JFR_CAT_*), u32 max size in MB. Response: u8 ok. */
    public static final int MSG_JFR_START = 0x1F;

    /** Request: empty. Response: string path of the recording, which is stopped and cleared. */
    public static final int MSG_JFR_STOP = 0x20;

    /** Request: empty. Response: string path of a dump of the recording, which keeps running. */
    public static final int MSG_JFR_SNAPSHOT = 0x21;

    /** Request: u8 value type, u8 scan kind, string value, string value2, string package filter, u32 max visited, u32 max matches, u32 limit, u8 user classes only, u8 use dropbox, u8 roots only. Response: a page. */
    public static final int MSG_SCAN_FIRST = 0x30;

    /** Request: u8 comparator, string value, string value2, u32 offset, u32 limit. Response: a page. */
    public static final int MSG_SCAN_NEXT = 0x31;

    /** Request: u8 pinned only, u32 offset, u32 limit. Response: a page. */
    public static final int MSG_SCAN_READ = 0x32;

    /** Request: u64 id, u8 is-null, string value. Response: string new value. */
    public static final int MSG_SCAN_WRITE = 0x33;

    /** Request: u64 id, u8 on, string value. Response: u8 ok. */
    public static final int MSG_SCAN_FREEZE = 0x34;

    /** Request: u64 id, u8 on. Response: u8 ok. */
    public static final int MSG_SCAN_PIN = 0x35;

    /** Request: empty. Response: u8 ok. */
    public static final int MSG_SCAN_CLEAR = 0x36;

    /** Request: string class, u32 max instances, u32 max visited, u8 from dropbox. Response: u32 count, then per instance a u64 handle id and string label. */
    public static final int MSG_LIST_INSTANCES = 0x37;

    /** Request: u64 handle id. Response: u32 count, then per field a string name, string type descriptor, string display, u64 reference id and u8 editable. */
    public static final int MSG_INSTANCE_FIELDS = 0x38;

    /** Request: u64 handle id, string field, u8 is-null, string value. Response: string new value. */
    public static final int MSG_SET_INSTANCE_FIELD = 0x39;

    /** Response only: string message, answering the request in flight. */
    public static final int MSG_ERROR = 0x7F;

    public static final int SCAN_INT = 0;
    public static final int SCAN_LONG = 1;
    public static final int SCAN_SHORT = 2;
    public static final int SCAN_BYTE = 3;
    public static final int SCAN_CHAR = 4;
    public static final int SCAN_FLOAT = 5;
    public static final int SCAN_DOUBLE = 6;
    public static final int SCAN_BOOLEAN = 7;
    public static final int SCAN_STRING = 8;

    /** Any numeric field; a match is recorded under its real type. */
    public static final int SCAN_NUMBER = 9;

    public static final int SCANKIND_EXACT = 0;
    public static final int SCANKIND_GREATER = 1;
    public static final int SCANKIND_LESS = 2;
    public static final int SCANKIND_BETWEEN = 3;

    /** Records every field of the type with its current value, for later narrowing. */
    public static final int SCANKIND_UNKNOWN = 4;

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

    public static final int FLAG_PINNED = 1;
    public static final int FLAG_FROZEN = 1 << 1;
    public static final int FLAG_COLLECTED = 1 << 2;

    /** A final field, not editable. */
    public static final int STATIC_READONLY = 0;

    /** A primitive field, editable as parsed text. */
    public static final int STATIC_PRIMITIVE = 1;

    /** A String field, editable as text or null. */
    public static final int STATIC_STRING = 2;

    /** An object field, which can only be set to null. */
    public static final int STATIC_REFERENCE = 3;

    /** Event: string name, u32 length, class bytes, for a class captured as it loads. */
    public static final int EVT_CLASS_LOADED = 0x43;

    public static final int CAP_REDEFINE = 1;
    public static final int CAP_RETRANSFORM = 1 << 1;
    public static final int CAP_BYTECODES = 1 << 2;

    /** The agent can drive Flight Recorder through the MSG_JFR_* messages. */
    public static final int CAP_JFR = 1 << 3;

    /** Execution sampling. */
    public static final int JFR_CAT_CPU = 1;

    /** Object allocation. */
    public static final int JFR_CAT_ALLOC = 1 << 1;

    /** Monitor and park contention. */
    public static final int JFR_CAT_LOCKS = 1 << 2;

    /** Thrown exceptions and errors. */
    public static final int JFR_CAT_EXCEPTIONS = 1 << 3;
}
