# JStudio Features

A reference of the features available in JStudio, a Java reverse-engineering and static/dynamic
analysis IDE built on the [YABR](https://github.com/Tonic-Box/YABR) bytecode library.

## Projects & Files

- **Open** — load a `.jar`, a single `.class`, or a directory of classes; drag-and-drop onto the window (append or replace).
- **Projects** — save/load a `.jstudio` project database holding annotations (bookmarks, comments, renames) keyed to the target's SHA-256.
- **Recent files** — quick re-open list with a clear option.
- **Export** — single class, all classes to a directory, or repackage everything back into a JAR.
- **New class** — synthesize a class/interface/enum/annotation from scratch.
- **Resources** — browse, import, view, and delete non-class files bundled in the artifact.

## Editor & View Modes

A tabbed editor renders each class through multiple views (selectable from the view dropdown):

- **Source** — decompiled Java with syntax highlighting; editable with recompile-back-to-class.
- **Bytecode** — JVM disassembly with color-coded opcodes, offsets, and resolved operands.
- **Dual** — bytecode and source side-by-side, linked by double-click (click a line on either side to highlight the corresponding line(s) on the other).
- **SSA IR** — Static Single Assignment intermediate representation.
- **AST IR** — abstract syntax tree of the decompiled source.
- **LLVM IR** — LLVM lowering of the SSA form.
- **Control Flow (CFG)** — basic-block graph with bytecode/IR toggle and per-block detail.
- **Program / System / Code Property graphs (PDG/SDG/CPG)** — dependence and property graph views.
- **Call Graph** — method call hierarchy with depth control and invoke-type-colored edges.
- **Const Pool** — constant-pool entry browser with filtering.
- **Attributes** — class/field/method attribute tree.
- **Statistics** — class metrics dashboard (method sizes, complexity, opcode distribution).
- **Hex** — raw class-file bytes.

### Source view specifics

- **Recompile** — edit decompiled source; a floating toolbar shows error/warning counts and recompiles changes back into the class.
- **Go to Definition** — Ctrl+Click a type/method/field to jump to its declaration (span-based, with a text fallback).
- **Usage Lenses** — clickable "N usages" counts above each class, method, and field declaration that open Find Usages; toggle via View ▸ Usage Counts.
- **Hide Annotations** — toggle annotation display in decompiled output.
- **Comments & Bookmarks** — shown as gutter icons inline.
- **Find/Replace** — in-view search panel.

## Navigation

- **Navigator tree** — package/class hierarchy with live search filtering and a separate resources root.
- **Context actions** — Find Usages, Rename (class/method/field with refactoring), Execute Method, Fuzz & Generate Tests, copy name/descriptor/reference, add class, import resource.
- **Find Usages** — bidirectional cross-references; results navigate to the exact source line and select the referenced name. Class searches resolve to type references; field/method searches to their access sites — including references inside inlined lambda bodies.
- **History** — back/forward navigation; breadcrumb bar per tab; go-to-class and go-to-line.
- **Inspector** — properties panel showing class/method/field metadata and complexity metrics.

## Query & Search

- **Query Explorer** — a composable query DSL for code searches with syntax highlighting and a hierarchical results table (see [query-dsl.md](query-dsl.md)).
- **Search** — text/regex search across classes, methods, and fields.
- **Strings** — extract and search constant-pool strings with frequency and pattern matching.
- **Similarity** — detect duplicate and renamed methods.

## Static Analysis

- **Code Analysis (simulation)** — abstract interpretation that finds opaque predicates, dead code, recovered (decrypted) strings, taint flows, and method purity; results table with filtering and JSON/HTML export.
- **Cross-reference database** — every class/method/field reference, used by Find Usages and the usage lenses.
- **Complexity metrics** — cyclomatic complexity, block/branch/loop counts.

## Dynamic Analysis (interpreting VM)

- **Bytecode Debugger** — step into/over/out and resume; breakpoints (gutter click or context menu); tabbed Bytecode/Source views that track the executing line; editable operand stack and locals; call-stack navigation; stub or recursive execution; trace recording with Markdown export.
- **VM Console** — interactive shell to execute methods, inspect variables, and create objects.
- **Execute Method** — run a method with configured arguments and inspect return value, exceptions, call trace, and output.
- **Heap Forensics** — object browser, field/array inspection, allocation timeline, mutation and provenance tracking, and snapshot diffs.
- **VM lifecycle** — initialize, reset, and inspect VM status.

## Live Debugging (attach to a running JVM)

Attach to an external running JVM (JDK 11+) via a `java.lang.instrument` agent. See [Live Debugging](live-debugging.md).

- **Attach & browse** - load the target's classes into the project and decompile them via YABR.
- **Patch & Continue** - recompile the open class and live-redefine it; a source edit grafts only the changed method bodies onto the running class, so untouched methods and synthetic members are preserved.
- **Live heap** - take an HPROF snapshot of the target and browse instances by class with field/array inspection.
- **Live statics** - view and inline-edit a class's static fields, and invoke its static methods.
- **Live profiler** - per-second live graphs of CPU, heap, metaspace, GC, threads, and loaded classes.
- **Live threads & deadlocks** - list threads and detect deadlock cycles from the wait-for graph.
- **Runtime class capture** - stream classes defined at runtime (packers, `defineHiddenClass`, ASM) into the project.

## Deobfuscation

- **Encrypted string detection** — heuristic scan (Base64/hex/entropy/non-printable) of constant-pool strings.
- **Decryptor identification** — locate likely decryption methods by signature and naming.
- **Auto-decrypt** — execute detected decryptors (single or batch) to reveal plaintext.
- **Name recovery** — batch-deobfuscate class/method/field names.

## Transforms

- **SSA optimizations** — constant folding, copy propagation, dead-code elimination, strength reduction.
- **Script Editor** — custom scripting language for AST/bytecode/IR manipulation, run against a class/method.
- **Recompute stack frames** — rebuild stack map frames.

## Test Generation

- **Method execution** with custom arguments, **fuzz testing** with constraint-based input generation, and **JUnit 4/5 export** of generated cases.

## Extensibility

- **Scripting bridges** — script API over YABR (project, call graph, data flow, IR, patterns, types, simulation, instrumentation, results).
- **Plugin API** — analyzer and transformer plugins with a project/analysis/YABR access surface and result collection.
- **CLI** — `run`, `repl`, `batch`, and `info` subcommands for plugin/script execution and target inspection (`--cli`).

## UI & Configuration

- **Panels** — toggleable navigator, properties, console, and a tabbed bottom panel (Find Usages results, bookmarks, comments, CFG block details).
- **Console** — multi-level logging output.
- **Bookmarks & Comments** — persistent annotations on classes/members, listed in dedicated panels and shown in the gutter.
- **Themes** — 8 built-in themes with live switching.
- **Preferences** — editor font, theme, word wrap, whether to load JDK classes for execution, and update-check behavior; window/layout state persisted.
- **Self-update** — checks GitHub releases and can download, verify (SHA-256), and swap in a new build.
- **Status bar** — progress, current position/view, and transient messages.
