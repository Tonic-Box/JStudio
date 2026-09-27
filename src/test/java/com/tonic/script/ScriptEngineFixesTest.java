package com.tonic.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.analysis.source.ast.stmt.BlockStmt;
import com.tonic.analysis.source.editor.ASTEditor;
import com.tonic.analysis.source.recovery.MethodRecoverer;
import com.tonic.analysis.ssa.SSA;
import com.tonic.builder.ClassBuilder;
import com.tonic.cli.engine.ExecutionResult;
import com.tonic.cli.output.OutputFormat;
import com.tonic.cli.output.OutputHandler;
import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import com.tonic.plugin.result.Finding;
import com.tonic.script.bridge.ASTBridge;
import com.tonic.script.engine.ScriptInterpreter;
import com.tonic.script.engine.ScriptLexer;
import com.tonic.script.engine.ScriptParser;
import com.tonic.script.engine.ScriptValue;
import com.tonic.type.AccessFlags;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("scripting and CLI output behave as documented")
class ScriptEngineFixesTest implements AccessFlags
{

    @TempDir
    Path dir;

    @Test
    @DisplayName("traversal callbacks run inside AST handlers")
    void walkRunsInsideHandlers()
    {
        ClassFile cf = ClassBuilder.create("a/Calls").access(ACC_PUBLIC)
                .addMethod(ACC_PUBLIC | ACC_STATIC, "run", "()I").code().iconst(-3).invokestatic("java/lang/Math", "abs", "(I)I").ireturn().end().end()
                .build();
        MethodEntry method = cf.getMethods().stream().filter(m -> m.getName().equals("run")).findFirst().orElseThrow();
        ScriptInterpreter interpreter = new ScriptInterpreter();
        ASTBridge bridge = new ASTBridge(interpreter);
        interpreter.getGlobalContext().defineConstant("ast", bridge.createAstObject());
        String script = "let visited = 0;\n"
                + "let matched = 0;\n"
                + "ast.onMethodCall((call) => {\n"
                + "    call.walk((n) => { visited = visited + 1; });\n"
                + "    matched = matched + call.findAll((n) => false).length;\n"
                + "});";
        interpreter.execute(new ScriptParser(new ScriptLexer(script).tokenize()).parse());

        BlockStmt body = MethodRecoverer.recoverMethod(new SSA(cf.getConstPool()).lift(method), method);
        bridge.applyTo(new ASTEditor(body, method.getName(), method.getDesc(), cf.getClassName()));

        assertTrue(interpreter.getGlobalContext().get("visited").asNumber() > 0);
        assertEquals(0.0, interpreter.getGlobalContext().get("matched").asNumber());
    }

    @Test
    @DisplayName("tokenizing twice gives the same tokens")
    void tokenizeIsRepeatable()
    {
        ScriptLexer lexer = new ScriptLexer("let x = 1;");

        assertEquals(lexer.tokenize().size(), lexer.tokenize().size());
    }

    @Test
    @DisplayName("script values built from Java lists and maps hold script values")
    void ofConvertsElements()
    {
        ScriptValue array = ScriptValue.of(Arrays.asList(1, "two", null));

        assertEquals("two", array.asArray().get(1).asString());
        assertTrue(array.asArray().get(2).isNull());
    }

    @Test
    @DisplayName("a finding without a location still has one field per CSV column")
    void csvColumnsLineUp() throws Exception
    {
        File out = dir.resolve("out.csv").toFile();
        ExecutionResult result = ExecutionResult.success(1, 1, 1, "ok", List.of(Finding.builder().message("m").category("c").build()));

        OutputHandler.forFormat(OutputFormat.CSV, out).writeResult(result);

        List<String> lines = Files.readAllLines(out.toPath(), StandardCharsets.UTF_8);
        assertEquals(lines.get(0).split(",", -1).length, lines.get(1).split(",", -1).length);
    }
}
