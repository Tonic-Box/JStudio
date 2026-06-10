# Query DSL

The Query Explorer (the **Query** tab in the right-side tool window) searches loaded bytecode with a
composable query language. Keywords are case-insensitive; quoted strings and type names match as
written. Double-clicking a result navigates to it (matched call/instruction sites navigate to the
exact bytecode offset).

## Structure

```
FIND <target> [IN <scope>] [WHERE <expr>] [ORDER BY col [ASC|DESC]] [LIMIT n]
```

**Targets:** `methods`, `classes`

`ORDER BY` sorts on a result column (`class`, `method`, `matches`), numeric-aware; `LIMIT` caps the
rows. The two clauses may appear in either order.

## Scope

| Scope | Meaning |
|---|---|
| `IN ALL` | all loaded classes (default) |
| `IN class "pat"` | classes matching a string or `/regex/` |
| `IN method "pat"` | methods matching a string or `/regex/` |
| `DURING <clinit>` | static initializers (optionally combined with a class pattern) |
| `DURING method "pat"` | methods matching a pattern |

## WHERE expression

| Form | Syntax |
|---|---|
| comparison | `accessor OP operand` |
| quantifier | `HAS\|ANY\|ALL\|NONE <selector> WHERE ( expr )` |
| count | `COUNT( <selector> [WHERE (expr)] ) OP n` |
| boolean | `expr AND expr` &nbsp; `expr OR expr` &nbsp; `NOT expr` &nbsp; `( expr )` |
| sequence | `SEQUENCE [ step, step, .. ]` (see [Instruction patterns](#instruction-patterns)) |

A bare accessor is truthy (e.g. `WHERE recursive`). Quantifier bodies rebind the subject: inside
`HAS call WHERE (…)` the bare atoms (`name`, `owner`, `arg`, …) refer to that call; inside
`HAS arg WHERE (…)` they refer to that argument. Quantifiers nest freely.

## Accessors

| Subject | Atoms |
|---|---|
| `method` | `name` `owner` `descriptor` `arity` `modifiers` `line` `opcodes` |
| `class` | `name` `modifiers` |
| `call` | `name` `owner` `descriptor` `arity` `kind` `opcode` `target` |
| `arg(n)` | `value` `type` `kind` `index` |
| `field` | `name` `owner` `descriptor` `kind` |
| `insn` | `opcode` `index` `line` |
| SSA / CFG | `recursive` &nbsp; `method.loops` &nbsp; `method.blocks` &nbsp; `(call\|insn).inLoop` &nbsp; `(call\|insn).loopDepth` |

## Selectors

`call` &nbsp; `arg` &nbsp; `insn` &nbsp; `field` — quantify over these with `HAS`/`ANY`/`ALL`/`NONE`/`COUNT`.

## Operators

```
==  !=  <  <=  >  >=    matches /re/    contains "s"    startsWith    endsWith    IN [a,b,c]
```

**Data-flow:** `flowsTo` / `flowsFrom` — the right side is an accessor, evaluated over the method's
SSA form (forward def-use reachability). Endpoints:

| Endpoint | Meaning |
|---|---|
| `param(n)` | the method's nth parameter value |
| `return` | any value the method returns |
| `arg(n)` | the nth argument of the current call (inside a `HAS call WHERE (…)` body) |
| `insn` | the value the current instruction defines (inside a `HAS insn WHERE (…)` body) |

`A flowsTo B` ≡ `B flowsFrom A`.

## Operands

| Kind | Examples |
|---|---|
| numbers | `999` &nbsp; `0xCAFE` |
| strings | `"text"` |
| regex | `/pattern/i` |
| types | `int` &nbsp; `java.lang.String` |
| kinds | `static virtual literal local field read write` |

## Instruction patterns

`SEQUENCE [ … ]` (alias `SEQ`) matches an ordered run of instructions appearing *anywhere* in a
method. Steps are adjacent by default; use `..` for a gap.

| Step | Meaning |
|---|---|
| `new` | an opcode mnemonic (case-insensitive) |
| `_` | any single instruction |
| `..` | a gap of any length (zero or more) |
| `( … )` | a full predicate on the instruction, e.g. `(opcode matches /^invoke/)` |

Repetition (suffix any step): `*` 0+ &nbsp; `+` 1+ &nbsp; `{n}` exactly n &nbsp; `{n,m}` n..m &nbsp; `{n,}` n+

**`opcodes` shorthand:** `opcodes` is the method's space-joined mnemonics, so a quick opcode-only
shape is just a regex: `opcodes matches /new dup .* invokespecial/`

## Examples

Find callers of `println`:
```
FIND methods WHERE HAS call WHERE (name == "println")
```

One int argument equal to 999:
```
FIND methods WHERE HAS call WHERE (COUNT(arg) == 1 AND arg(0).value == 999)
```

Public getters:
```
FIND methods WHERE method.name matches /^get/ AND method.modifiers contains public
```

Test classes:
```
FIND classes WHERE class.name endsWith "Test"
```

Crypto calls:
```
FIND methods WHERE HAS call WHERE (owner matches /Cipher/ AND name == "doFinal")
```

Large methods in a package:
```
FIND methods IN class "com/example/.*" WHERE COUNT(insn) > 100
```

Recursive methods with a call inside a loop:
```
FIND methods WHERE recursive AND HAS call WHERE (inLoop)
```

Returns its first parameter:
```
FIND methods WHERE param(0) flowsTo return
```

Forwards a parameter to a call:
```
FIND methods WHERE HAS call WHERE (arg(0) flowsFrom param(0))
```

Allocation pattern (`new .. invokespecial`):
```
FIND methods WHERE SEQUENCE [ new, dup, .., invokespecial ]
```

Opcode shape via regex:
```
FIND methods WHERE opcodes matches /new dup .* invokespecial/
```

Biggest methods first:
```
FIND methods WHERE COUNT(insn) > 50 ORDER BY matches DESC LIMIT 20
```
