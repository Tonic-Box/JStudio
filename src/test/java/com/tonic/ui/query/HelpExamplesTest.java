package com.tonic.ui.query;

import com.tonic.ui.query.parser.QueryParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards that every example shown in the Query DSL help popup parses as valid composable DSL.
 * Mirrors the strings in {@code QueryExplorerPanel.buildExampleBoxes}.
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

    @Test
    void allHelpExamplesParse() throws Exception {
        QueryParser parser = new QueryParser();
        for (String example : EXAMPLES) {
            assertNotNull(parser.parse(example), example);
        }
    }
}
