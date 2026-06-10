package com.tonic.ui.query;

import org.fife.ui.rsyntaxtextarea.Token;
import org.junit.jupiter.api.Test;

import javax.swing.text.Segment;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the Query DSL token maker classifies the closed grammar (keywords, operators, literals)
 * while leaving open-vocabulary accessor atoms as plain identifiers.
 */
class QueryTokenMakerTest {

    private Map<String, Integer> tokenize(String src) {
        Segment seg = new Segment(src.toCharArray(), 0, src.length());
        Token t = new QueryTokenMaker().getTokenList(seg, Token.NULL, 0);
        Map<String, Integer> byLexeme = new HashMap<>();
        while (t != null && t.isPaintable()) {
            byLexeme.put(t.getLexeme(), t.getType());
            t = t.getNextToken();
        }
        return byLexeme;
    }

    @Test
    void classifiesClosedGrammar() {
        Map<String, Integer> t = tokenize("FIND methods WHERE arg(0).value == 999 and name matches /^get/i");
        assertEquals(Token.RESERVED_WORD, t.get("FIND"));
        assertEquals(Token.RESERVED_WORD, t.get("WHERE"));
        assertEquals(Token.RESERVED_WORD, t.get("and"));
        assertEquals(Token.FUNCTION, t.get("matches"));
        assertEquals(Token.OPERATOR, t.get("=="));
        assertEquals(Token.LITERAL_NUMBER_DECIMAL_INT, t.get("999"));
        assertEquals(Token.REGEX, t.get("/^get/i"));
        assertEquals(Token.IDENTIFIER, t.get("arg"));
        assertEquals(Token.IDENTIFIER, t.get("value"));
        assertEquals(Token.IDENTIFIER, t.get("name"));
    }

    @Test
    void classifiesSequenceConstruct() {
        Map<String, Integer> t = tokenize("SEQUENCE [ new+, _, (opcode matches /^invoke/){1,2} ]");
        assertEquals(Token.RESERVED_WORD, t.get("SEQUENCE"));
        assertEquals(Token.SEPARATOR, t.get("["));
        assertEquals(Token.SEPARATOR, t.get("]"));
        assertEquals(Token.OPERATOR, t.get("+"));
        assertEquals(Token.SEPARATOR, t.get("{"));
        assertEquals(Token.SEPARATOR, t.get("}"));
        assertEquals(Token.FUNCTION, t.get("matches"));
        assertEquals(Token.RESERVED_WORD_2, t.get("new"));   // opcode mnemonic -> its own color
        assertEquals(Token.IDENTIFIER, t.get("opcode"));     // the accessor atom, not an opcode
        Map<String, Integer> seq = tokenize("SEQ [ dup ]");
        assertEquals(Token.RESERVED_WORD, seq.get("SEQ"));
        assertEquals(Token.RESERVED_WORD_2, seq.get("dup"));
    }

    @Test
    void classifiesTypesStringsAndConstants() {
        Map<String, Integer> t = tokenize("arg(0).type == int and recursive == true and name == \"println\"");
        assertEquals(Token.DATA_TYPE, t.get("int"));
        assertEquals(Token.LITERAL_BOOLEAN, t.get("true"));
        assertEquals(Token.LITERAL_STRING_DOUBLE_QUOTE, t.get("\"println\""));
        assertEquals(Token.IDENTIFIER, t.get("recursive"));
    }
}
