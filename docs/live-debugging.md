# JStudio Live - attach to a running JVM

JStudio Live attaches to an external running JVM (JDK 11+) and lets you browse its loaded classes through
YABR, live-patch method bodies ("patch & continue"), capture runtime-generated classes, and detect
deadlocks.

## Capabilities

| Capability | Mechanism | UI |
|---|---|---|
| Browse loaded classes -> YABR views | `Instrumentation.getAllLoadedClasses` + `retransformClasses` capture | Attach loads a live project |
| Live method-body patch ("patch & continue") | a source edit grafts only the **changed** method bodies onto the running class (so untouched methods and synthetic members keep their exact bytes); the bytecode editor sends the whole class. Either way -> `redefineClasses` | Recompile in the source view, or Attach -> Patch Live Class |
| Live heap snapshot | on-demand HPROF heap dump, parsed and browsed by class/instance with field/array inspection | per-class **Live** view (Instances) |
| Live statics | read (and inline-edit primitive/String) a class's static fields; list and invoke its static methods | per-class **Live** view (Statics) |
| Live profiler | per-second snapshot of CPU, heap, metaspace, GC, threads, and loaded classes (JMX MXBeans) rendered as live graphs | **Profiler** right-dock tool |
| JFR recorder | start/stop a Flight Recorder recording in the target (built-in profile + CPU/alloc/locks/exceptions toggles), snapshot the in-progress buffer, export the `.jfr`, and analyze it in-app (flame graphs, hot methods, allocations, locks, exceptions) with frame-to-source navigation | **Recorder** right-dock tool (shown when the target supports JFR); double-click a capture to analyze |
| Runtime-generated class capture (packers, defineHiddenClass, ASM) | a `ClassFileTransformer` streams non-bootstrap loads with real bytes | Attach -> Capture Runtime Classes |
| Deadlock detection | `ThreadMXBean` wait-for graph -> cycle find (`Deadlocks`) | Attach -> Find Deadlocks |
| Thread list | `ThreadMXBean` | per-class **Live** view (Threads), scripting (`live.threads()`) |
| Java scratch pad (eval) | snippet compiled in-memory with `javac` against the target's pulled classes, shipped to the agent, and run via a throwaway child of a chosen context class's loader; stdout/result/exceptions returned | Attach -> Java Scratch Pad... |

Scriptable via the `live` binding: `live.threads()`, `live.deadlocks()`, `live.captureLoads(bool)`,
`live.redefineFromProject("com/foo/Bar")`.

## Using it (UI)

1. Start your target program (any JDK 11+ process).
2. **Attach -> Attach to Live JVM…**, pick the target (toggle "Include JDK classes" off for a smaller
   tree), Attach. This replaces the current project with one built from the target's live classes and
   shows a progress bar.
3. Browse classes in the navigator (open one to decompile via YABR). Use **Attach -> Patch Live Class**,
   **Find Deadlocks**, **Java Scratch Pad...**, **Capture Runtime Classes**, and **Detach**. These items
   appear only while attached.
4. While attached, an open class gains a **Live** section in its view dropdown - **Instances** (from a heap
   snapshot), **Statics** (view/edit static fields, invoke static methods), and **Threads**. Editing in the
   source view and recompiling live-patches the running class (see the patch row above).
5. The **Profiler** right-dock tool shows live graphs (CPU, heap, metaspace, GC, threads, loaded classes),
   sampled once a second; it pauses while its tab is hidden.
6. The **Recorder** right-dock tool (shown when the target JVM supports JFR) records a Flight Recorder
   session: pick a profile (low-overhead or detailed) and event categories (CPU, allocations, locks,
   exceptions), **Start**, **Snapshot** the in-progress buffer at any time, then **Stop**. Captured `.jfr`
   files are listed; **Save As...** exports one (open it in JDK Mission Control), and **double-click** a
   capture (or **Analyze**) to open the in-app analysis window: an Overview plus CPU (flame graph + hot
   methods), Allocations, Locks, and Exceptions tabs (shown only for recorded categories). Double-click a
   flame-graph frame or a hot-method row to open that method's decompiled source.
7. **Attach -> Java Scratch Pad...** opens a non-modal pad: write Java statements (an optional trailing
   `return <expr>;` becomes the result, `import` lines at the top are supported), pick a context class, and
   **Run** (Ctrl+Enter). The snippet compiles against the target's pulled classes and runs inside it; stdout,
   the returned value, and any exception come back to the console. Completion (as you type, or Ctrl+Space)
   covers keywords, the project's classes, and common JDK types (`java.lang`/`util`/`io`/`time`/`nio.file`/
   ...); a JDK or named-package class used by simple name is auto-imported. The first run per session asks for
   confirmation.

## Building & bundling

The agent is built by the `:live-agent` module and staged into the jar by the `stageJavaAgent` Gradle task
(a dependency of `processResources`, so it runs on `build`/`run`/`jar`/`shadowJar`). `./gradlew build`
produces a self-contained `build/libs/JStudio.jar` containing the agent at `agent/live-agent.bin` - no
native toolchain involved. Manifest sets `Agent-Class`/`Premain-Class` and `Can-Redefine-Classes` /
`Can-Retransform-Classes`.

## Limitations

- `redefineClasses` is **method-body only** - add/remove fields or methods, or hierarchy changes, are
  rejected by the JVM (surfaced as an error).
- The scratch pad runs **fresh** code (not paused-frame eval): it reaches the target's statics/singletons via
  public APIs. Each run is stateless (a new class in the default package): default-package classes are
  referenceable directly, named-package classes by simple name are auto-imported (or use a fully-qualified
  name), and only **public** members of named-package classes are reachable. The eval runs on the live
  connection thread - a blocking or looping snippet stalls the connection until it returns (no cancel).
