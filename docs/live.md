# JStudio Live internals

Design notes for `live-agent`, `live-client` and `live-debug`. The user-facing guide is `live-debugging.md`; this
file records the reasons behind the implementation, which the code no longer carries as comments.

## Wire protocol

- Each message is a big-endian u32 length followed by the payload. The payload's first byte is the message type
  and the rest is the body. Integers are big-endian, strings are a u16 length and UTF-8 bytes, and class names
  use the internal form (`com/foo/Bar`). Each message's layout is documented on its constant in `LiveProtocol`.
- Types from 0x40 up are unsolicited events. `MSG_ERROR` (0x7F) is the exception: it answers the request in
  flight, so the client treats `[0x40, 0x7F)` as events.
- Value-scanner results travel as pages. A location is a u64 id, then strings for declaring class, field name,
  field descriptor, display path, type and value, then a u8 of `FLAG_*` bits. A page is a u32 total, u8
  truncated, u32 returned, then the locations. Scalar values travel as strings and are parsed agent-side, as
  `MSG_SET_STATIC` does.
- Instance handles are weak references the agent retains, so a field write hits the real live object.

## Client (LiveAgentClient)

- Requests are serialized, one in flight. Responses go through an unbounded queue so the reader thread never
  blocks handing one off.
- Events are dispatched on their own single thread, never the reader thread, so a slow listener cannot wedge
  the protocol stream. Dispatch becomes a no-op once it has been shut down.
- On disconnect or close a poison entry is pushed into the response queue, so a request in flight fails instead
  of hanging. A five-minute request timeout is a last-resort backstop for an agent that is wedged but still
  connected; it tears the connection down. A stale response left by an earlier timed-out request is drained
  before each request is sent.

## Agent (JavaAgent, ScanEngine, JfrController)

- At start the agent opens every boot-layer module's packages to itself. Without that, `setAccessible(true)`
  throws on Java 17+ for closed modules, the object-graph walk dies at the first JDK object, and application
  values held inside JDK containers (a string reachable only through a Swing window's component tree) are never
  visited.
- The agent's own classes are never reported to JStudio.
- Scratch pad snippets are defined in a throwaway loader whose parent is the chosen context class's loader, so
  they link against exactly what that class sees; each run gets its own namespace, so re-running the same name
  never collides and no classes accumulate. The loader, being agent-loaded, can call the protected `defineClass`
  directly, so no JDK-internal reflection or `--add-opens` is needed. All of a snippet's classes (the wrapper
  and any anonymous or local classes) go into one loader so their cross-references resolve; the loader is closed
  once `run()` returns, which releases its resources without invalidating the classes. Output is captured by
  teeing stdout and stderr during the call.
- A throwable's description falls back to its class when its message is null, which JVM redefine errors often
  are. CPU load comes from the HotSpot extension bean and is -1 where it is unavailable.
- The value scanner walks the reachable object graph from application static roots, every live thread and every
  AWT/Swing window (found by reflection, so a headless target is fine), and retains weak `(object, field)`
  handles whose value matches. Later scans re-read those exact handles, which gives stable addresses across
  scans without heap dumps or identity matching, and the weak handles never pin the heap.
- The scanner keeps two sets: the active candidates narrowed by each next scan, and the pinned watch and freeze
  list, which survives narrowing. Frozen locations are rewritten on a timer. Hard time and size ceilings keep a
  runaway walk from wedging the target.
- Boxed primitives and strings are not traversed: they have no useful references and are interned or shared.
- Reference scans (strings) match on the value's runtime type, not the declared type, so a string held in an
  `Object` or `CharSequence` field or an `Object[]` behind a collection is found. A number-mode match is recorded
  under its concrete type so writes and formatting use the real type.
- User-classes-only filtering treats array slots as not user-owned.
- Instance handles from earlier walks stay valid (a re-walk must not orphan a list still on screen); since they
  are weak, the map is only soft-capped to bound growth over a long session.
- Instance field reads give each reference field a fresh handle so the UI can navigate into it; primitives and
  strings are editable unless final.
- The JFR controller owns one recording at a time, the same one-capture model as the heap dump. Recordings dump
  to the target's temp directory, which JStudio reads directly because attach is local. JFR availability is
  probed so the agent can advertise `CAP_JFR` and the UI can hide the Recorder. Only event names present on the
  running JVM are enabled, which keeps category selection robust across versions. All controller calls run on
  the agent's single dispatch thread, so its one recording reference needs no locking. Disconnecting discards
  any recording so nothing is orphaned.

## Debugger (DebugSession)

- The JDI "dropbox" hand-off: the VM is suspended, up to a maximum number of live instances of a class are
  enumerated and parked in the agent's dropbox static field, and the VM resumes. The parked array holds the set
  strongly so it survives the resume until the agent consumes it; the agent clears the dropbox as soon as it has
  registered them, so the strong reference is brief. Where JDI cannot do this (no instance-info capability) the
  caller falls back to the agent's own walk. Stack-frame harvesting (this, arguments, and locals where a local
  variable table exists) augments the agent's roots rather than replacing them.
