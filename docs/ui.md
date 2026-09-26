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
  metrics are JVM-wide, not view-specific. The timer stops on detach.
- Charts keep 180 samples per series, about three minutes at one sample a second.
- Flame graph frames narrower than three pixels are not drawn; zooming reveals them. Colour hue follows the package
  so a package reads as one family, with brightness varied per method.
- The recorder disables every action while a request is in flight, because the connection is serial.
