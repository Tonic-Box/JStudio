# Editor

Design notes for `com.tonic.ui.editor` and the debugger gutters. The code carries no comments; the reasons behind
its less obvious choices are recorded here.

## Recompiling edited source (SourceCompiler, SourceCodeView)

- Recompile is transactional. The class is lowered into a byte-for-byte working copy, which is swapped into the
  pool under the class's name so references to the class itself and to new members resolve against the version
  in progress. On success the copy stays and the caller commits it; on failure (a lowering error or a failed
  verification) the original is put back, so a failed recompile never corrupts the model. Only when the copy
  cannot be made does lowering fall back to the original.
- Recompile is method-scoped: an existing method whose body did not change keeps its original bytecode, so
  editing one method never re-lowers, and risks perturbing, the others. An empty or undeterminable diff falls
  back to recompiling everything, so an edit is never silently dropped.
- A method that cannot be lowered keeps its original bytecode (a new one is reported) rather than aborting the
  whole recompile and discarding every other edit.
- Constructors are re-lowered into their `<init>` entries: only changed ones in method-scoped mode, all of them in
  the whole-class fallback, so a constructor edit is never silently dropped.
- Synthetic methods produced while lowering (lambda bodies, array constructors) are added only when the original
  class lacks one with the same name and descriptor, so round-tripped classes keep their working synthetic bodies.
- Methods the edited source no longer declares are removed, except constructors, static initializers and
  compiler-generated members (synthetic, `lambda$`, `access$`, `$deserializeLambda$`), which the decompiled
  source represents implicitly.
- New fields are created with their JVM default so references resolve; existing fields are untouched.
- `<clinit>` is regenerated from static field initializers, then static blocks in declaration order. A static
  field the original class initializes through a `ConstantValue` attribute is skipped: synthesizing a `putstatic`
  for it would add a spurious static block on every round trip.
- Verification errors are filtered to the methods that were re-lowered, so a pre-existing quirk in an untouched
  method never blocks an edit, and each error points at its member's source line rather than line 1. A failure
  inside the verifier itself does not block an otherwise valid recompile.
- Method descriptors are built with reference types resolved through the imports (`Frame` becomes
  `Ljava/awt/Frame;`) and nested classes spelled with `$`; otherwise the descriptor would not match the original
  and the method would be treated as new.
- The pre-edit bytecode is snapshotted into local history before compiling, because the compiler mutates the
  class in place and a later snapshot would record the changed bytes. The pre-edit source is captured too,
  because a successful recompile overwrites it and the live patch diffs against it.
- After a live patch the in-memory class is replaced with what is now running, so the bytecode view and the next
  recompile baseline match the live class. A failure to refresh it is swallowed: the patch already succeeded.

## Source navigation

- A line's declaration comes from the decompiler's member spans (a line belongs to the member whose span contains
  it). Spans are unusable while annotations are hidden, because hiding them removes lines, so navigation then
  falls back to regex recognition of the line.
- The method at the caret is the method whose span contains the caret line; outside any method there is none, and
  actions that take the current method fall back to the whole class.
- Scrolling to a declaration is deferred with `invokeLater`: when a class has just opened from cached source the
  text component has not been laid out, and an immediate scroll landed nowhere (the old "navigate twice" bug).
- Token selection prefers a real reference (`.token` or `token(`) over a match inside a string or character
  literal.
- Hidden annotations are tracked with an original-to-filtered line map. Only whole lines are removed, so the map is
  exact; a declaration line that was removed (an annotation) maps forward to the next kept line.
- Renaming a local edits the method's local variable table and re-decompiles, restoring the caret and scroll
  position.

## Gutters and overlays

- The Run gutter badge listens on the gutter's icon-row child, because AWT dispatches to the deepest component,
  not the parent gutter; the point is converted into the text area to correct for scrolling. While a run is
  active it auto-attaches a live session, so the badge stays visible then and turns into a stop control; only a
  manual attachment hides it.
