package com.tonic.script.engine;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.editor.ASTEditor;
import com.tonic.analysis.source.editor.Replacement;
import com.tonic.analysis.source.lower.ASTLowerer;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.analysis.ssa.cfg.IRMethod;
import com.tonic.live.LiveSession;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.parser.MethodEntry;
import com.tonic.script.bridge.ASTBridge;
import com.tonic.script.bridge.AnnotationBridge;
import com.tonic.script.bridge.BridgeRegistry;
import com.tonic.script.bridge.CommonAPI;
import com.tonic.script.bridge.IRBridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Runs a Script-Editor script (the JS-like DSL) against a target scope, applying its AST/IR/annotation transforms
 * and returning the number of modifications. All console output is routed to a caller-supplied {@code out} sink
 * (each call receives one line, terminated by {@code \n}), so this is UI-free: the Script Editor passes a sink that
 * appends to its console on the EDT, while the AI assistant passes a sink that buffers + streams to the bottom
 * Script Console tab. Synchronous and blocking.
 *
 * <p>Extracted from {@code ScriptEditorPanel} so the editor and the assistant share one implementation of the
 * bytecode-mutation logic.
 */
public final class ScriptRunner {

    private ScriptRunner() {
    }

    /** Which targets a run touches. */
    public enum Scope {
        ALL, CLASS, METHOD
    }

    /**
     * Runs {@code source} over the given scope. {@code targetClass}/{@code targetMethod} are required for
     * {@link Scope#CLASS}/{@link Scope#METHOD} respectively. {@code live} may be null (no attached JVM). Returns
     * the total modification count.
     */
    public static int run(String source, Script.Mode mode, ProjectModel project, LiveSession live,
                          Scope scope, ClassEntryModel targetClass, MethodEntryModel targetMethod,
                          Consumer<String> out) {
        int count = 0;
        try {
            if (scope == Scope.ALL) {
                if (project == null) {
                    out.accept("ERROR: No project loaded\n");
                    return 0;
                }
                for (ClassEntryModel classEntry : project.getAllClasses()) {
                    int before = count;
                    count += runAnnotationsOnClass(source, classEntry, project, live, out);
                    for (MethodEntryModel methodModel : classEntry.getMethods()) {
                        count += runOnMethod(source, mode, classEntry, methodModel, project, live, out);
                    }
                    if (count > before) {
                        commitClass(classEntry, out);
                    }
                }
            } else if (scope == Scope.CLASS) {
                if (targetClass == null) {
                    out.accept("ERROR: No class selected\n");
                    return 0;
                }
                int before = count;
                count += runAnnotationsOnClass(source, targetClass, project, live, out);
                for (MethodEntryModel methodModel : targetClass.getMethods()) {
                    count += runOnMethod(source, mode, targetClass, methodModel, project, live, out);
                }
                if (count > before) {
                    commitClass(targetClass, out);
                }
            } else {
                if (targetClass == null || targetMethod == null) {
                    out.accept("ERROR: No method selected\n");
                    return 0;
                }
                int before = count;
                count += runAnnotationsOnClass(source, targetClass, project, live, out);
                count += runOnMethod(source, mode, targetClass, targetMethod, project, live, out);
                if (count > before) {
                    commitClass(targetClass, out);
                }
            }
        } catch (Exception e) {
            out.accept("ERROR: " + e.getMessage() + "\n");
        }
        return count;
    }

