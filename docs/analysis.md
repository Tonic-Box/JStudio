# Analysis services

Design notes for `com.tonic.service` and related model code. The code carries no comments; the reasons behind
its less obvious choices are recorded here.

## Cross-references

- A class query indexes every reference whose target class matches, including reads, writes and calls of the
  class's own members (an internal `this.field = ...`). Those are member usages, not type usages, so a class
  search keeps only references to the class as a type: `new`, casts, `instanceof` and type positions, which carry
  no target member.

## Dead code

- A class is live if it has a reachable method, is referenced as a type by reachable code, or is a user supertype
  of a live class; a live class keeps its user supertypes or the hierarchy breaks.
- A method that overrides one declared by a JDK or library supertype is kept. User supertypes are walked through
  their bytecode up to the external boundary; external supertypes are resolved by reflection, which covers the JDK
  and the classpath whether or not the project's pool loaded JDK classes.
- Compile-time constants (static final primitives and strings) are inlined at their use sites and look
  unreferenced, so they are kept.
- Removal runs in a fixed order: writers of write-only fields are patched (each store becomes pops) before the
  fields go; then dead methods and fields are removed; then whole dead classes (which rebuilds the pool and fires
  `ProjectUpdatedEvent`); then decompilation caches of mutated classes that remain are invalidated.

## Projects

- `ProjectModel` bumps a counter on every bytecode mutation; the VM uses it to invalidate its cached class
  snapshot. The class pool is rebuilt from the remaining user classes after classes are removed.
- The class pool used for execution can include JDK classes so recursive execution can step into JDK methods.
- Local history reads blobs from the in-memory pending set first and falls back to the store on disk, with one
  zip open for the lot.

## Local variable renaming

- A rename targets the local variable table entry for the clicked occurrence: same slot, name and scope. Where no
  entry's scope covers the offset, any entry with that name is used.

## Updates and the CLI

- The updater backs up the current jar best-effort and does not touch it until the new copy has succeeded; a
  leftover temporary or backup file is harmless.

## More notes

- Find Usages and the usage lenses share `XrefQueryService`, so their counts match. Building its database scans
  every class, so it runs off the EDT; results leave out JDK callers and class-level references with no source
  method.
- Local history snapshots build up in memory and reach the history zip only when the project is saved, so an
  unsaved session leaves no trace; reopening a saved project restores its newest snapshot, so bytecode edits survive
  sessions. Snapshots run synchronously so they capture the bytes from before the change. Class bytes are
  deduplicated blobs referenced by hash, so an unchanged class costs nothing across snapshots.
- Local variables are renamed through the LocalVariableTable because the decompiler takes local names from it: the
  new name appears on the next decompile with no source rewrite. A method without a table first gets one built from
  the decompiler's recovered names so the rename persists. The target keeps the clicked occurrence's exact scope,
  which separates variables sharing a slot and name in disjoint regions (three consecutive loops' `i`); the same
  name is allowed in scopes that do not overlap.
- Synthetic table widening lets a live debugger see named locals anywhere in a method. Only same-typed slots
  collapse, so a slot reused for different types is never misread, and only the debug attribute changes, leaving
  bytecode, offsets, frames and breakpoints valid.
- Runs use a separate JVM so the target's `System.exit`, uncaught exceptions or native crashes cannot take down
  JStudio; run output callbacks fire off the EDT. Export as JAR and Run share `ProjectJarExporter`, so both reflect
  transforms and edits. JDKs are looked for in JAVA_HOME, the Adoptium, Corretto and Zulu install directories,
  `~/.jdks` and `~/.sdkman`.
- Dead-code roots are every `main` and static initializer, every override of a method declared outside user code
  (framework callbacks such as `KeyListener.keyPressed` are never removed), the keep-list, and optionally all public
  members. YABR's call graph follows virtual dispatch. The write-only field rewrite is a same-size patch:
  `putstatic` and `putfield` are three bytes, and `pop` or `pop2` (plus a `pop` for `putfield`'s receiver) padded
  with `nop` has the same net stack effect, so offsets, branches, frames and max stack are untouched.
- A rename or script can change references in any class, so every decompilation cache is cleared, not only the
  changed class's. Restores rebuild the class pool once and leave library and JDK entries alone; callers refresh the
  UI afterwards. The dirty flag is what makes closing a project prompt to save.
- `SourceLineMaps` holds the offset and line logic in one place. `sourceLineForPc` prefers the next entry at or after
  the offset, because an inlined expression is emitted by the statement that consumes it; `pcSpanForSourceLine`
  anchors a statement at its defining instruction (the `ireturn` of `return f(x)`), so its span starts just after the
  previous statement's anchor.
- The event bus delivers by exact event class (a handler for a superclass never sees subclasses). Handlers run
  immediately when posted on the EDT and are queued with `invokeLater` otherwise. Renames run on the AI chat worker
  thread, which is why their event is handled on the EDT.
- The updater runs from the downloaded jar, not the locked running one, and keeps a `.bak` of the target. Copying
  with retries is the lock probe: on Windows the running JVM holds the jar without write sharing, so the copy fails
  until it exits; elsewhere it succeeds at once. If the copy never succeeds the target is untouched and the old
  version relaunches.