- While the application is paused, the agent's own threads are resumed so it can scan, read and edit a stable
  heap. They re-suspend at the next suspend-all pause, and the user's Resume releases everything together. The
  agent runs only reflection (no application code, no application locks), so it cannot deadlock on a lock a
  frozen thread holds.
- HotSwap invalidates breakpoints in the redefined class. Only the debug attribute changed, so code indices are
  unchanged and the breakpoints are re-installed from their specs at the same locations.
- A `char[]` is rendered as its quoted string content, capped, since that is how an in-flight password buffer is
  recognised.

## Project loading

- Live projects skip JVM-internal noise (`jdk/internal/reflect/GeneratedMethodAccessor*` created when a static is
  invoked reflectively through the agent) and hidden classes. A hidden class's name carries a `/0x<address>`
  suffix and cannot be fetched by name, so enumerating one only yields "class not loaded" noise.
- The project's class map is concurrent because live capture pulls classes in on a background thread while the
  EDT iterates them; weakly consistent iteration avoids `ConcurrentModificationException`.
- Synthetic local variable tables are widened: a slot whose entries share one type becomes a single method-wide
  entry, while mixed-type slots keep their scope-accurate entries. A class is redefined only when the widened table
  actually differs.

## JStudio side

- Live patching grafts only the changed methods onto the running class, and only those that resolve by exact
  signature in both classes. The result therefore always has the running class's member set, so no member-set
  check is needed, and it must not be done against the recompiled class, whose member set can carry spurious new
  methods when the decompiler or recompiler mis-resolves a descriptor.
- Scratch pad snippets are compiled for the highest class-file version among the target's pulled classes, which
  is at most the target JVM's version, so the target can always define them even when it is older than the JDK
  running JStudio. `--release` is passed only when the target is older. Project classes referenced by a unique
  simple name are imported automatically; default-package classes share the wrapper's package and need none.
- The JDI hand-off parks at most 200,000 objects per scan.
- The debugger injects a synthetic local variable table into a stripped class so paused frames report named locals
  matching the decompilation. It is best-effort and attempted once per class per session: skipped when the VM
  cannot HotSwap, the class is not loaded, the project lacks it, or it already has one.
- The agent's socket thread is kept running during a freeze so it can work against the frozen heap.

## More notes

- Class lists are eager and cheap (names and access flags); class bytes are fetched lazily when needed.
- The agent is pure `java.lang.instrument`, so it needs no native build on any platform; JStudio's jar bundles it as
  `agent/live-agent.bin`.
- The dropbox class lives under `com.tonic.live.agent` so the agent's own-class filter hides it from the class
  browser and JDI can find it by name on the agent's loader.
- A blocked thread waits on at most one monitor, so every node in the wait-for graph has at most one outgoing edge
  and every deadlock is a simple ring found by following successors.
- Debug frames are identified by index, because JDI frames become invalid when the target resumes; a frame is
  fetched again from the paused thread when its variables are read.
- The class-prepare hook is where a synthetic local variable table is injected before a stripped class runs; it
  fires on the event thread with the prepared thread suspended.
- `DebugSession.start()` must follow breakpoint installation, so with a `suspend=y` launch the requests are in place
  before VMStart resumes; that is what lets pre-set breakpoints catch startup. `connectWithRetry` tries 50 times at
  200 ms because a freshly launched JDWP listener may not be up yet.
- A breakpoint's bytecode offset is its single source of truth: the source view maps to it through the line maps
  and the bytecode view through the disassembly index, so a breakpoint set in one appears in the other. Breakpoints
  can be set before a debugger attaches, persist across sessions and are re-armed on every connect. On the paused
  line, right-click still removes the breakpoint while left-click resumes.
- Capture events arrive on the client's reader thread, which must not issue protocol requests, so captured class
  bytes are parsed on a separate executor.
- One parsed heap dump serves every class, so switching tabs only re-filters; a new dump is taken only on an explicit
  refresh or the first entry into Live Instances. At most one HPROF file exists at a time and it is deleted on
  detach. The HPROF reader indexes object ids to file offsets in one streaming pass and decodes fields by seeking, so
  memory stays bounded.
- `redefineClasses` accepts method-body changes only (lambdas and invokedynamic included). The bytecode editor's
  whole-class path has no decompile round trip, so its member set already matches the running class.
- Method bodies are compared by their canonical printed AST, so reformatting alone is not a change; methods present
  in only one revision are member changes, which redefinition cannot apply.
- The scratch pad's JDK class index covers `java.base` plus a curated set of packages, which keeps it fast and avoids
  simple-name clashes such as `java.awt.List`. The compiler's project classpath excludes `java.*` and `sun.*` so javac
  resolves those from its platform classpath. The first scratch-pad run in a session asks for confirmation: running
  arbitrary code in a live JVM is as powerful as redefinition.
- JFR event fields are read through `hasField`, because event schemas differ across JDKs (`jdk.ObjectAllocationSample`
  on 16 and later, TLAB events on 11).