    private static int runOnMethod(String code, Script.Mode mode, ClassEntryModel classEntry,
                                   MethodEntryModel methodModel, ProjectModel project, LiveSession live,
                                   Consumer<String> out) {
        MethodEntry method = methodModel.getMethodEntry();
        if (method.getCodeAttribute() == null) return 0;
        if (method.getName().startsWith("<")) return 0;

        int count = 0;

        ScriptInterpreter interpreter = new ScriptInterpreter();

        CommonAPI commonAPI = new CommonAPI();
        commonAPI.setContext(classEntry.getClassName(), method.getName(), method.getDesc());
        commonAPI.setCallbacks(
            msg -> out.accept(msg + "\n"),
            msg -> out.accept("WARN: " + msg + "\n"),
            msg -> out.accept("ERROR: " + msg + "\n")
        );
        commonAPI.registerIn(interpreter);

        if (project != null) {
            BridgeRegistry registry = new BridgeRegistry(interpreter, project);
            registry.setLogCallback(msg -> out.accept(msg + "\n"));
            registry.registerAll();
            registry.registerLiveBridge(live);
        }

        ScriptLexer lexer = new ScriptLexer(code);
        List<ScriptToken> tokens = lexer.tokenize();
        if (!lexer.getErrors().isEmpty()) {
            for (String err : lexer.getErrors()) {
                out.accept("Lexer error: " + err + "\n");
            }
            return 0;
        }

        ScriptParser parser = new ScriptParser(tokens);
        List<ScriptAST> statements = parser.parse();
        if (!parser.getErrors().isEmpty()) {
            for (String err : parser.getErrors()) {
                out.accept("Parser error: " + err + "\n");
            }
            return 0;
        }

        if (mode == Script.Mode.AST || mode == Script.Mode.BOTH) {
            count += runASTMode(interpreter, statements, classEntry, method, project, out);
        }
        if (mode == Script.Mode.IR || mode == Script.Mode.BOTH) {
            count += runIRMode(interpreter, statements, method, methodModel, classEntry, out);
        }

        return count;
    }

    private static int runASTMode(ScriptInterpreter interpreter, List<ScriptAST> statements,
                                  ClassEntryModel classEntry, MethodEntry method, ProjectModel project,
                                  Consumer<String> out) {
        try {
            SSA ssa = new SSA(classEntry.getClassFile().getConstPool());
            IRMethod irMethod = ssa.lift(method);
            if (irMethod == null || irMethod.getEntryBlock() == null) return 0;

            BlockStmt methodBody = MethodRecoverer.recoverMethod(irMethod, method);
            if (methodBody == null) return 0;

            ASTBridge astBridge = new ASTBridge(interpreter);
            astBridge.setLogCallback(msg -> out.accept(msg + "\n"));

            interpreter.getGlobalContext().defineConstant("ast", astBridge.createAstObject());
            defineInertBridges(interpreter, false, true, true);

            interpreter.execute(statements);

            ASTEditor editor = new ASTEditor(methodBody, method.getName(), method.getDesc(),
                classEntry.getClassName());
            int count = astBridge.applyTo(editor);
            if (count == 0) return 0;

            // Cement the edited AST back to bytecode: lower AST -> IR -> bytecode into the method.
            // YABR's decompiler cannot round-trip every method losslessly (complex control flow, lambdas, ...),
            // so we VERIFY: lower, then re-decompile and confirm the method's call set is exactly what we intended.
            // If calls were lost/changed beyond the edit, restore the original bytecode (the IR lift->lower round
            // trip is faithful) and skip the method rather than cement a corrupt one.
            List<String> intended = callSignatures(methodBody, method, classEntry.getClassName());
            IRMethod pristine = new SSA(classEntry.getClassFile().getConstPool()).lift(method);

            ASTLowerer lowerer = new ASTLowerer(classEntry.getClassFile().getConstPool(),
                    project != null ? project.getClassPool() : null);
            lowerer.replaceBody(methodBody, irMethod);
            ssa.lower(irMethod, method);

            BlockStmt check = MethodRecoverer.recoverMethod(
                    new SSA(classEntry.getClassFile().getConstPool()).lift(method), method);
            List<String> actual = check == null ? null : callSignatures(check, method, classEntry.getClassName());
            if (actual == null || !actual.equals(intended)) {
                new SSA(classEntry.getClassFile().getConstPool()).lower(pristine, method);
                out.accept("Skipped " + classEntry.getClassName() + "." + method.getName()
                        + ": the decompiler could not round-trip this method losslessly, so it was left "
                        + "unchanged (no partial/corrupt edit).\n");
                return 0;
            }
            return count;

        } catch (Exception e) {
            out.accept("AST mode error: " + e.getMessage() + "\n");
            return 0;
        }
    }

    private static int runIRMode(ScriptInterpreter interpreter, List<ScriptAST> statements,
                                 MethodEntry method, MethodEntryModel methodModel, ClassEntryModel classEntry,
                                 Consumer<String> out) {
        try {
            SSA ssa = new SSA(classEntry.getClassFile().getConstPool());
            IRMethod irMethod = ssa.lift(method);
            if (irMethod == null || irMethod.getEntryBlock() == null) return 0;

            IRBridge irBridge = new IRBridge(interpreter);
            irBridge.setLogCallback(msg -> out.accept(msg + "\n"));

            interpreter.getGlobalContext().defineConstant("ir", irBridge.createIRObject());
            defineInertBridges(interpreter, true, false, true);

            interpreter.execute(statements);

            int count = irBridge.applyTo(irMethod);

            // Cement the edited IR back to bytecode into the method.
            if (count > 0) {
                ssa.lower(irMethod, method);
            }

            methodModel.setIrCache(null);

            return count;

        } catch (Exception e) {
            out.accept("IR mode error: " + e.getMessage() + "\n");
            return 0;
        }
    }

