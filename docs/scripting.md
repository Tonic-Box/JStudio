# Scripting engine

Design notes for `com.tonic.script`. The code carries no comments; the reasons behind its less obvious choices
are recorded here.

## Bridges

- `ast`, `ir` and `annotations` handlers delete only when they return the explicit `ast.remove()`, `ir.remove()`
  or `annotations.remove()` sentinel. Returning null, nothing, or the original keeps it, so a handler that forgets
  to return never deletes anything.
- `AnnotationBridge` reads a class's attributes through reflection on `ClassFile.classAttributes`, because YABR
  has no getter for them.
- The `intLiteral`-style names on the AST factory are aliases kept so older scripts keep working.

## Running scripts (ScriptRunner)

- Every pass defines inert `ast`, `ir` and `annotations` bindings for the bridges it does not apply, so the whole
  script runs in any pass without failing on an undefined bridge ("Cannot call non-function"). Only the pass's
  own bridge has its handlers applied.
- An edited AST is lowered back to bytecode (AST to IR to bytecode). YABR's decompiler cannot round-trip every
  method losslessly (complex control flow, lambdas), so the result is verified: the method is decompiled again and
  its sorted multiset of `owner#name` call signatures must be exactly what the edit intended. If calls were lost
  or changed beyond the edit, the original bytecode is restored (the IR lift and lower round trip is faithful) and
  the method is skipped rather than cemented in a corrupt state.
- After a class is modified its stack-map frames are recomputed, then its decompiled-source and per-method IR
  caches are invalidated so every view regenerates from the new bytecode. A frame computation failure is
  reported and does not stop the run.

- `ScriptRunner` was extracted from the Script Editor so the editor and the AI assistant share one implementation
  with no UI code: the editor's output sink appends to its console on the EDT, the assistant's streams to the Script
  Console tab. Each sink call receives one newline-terminated line, and runs are synchronous.
- `live` bridge operations are synchronous protocol calls on the script thread.

## Language

- Semicolons are optional wherever a statement can end.
- `+` concatenates when either side is a string, otherwise it adds numbers.
- Objects, functions and arrays are truthy.
- `?.` short-circuits to null when the receiver is null; `&&` and `||` short-circuit.

## Script store

- Scripts are stored as JSON, and plain `.js` files in the scripts directory are loaded as well. The JSON is read
  with a small hand-written extractor (quoted values with escaped quotes), so the store needs no JSON library.
