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