    private static int runAnnotationsOnClass(String code, ClassEntryModel classEntry, ProjectModel project,
                                             LiveSession live, Consumer<String> out) {
        try {
            ScriptInterpreter interpreter = new ScriptInterpreter();

            CommonAPI commonAPI = new CommonAPI();
            commonAPI.setContext(classEntry.getClassName(), "", "");
            commonAPI.setCallbacks(
                msg -> out.accept(msg + "\n"),
                msg -> out.accept("WARN: " + msg + "\n"),
                msg -> out.accept("ERROR: " + msg + "\n")
            );
            commonAPI.registerIn(interpreter);

            if (project != null) {
                BridgeRegistry registry = new BridgeRegistry(interpreter, project);
                registry.setLogCallback(msg -> out.accept(msg + "\n"));
                registry.registerAll();
                registry.registerLiveBridge(live);
            }

            AnnotationBridge annotationBridge = new AnnotationBridge(interpreter);
            annotationBridge.setLogCallback(msg -> out.accept(msg + "\n"));

            interpreter.getGlobalContext().defineConstant("annotations", annotationBridge.createAnnotationObject());
            defineInertBridges(interpreter, true, true, false);

            ScriptLexer lexer = new ScriptLexer(code);
            List<ScriptToken> tokens = lexer.tokenize();
            if (!lexer.getErrors().isEmpty()) {
                return 0;
            }

            ScriptParser parser = new ScriptParser(tokens);
            List<ScriptAST> statements = parser.parse();
            if (!parser.getErrors().isEmpty()) {
                return 0;
            }

            interpreter.execute(statements);

            if (annotationBridge.hasHandlers()) {
                return annotationBridge.applyToClass(classEntry);
            }

            return 0;

        } catch (Exception e) {
            out.accept("Annotation processing error: " + e.getMessage() + "\n");
            return 0;
        }
    }

    /** The sorted multiset of {@code owner#name} method-call signatures in a recovered body (round-trip check). */
    private static List<String> callSignatures(BlockStmt body, MethodEntry method, String className) {
        List<String> sigs = new ArrayList<>();
        ASTEditor editor = new ASTEditor(body, method.getName(), method.getDesc(), className);
        editor.onMethodCall((ctx, call) -> {
            sigs.add(call.getOwnerClass() + "#" + call.getMethodName());
            return Replacement.keep();
        });
        editor.apply();
        Collections.sort(sigs);
        return sigs;
    }

    /**
     * Defines fresh, inert {@code ast}/{@code ir}/{@code annotations} bindings so the whole script executes in any
     * pass without hitting an undefined bridge ("Cannot call non-function"). Each pass keeps its own real bridge
     * for the handlers it actually applies; these stand in for the others (their handlers are simply never applied).
     */
    private static void defineInertBridges(ScriptInterpreter interp, boolean ast, boolean ir, boolean annotations) {
        if (ast) {
            interp.getGlobalContext().defineConstant("ast", new ASTBridge(interp).createAstObject());
        }
        if (ir) {
            interp.getGlobalContext().defineConstant("ir", new IRBridge(interp).createIRObject());
        }
        if (annotations) {
            interp.getGlobalContext().defineConstant("annotations",
                    new AnnotationBridge(interp).createAnnotationObject());
        }
    }

    /**
     * After a class was modified: recompute its stack-map frames, then invalidate JStudio's caches (decompiled
     * source + per-method IR) so every view regenerates from the new bytecode.
     */
    private static void commitClass(ClassEntryModel classEntry, Consumer<String> out) {
        try {
            classEntry.getClassFile().computeFrames();
        } catch (Exception e) {
            out.accept("Frame computation failed for " + classEntry.getClassName() + ": " + e.getMessage() + "\n");
        }
        classEntry.invalidateDecompilationCache();
        for (MethodEntryModel methodModel : classEntry.getMethods()) {
            methodModel.invalidateIRCache();
        }
    }
}
