package com.tonic.ui.query;

import com.tonic.ui.query.parser.QueryParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards that every documented query example parses as valid composable DSL: the help popup
 * examples (mirroring {@code QueryExplorerPanel.buildExampleBoxes}) and the advanced examples in
 * {@code docs/query-dsl.md}.
 */
class HelpExamplesTest {

    private static final String[] EXAMPLES = {
        "FIND methods WHERE HAS call WHERE (name == \"println\")",
        "FIND methods WHERE HAS call WHERE (COUNT(arg) == 1 AND arg(0).value == 999)",
        "FIND methods WHERE method.name matches /^get/ AND method.modifiers contains public",
        "FIND classes WHERE class.name endsWith \"Test\"",
        "FIND methods WHERE HAS call WHERE (owner matches /Cipher/ AND name == \"doFinal\")",
        "FIND methods IN class \"com/example/.*\" WHERE COUNT(insn) > 100",
        "FIND methods WHERE recursive AND HAS call WHERE (inLoop)",
        "FIND methods WHERE param(0) flowsTo return",
        "FIND methods WHERE HAS call WHERE (arg(0) flowsFrom param(0))",
        "FIND methods WHERE SEQUENCE [ new, dup, .., invokespecial ]",
        "FIND methods WHERE opcodes matches /new dup .* invokespecial/",
        "FIND methods WHERE COUNT(insn) > 50 ORDER BY matches DESC LIMIT 20",
    };

    private static final String[] DOC_ADVANCED_EXAMPLES = {
        "FIND methods WHERE HAS call WHERE (owner == \"java/lang/Runtime\" AND name == \"exec\" AND HAS arg WHERE (kind != literal))",
        "FIND methods WHERE HAS call WHERE (name == \"exec\" AND arg(0) flowsFrom param(0))",
        "FIND methods WHERE HAS insn WHERE (opcode == \"ixor\" AND inLoop) AND HAS insn WHERE (opcode matches /[bcis]astore/ AND inLoop)",
        "FIND methods WHERE HAS insn WHERE (opcode IN [tableswitch, lookupswitch] AND inLoop)",
        "FIND methods WHERE SEQUENCE [ getfield, _{0,4}, putfield ]",
        "FIND methods WHERE SEQUENCE [ (opcode matches /^aload/), (opcode matches /^invoke/){1,3}, putfield ]",
        "FIND methods WHERE method.modifiers contains static AND SEQUENCE [ new, dup, .., invokespecial, .., areturn ]",
        "FIND methods WHERE opcodes matches /^aload.* getfield areturn$/",
        "FIND methods WHERE HAS call WHERE (name == \"exec\") AND NONE call WHERE (name matches /sanitize|validate|check/i)",
        "FIND methods WHERE COUNT(call WHERE (owner == \"java/lang/StringBuilder\" AND name == \"append\" AND inLoop)) >= 3 ORDER BY matches DESC LIMIT 15",
        "FIND methods WHERE recursive AND method.arity == 1 AND param(0) flowsTo return",
    };

    @Test
    void allHelpExamplesParse() throws Exception {
        QueryParser parser = new QueryParser();
        for (String example : EXAMPLES) {
            assertNotNull(parser.parse(example), example);
        }
    }

    @Test
    void allDocAdvancedExamplesParse() throws Exception {
        QueryParser parser = new QueryParser();
        for (String example : DOC_ADVANCED_EXAMPLES) {
            assertNotNull(parser.parse(example), example);
        }
    }
}
