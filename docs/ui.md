# User interface

Design notes for the parts of `com.tonic.ui` not covered by `layout.md`, `editor.md` or `live.md`. The code carries
no comments; the reasons behind its less obvious choices are recorded here.

## Navigator

- Tree expansion and selection are captured before a rebuild and restored afterwards by name-path keys (display
  texts joined with newlines, which names cannot contain), so they survive the new node objects. A renamed node's
  key changes, so only that node loses its state. The capture walk stops at collapsed nodes, bounding it to the open
  subtree.
- With no project open, creating a class creates a new Untitled project to hold it, but only after the dialog is
  confirmed, so cancelling leaves the no-project state untouched.
- A plugin's context-menu provider that throws must not break the built-in menu, so providers are isolated.

## Query explorer

- The panel's own preferred sizes do not pin the tool column's width; its scroll panes absorb the shortfall so the
  split stays freely resizable.
- Loading a project replaces the class pool, so earlier results are cleared and the query service is rebuilt for
  the current project.
- Opcode mnemonics for highlighting come from YABR's `Opcode` enum so they never drift.

## Local history

- Diffs decompile both sides fresh, never from the cache: after a recompile the cache holds the user's hand-edited
  source, which would diff noisily against the snapshot's decompiler output.
- Snapshot rows are filled lazily with the classes whose bytes differ from the current project.
- In the line diff the common suffix starts at different indices on the two sides; pending deletes and inserts are
  paired as changes and the remainder emitted as deletes or inserts.

## Themes and icons

- The view-mode dropdown is themed explicitly so it is not the look-and-feel default on first show.
- The analysis icon is an ascending bar chart, deliberately distinct from the run triangle so "Run Analysis" is not
  mistaken for running a main method.
- The runnable overlay draws a background disc before the green triangle for contrast with the base icon.

## Application

- `JStudio` installs diagnostics for a recurring silent close, appending to `~/.jstudio/close-diagnostics.log`:
  a security manager that records any `System.exit` or `halt`, a default uncaught-exception handler, a shutdown-hook
  thread dump, a startup marker, and an EDT wrapper that logs and rethrows any exception, so a silent EDT death is
  captured.
- Navigation history is browser-style: opening a class while not at the end drops the forward entries.
- The status bar clears messages after five seconds.

## VM tools

- The VM caches a defensive snapshot of the project's user-class bytes and reuses it across isolated VM instances
  until the project's bytecode changes; a class that cannot be serialized is left out.
- Debugger breakpoints are toggled against the displayed method, which differs from the entry method while
  stepping through callees in recursive mode.

## Profiler and recorder

- The profiler polls whether or not its tab is visible, so sampling starts as soon as the panel is added on attach;
  metrics are JVM-wide, not view-specific. The timer starts and stops as the panel is added to and removed from a
  window, and at most one metrics request is in flight.
- Charts keep 180 samples per series, about three minutes at one sample a second.
- Flame graph frames narrower than three pixels are not drawn; zooming reveals them. Colour hue follows the package
  so a package reads as one family, with brightness varied per method.
- The recorder disables every action while a request is in flight, because the connection is serial.

## VM isolation

- `SnapshotClassPool` serves user classes from a frozen byte snapshot parsed on first access, so edits to the live
  project cannot disturb a running VM; JDK and library classes are never edited and come from the live pool. The
  YABR engine has no global mutable state, so separate `VmInstance`s, one per AI session, do not interfere.
  `VmSupport` is shared by that path and `VMExecutionService`.
- The debugger's source view shows only the current method, cut from the decompiled class by the per-method spans.
  The executing statement comes from the PC through the offset-to-line maps and breakpoints go back through them;
  text, maps and spans are shared with the editor through the class model's cache. Stepping into another method
  re-cuts the view first.

## Graphs, queries and updates

- `DotParser` supports `digraph` and `graph`, node and edge statements and edge chains, node, edge and graph
  attribute defaults, subgraphs and brace blocks (flattened), the attributes label, shape, color, fillcolor, style
  and rankdir, all three comment forms and quoted ids. Clusters, ports, ranks and HTML labels are accepted and
  ignored; `DotGraph` models only what the renderer honours.
- `DotGraphBuilder` mirrors the call-graph renderer (insert in one model update, then a hierarchical layout) with
  inline styles so DOT colours survive. `DotGraphView.render` is the only entry point, keeping `com.mxgraph` out of
  callers' APIs. `DotGraphPanel` rebuilds from the DOT source; the AI chat embeds it as a tab and `DotGraphDialog`
  is the popup fallback.
- The query highlighter leaves accessor names plain so it never drifts from the accessor registry; keywords are
  case-insensitive to match the lexer.
- Line diffs trim the common prefix and suffix first; only the middle goes through LCS, capped at four million
  cells, beyond which it becomes plain delete and insert blocks.
- Remove Dead Code has no undo, so its preview tree is the safety net.
- `AppVersion` reads `Implementation-Version`, which only a release jar has; in development runs it is null and
  update checks are skipped. After `UpdateInstaller.applyAndRestart` the caller must exit at once so the updater can
  replace the jar.

## Theme helpers

- `ThemeStyles` helpers copy existing inline styling exactly, changing how a colour is applied but never when; live
  re-theming stays with the `Themed*` base classes. The dual view's link highlight is separate from the current-line
  highlight so a linked line stands out from the caret line.
- `WrapLayout` exists because `FlowLayout` always reports a single-row preferred height, clipping wrapped rows.
- `SwingWorkers` hands the error callback the unwrapped cause, not the `ExecutionException`.
- Listener-count getters on `ThemeManager`, `ProjectDatabaseService` and `LocalHistoryService` exist only for leak
  tests.