- The breakpoint gutter's click handler is removed before it is added, so there is exactly one listener even after
  a re-decompile re-creates the gutter; otherwise a click toggled twice. The paused line always carries the resume
  badge, so removing its breakpoint mid-pause never removes the way to resume.
- Source-view breakpoints map a clicked line to the lowest bytecode offset on it. If the offset-to-line maps were
  dropped by a cache invalidation while the tab stays open, they are regenerated from the unchanged bytecode.
- Runtime value hints are painted, never inserted, so the decompiler's line maps stay valid. Locals are placed at
  their declaration line (the local variable table start offset mapped to a line); `this`, arguments and anything
  without an in-scope entry go on the paused line. Inline values are truncated and the full values are in the
  values dialog; the editor widens so a hint trailing off the view can be scrolled to.
- Usage lenses are computed off the EDT from the same xref database Find Usages uses.

## Find panel (SearchPanel)

- Matches are scanned off the EDT from a text snapshot taken on the EDT and invalidated on any document change.
- Only matches in the viewport plus two screens either side are marked, capped at 1000, because the text area's
  position creation cost is quadratic in the number of marks. Highlights refresh, debounced, as the view scrolls.
  The current match gets its own opaque highlight, distinct in hue from the pale marks.

## Views

- A method at or below 16 bytes of code is treated as trivial in the bytecode view.
- The bytecode line index mirrors the bytecode view's layout: structural comments and method headers at column 0,
  disassembly indented two spaces.
- The debugger's source view shows the method's slice of the class source and rebases between document and display
  lines, keeping whole-document line numbers so they match the editor. Before it is laid out it has no geometry,
  so scrolling is deferred.

## More notes

- The find panel debounces typing by 200 ms and scroll refreshes by 50 ms, keeps match offsets sorted so moving
  between matches is a binary search, and does not use RSyntaxTextArea's SearchEngine, which is quadratic in the
  number of matches.
- `EditorTab.reload` exists for classes changed outside the tab (an AI rename, a script run). It must call the
  source view's `reload`, not `refresh`: `refresh` does nothing once a view has loaded and would keep showing the
  stale cached source.
- Source-offset navigation looks up the ceiling entry first, because an inlined expression is emitted by the later
  statement that consumes it, then the floor entry, each checked for the token, and finally scans the lines between.
  Without a line map it returns false so the caller falls back to method-level navigation.
- The usage lens sits on the blank line the decompiler leaves between members (an inlay without touching the
  document), falling back to the end of the declaration line; one placement algorithm serves every member kind.
- `SourceAssembler` swaps in only the header and the method bodies that have a cleaned replacement; imports, gaps
  and other methods stay verbatim, so a failed piece never drops a member. It takes only a class model and strings
  because the span types are YABR types plugins cannot reach. `ReadonlyJavaView` exists for plugins whose classpath
  excludes RSyntaxTextArea.
- In live-patch mode the compile toolbar patches the running JVM, since a recompile alone does not affect it.
- `BytecodeLineIndex` is the only code that depends on the disassembly text format: a method header is a column-0
  `//` line whose last token contains `(`, and an instruction line is an indent, a decimal offset and a colon.
  `BytecodeFormatter` indents every verbose comment so none look like headers. All disassembly comes from YABR's
  `CodePrinter.prettyPrintCode`; the whole-class and single-method helpers exist for plugins, and `skipTrivial`
  and `indexOf` keep down the token cost of feeding a class to an LLM.
- The dual view hosts its own pane instances rather than reparenting the tab's views; font, wrap and method
  scrolling apply to both panes, and the source pane keeps annotations on so its lines match the decompiler's maps.
  `SourceBytecodeLinker` is the one place that translates between panes, many-to-one both ways; an unmapped line
  highlights one side only.
- A bytecode PC highlight requested during a refresh is deferred until the load finishes, because the load
  replaces the document and would wipe it.
- Editor views get the theme lifecycle from `ThemedJPanel`: register in the constructor, unregister in
  `removeNotify`, re-theme in `applyChildThemes`, where text views add their token styling after `applyTextTheme`.
