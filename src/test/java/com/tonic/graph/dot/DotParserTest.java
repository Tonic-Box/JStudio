package com.tonic.graph.dot;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DotParserTest {

    private static DotGraph.Node node(DotGraph g, String id) {
        Optional<DotGraph.Node> found = g.getNodes().stream().filter(n -> n.getId().equals(id)).findFirst();
        assertTrue(found.isPresent(), "node " + id + " present");
        return found.get();
    }

    @Test
    void parsesNodesEdgesLabelsAndRankdir() {
        String dot =
                "digraph G {\n"
                        + "  rankdir=LR;\n"
                        + "  // a comment\n"
                        + "  A [label=\"Start\"];\n"
                        + "  B [shape=diamond, color=\"#ff0000\"];\n"
                        + "  A -> B -> C [label=\"go\"];\n"
                        + "}\n";
        DotGraph g = DotParser.parse(dot);

        assertTrue(g.isDirected());
        assertEquals(DotGraph.Rankdir.LR, g.getRankdir());
        assertEquals(3, g.getNodes().size());
        assertEquals("Start", node(g, "A").getLabel());
        assertEquals("diamond", node(g, "B").getShape());
        assertEquals("#ff0000", node(g, "B").getStrokeColor());

        List<DotGraph.Edge> edges = g.getEdges();
        assertEquals(2, edges.size());
        assertEquals("A", edges.get(0).getFrom());
        assertEquals("B", edges.get(0).getTo());
        assertTrue(edges.get(0).isDirected());
        assertEquals("go", edges.get(0).getLabel());
        assertEquals("B", edges.get(1).getFrom());
        assertEquals("C", edges.get(1).getTo());
    }

    @Test
    void honorsNodeDefaultsStyleAndUndirectedEdges() {
        String dot =
                "graph G {\n"
                        + "  node [shape=box, style=\"rounded,dashed\"];\n"
                        + "  X -- Y;\n"
                        + "}\n";
        DotGraph g = DotParser.parse(dot);

        assertFalse(g.isDirected());
        assertTrue(node(g, "X").isRounded());
        assertTrue(node(g, "X").isDashed());
        assertEquals(1, g.getEdges().size());
        assertFalse(g.getEdges().get(0).isDirected());
    }

    @Test
    void escapedNewlinesBecomeLineBreaks() {
        DotGraph g = DotParser.parse("digraph { A [label=\"line1\\nline2\"]; }");
        assertEquals("line1\nline2", node(g, "A").getLabel());
    }

    @Test
    void blockCommentsAndQuotedIdsAreHandled() {
        DotGraph g = DotParser.parse("digraph { /* x */ \"a b\" -> c; }");
        assertNotNull(node(g, "a b"));
        assertNotNull(node(g, "c"));
        assertEquals(1, g.getEdges().size());
    }

    @Test
    void malformedInputThrows() {
        assertThrows(DotParseException.class, () -> DotParser.parse("this is not a graph"));
    }

    @Test
    void emptyInputThrows() {
        assertThrows(DotParseException.class, () -> DotParser.parse("   "));
    }
}
