# JStudio Live - attach to a running JVM

JStudio Live attaches to an external running JVM (JDK 11+) and lets you browse its loaded classes through
YABR, live-patch method bodies ("patch & continue"), capture runtime-generated classes, and detect
deadlocks. It uses a **pure-Java `java.lang.instrument` agent** - no native code, so it works on any
OS/arch with no native build or toolchain.

## Capabilities

| Capability | Mechanism | UI |
|---|---|---|
| Browse loaded classes -> YABR views | `Instrumentation.getAllLoadedClasses` + `retransformClasses` capture | Attach loads a live project |
| Live method-body patch ("patch & continue") | a source edit grafts only the **changed** method bodies onto the running class (so untouched methods and synthetic members keep their exact bytes); the bytecode editor sends the whole class. Either way -> `redefineClasses` | Recompile in the source view, or Attach -> Patch Live Class |
| Live heap snapshot | on-demand HPROF heap dump, parsed and browsed by class/instance with field/array inspection | per-class **Live** view (Instances) |
| Live statics | read (and inline-edit primitive/String) a class's static fields; list and invoke its static methods | per-class **Live** view (Statics) |
| Runtime-generated class capture (packers, defineHiddenClass, ASM) | a `ClassFileTransformer` streams non-bootstrap loads with real bytes | Attach -> Capture Runtime Classes |
| Deadlock detection | `ThreadMXBean` wait-for graph -> cycle find (`Deadlocks`) | Attach -> Find Deadlocks |
| Thread list | `ThreadMXBean` | per-class **Live** view (Threads), scripting (`live.threads()`) |

Scriptable via the `live` binding: `live.threads()`, `live.deadlocks()`, `live.captureLoads(bool)`,
`live.redefineFromProject("com/foo/Bar")`.

## Using it (UI)

1. Start your target program (any JDK 11+ process).
2. **Attach -> Attach to Live JVM…**, pick the target (toggle "Include JDK classes" off for a smaller
   tree), Attach. This replaces the current project with one built from the target's live classes and
   shows a progress bar.
3. Browse classes in the navigator (open one to decompile via YABR). Use **Attach -> Patch Live Class**,
   **Find Deadlocks**, **Capture Runtime Classes**, and **Detach**. These items appear only while attached.
4. While attached, an open class gains a **Live** section in its view dropdown - **Instances** (from a heap
   snapshot), **Statics** (view/edit static fields, invoke static methods), and **Threads**. Editing in the
   source view and recompiling live-patches the running class (see the patch row above).

## Building & bundling

The agent is built by the `:live-agent` module and staged into the jar by the `stageJavaAgent` Gradle task
(a dependency of `processResources`, so it runs on `build`/`run`/`jar`/`shadowJar`). `./gradlew build`
produces a self-contained `build/libs/JStudio.jar` containing the agent at `agent/live-agent.bin` - no
native toolchain involved. Manifest sets `Agent-Class`/`Premain-Class` and `Can-Redefine-Classes` /
`Can-Retransform-Classes`.

## Limitations

- `redefineClasses` is **method-body only** - add/remove fields or methods, or hierarchy changes, are
  rejected by the JVM (surfaced as an error).
