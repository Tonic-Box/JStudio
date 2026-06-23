package com.tonic.graph.dot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, best-effort parser for the practical subset of Graphviz DOT that an AI assistant emits for diagrams:
 * {@code digraph}/{@code graph}, node and edge statements, edge chains ({@code a -> b -> c}), {@code node}/
 * {@code edge}/{@code graph} attribute defaults, {@code subgraph}/brace blocks (flattened), and the attributes the
 * renderer honors ({@code label}, {@code shape}, {@code color}/{@code fillcolor}, {@code style}, {@code rankdir}).
 * Comments ({@code //}, {@code #}, {@code /* *}{@code /}) and quoted ids/labels are supported. Clusters, ports,
 * ranks, and HTML-label markup are accepted but ignored. Structurally invalid input throws {@link DotParseException}.
 */
public final class DotParser {

    private DotParser() {
    }

    public static DotGraph parse(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw new DotParseException("empty graph");
        }
        List<Tok> tokens = new Lexer(source).lex();
        return new Parser(tokens).parse();
    }

    // ---- tokens -----------------------------------------------------------

    private enum Kind { ID, ARROW_DIRECTED, ARROW_UNDIRECTED, LBRACE, RBRACE, LBRACKET, RBRACKET, EQ, COMMA, SEMI, COLON, EOF }

    private static final class Tok {
        final Kind kind;
        final String text;
        Tok(Kind kind, String text) { this.kind = kind; this.text = text; }
    }

    // ---- lexer ------------------------------------------------------------

    private static final class Lexer {
        private final String s;
        private int i;

        Lexer(String s) { this.s = s; }

        List<Tok> lex() {
            List<Tok> out = new ArrayList<>();
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isWhitespace(c)) { i++; continue; }
                if (c == '/' && peek(1) == '/') { lineComment(); continue; }
                if (c == '#') { lineComment(); continue; }
                if (c == '/' && peek(1) == '*') { blockComment(); continue; }
                switch (c) {
                    case '{': out.add(new Tok(Kind.LBRACE, "{")); i++; continue;
                    case '}': out.add(new Tok(Kind.RBRACE, "}")); i++; continue;
                    case '[': out.add(new Tok(Kind.LBRACKET, "[")); i++; continue;
                    case ']': out.add(new Tok(Kind.RBRACKET, "]")); i++; continue;
                    case '=': out.add(new Tok(Kind.EQ, "=")); i++; continue;
                    case ',': out.add(new Tok(Kind.COMMA, ",")); i++; continue;
                    case ';': out.add(new Tok(Kind.SEMI, ";")); i++; continue;
                    case ':': out.add(new Tok(Kind.COLON, ":")); i++; continue;
                    default: break;
                }
                if (c == '-' && (peek(1) == '>' )) { out.add(new Tok(Kind.ARROW_DIRECTED, "->")); i += 2; continue; }
                if (c == '-' && (peek(1) == '-')) { out.add(new Tok(Kind.ARROW_UNDIRECTED, "--")); i += 2; continue; }
                if (c == '"') { out.add(new Tok(Kind.ID, quoted())); continue; }
                if (c == '<') { out.add(new Tok(Kind.ID, htmlString())); continue; }
                String id = bareId();
                if (id.isEmpty()) { i++; continue; }     // skip an unrecognized char
                out.add(new Tok(Kind.ID, id));
            }
            return out;
        }

        private char peek(int ahead) {
            int j = i + ahead;
            return j < s.length() ? s.charAt(j) : '\0';
        }

        private void lineComment() {
            while (i < s.length() && s.charAt(i) != '\n') i++;
        }

        private void blockComment() {
            i += 2;
            while (i < s.length() && !(s.charAt(i) == '*' && peek(1) == '/')) i++;
            i = Math.min(s.length(), i + 2);
        }

        private String quoted() {
            StringBuilder sb = new StringBuilder();
            i++; // opening quote
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '\\' && i < s.length()) {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': case 'l': case 'r': sb.append('\n'); break;   // DOT line-break escapes
                        case 't': sb.append('\t'); break;
                        default: sb.append(e); break;
                    }
                    continue;
                }
                if (c == '"') break;
                sb.append(c);
            }
            return sb.toString();
        }

        /** Reads an HTML-like {@code <...>} string, stripping tags to plain text (markup is not rendered). */
        private String htmlString() {
            StringBuilder sb = new StringBuilder();
            int depth = 0;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '<') { depth++; continue; }
                if (c == '>') { depth--; if (depth <= 0) break; continue; }
                if (depth == 1) sb.append(c);   // top-level text only
            }
            return sb.toString().replaceAll("<[^>]*>", "").trim();
        }

        private String bareId() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isLetterOrDigit(c) || c == '_' || c == '.' || (c == '-' && i == start)) {
                    i++;
                } else {
                    break;
                }
            }
            return s.substring(start, i);
        }
    }

    // ---- parser -----------------------------------------------------------

    private static final class Parser {
        private static final Tok EOF = new Tok(Kind.EOF, "");

        private final List<Tok> toks;
        private int pos;

        private DotGraph.Rankdir rankdir = DotGraph.Rankdir.TB;
        private final LinkedHashMap<String, DotGraph.Node> nodes = new LinkedHashMap<>();
        private final List<DotGraph.Edge> edges = new ArrayList<>();
        private final Map<String, String> nodeDefaults = new LinkedHashMap<>();
        private final Map<String, String> edgeDefaults = new LinkedHashMap<>();

        Parser(List<Tok> toks) { this.toks = toks; }

        DotGraph parse() {
            if (isId("strict")) pos++;
            boolean directed;
            if (isId("digraph")) { directed = true; pos++; }
            else if (isId("graph")) { directed = false; pos++; }
            else throw new DotParseException("expected 'graph' or 'digraph'");

            if (peek().kind == Kind.ID) pos++;   // optional graph name
            expect(Kind.LBRACE, "{");
            parseStatements();
            return new DotGraph(directed, rankdir, new ArrayList<>(nodes.values()), edges);
        }

        private void parseStatements() {
            while (peek().kind != Kind.EOF) {
                Tok t = peek();
                if (t.kind == Kind.RBRACE) { pos++; return; }
                if (t.kind == Kind.SEMI || t.kind == Kind.COMMA) { pos++; continue; }
                if (t.kind == Kind.LBRACE) { pos++; parseStatements(); continue; }   // anonymous block
                if (t.kind != Kind.ID) { pos++; continue; }

                String id = t.text;
                if (id.equalsIgnoreCase("subgraph")) {
                    pos++;
                    if (peek().kind == Kind.ID) pos++;   // optional subgraph name
                    if (peek().kind == Kind.LBRACE) { pos++; parseStatements(); }
                    continue;
                }
                if (id.equalsIgnoreCase("node")) { pos++; mergeInto(nodeDefaults, parseAttrs()); continue; }
                if (id.equalsIgnoreCase("edge")) { pos++; mergeInto(edgeDefaults, parseAttrs()); continue; }
                if (id.equalsIgnoreCase("graph")) { pos++; applyGraphAttrs(parseAttrs()); continue; }

                parseNodeOrEdge();
            }
            throw new DotParseException("unterminated graph (missing '}')");
        }

        private void parseNodeOrEdge() {
            String first = consumeNodeId();
            Tok next = peek();
            if (next.kind == Kind.ARROW_DIRECTED || next.kind == Kind.ARROW_UNDIRECTED) {
                List<String> chain = new ArrayList<>();
                chain.add(first);
                List<Boolean> arrowDirected = new ArrayList<>();
                Tok arrow = peek();
                while (arrow.kind == Kind.ARROW_DIRECTED || arrow.kind == Kind.ARROW_UNDIRECTED) {
                    arrowDirected.add(arrow.kind == Kind.ARROW_DIRECTED);
                    pos++;
                    chain.add(consumeNodeId());
                    arrow = peek();
                }
                Map<String, String> attrs = parseAttrs();
                for (String id : chain) ensureNode(id);
                for (int k = 0; k < chain.size() - 1; k++) {
                    DotGraph.Edge edge = new DotGraph.Edge(chain.get(k), chain.get(k + 1), arrowDirected.get(k));
                    Map<String, String> merged = new LinkedHashMap<>(edgeDefaults);
                    merged.putAll(attrs);
                    applyEdgeAttrs(edge, merged);
                    edges.add(edge);
                }
                return;
            }
            if (next.kind == Kind.EQ) {     // statement-level graph attribute: id = value
                pos++;
                String value = peek().kind == Kind.ID ? toks.get(pos++).text : "";
                applyGraphAttr(first, value);
                return;
            }
            DotGraph.Node node = ensureNode(first);
            applyNodeAttrs(node, parseAttrs());
        }

        /** A node id, skipping an optional {@code :port[:compass]} suffix (ports are not modeled). */
        private String consumeNodeId() {
            Tok t = peek();
            if (t.kind != Kind.ID) throw new DotParseException("expected a node id");
            pos++;
            while (peek().kind == Kind.COLON) {
                pos++;
                if (peek().kind == Kind.ID) pos++;
            }
            return t.text;
        }

        private Map<String, String> parseAttrs() {
            Map<String, String> attrs = new LinkedHashMap<>();
            while (peek().kind == Kind.LBRACKET) {
                pos++;
                while (peek().kind != Kind.RBRACKET && peek().kind != Kind.EOF) {
                    Tok t = peek();
                    if (t.kind != Kind.ID) { pos++; continue; }
                    String key = t.text.toLowerCase();
                    pos++;
                    String value = "";
                    if (peek().kind == Kind.EQ) {
                        pos++;
                        if (peek().kind == Kind.ID) value = toks.get(pos++).text;
                    }
                    attrs.put(key, value);
                }
                if (peek().kind == Kind.RBRACKET) pos++;
            }
            return attrs;
        }

        private DotGraph.Node ensureNode(String id) {
            DotGraph.Node existing = nodes.get(id);
            if (existing != null) return existing;
            DotGraph.Node node = new DotGraph.Node(id);
            applyNodeAttrs(node, nodeDefaults);
            nodes.put(id, node);
            return node;
        }

        private void applyNodeAttrs(DotGraph.Node node, Map<String, String> attrs) {
            if (attrs.containsKey("label")) node.setLabel(attrs.get("label"));
            if (attrs.containsKey("shape")) node.setShape(attrs.get("shape").toLowerCase());
            if (attrs.containsKey("fillcolor")) node.setFillColor(attrs.get("fillcolor"));
            if (attrs.containsKey("color")) node.setStrokeColor(attrs.get("color"));
            String style = attrs.get("style");
            if (style != null) {
                String lower = style.toLowerCase();
                if (lower.contains("dashed")) node.setDashed(true);
                if (lower.contains("rounded")) node.setRounded(true);
                if (lower.contains("filled") && node.getFillColor() == null && node.getStrokeColor() != null) {
                    node.setFillColor(node.getStrokeColor());
                }
            }
        }

        private void applyEdgeAttrs(DotGraph.Edge edge, Map<String, String> attrs) {
            if (attrs.containsKey("label")) edge.setLabel(attrs.get("label"));
            String style = attrs.get("style");
            if (style != null && style.toLowerCase().contains("dashed")) edge.setDashed(true);
        }

        private void applyGraphAttrs(Map<String, String> attrs) {
            for (Map.Entry<String, String> e : attrs.entrySet()) applyGraphAttr(e.getKey(), e.getValue());
        }

        private void applyGraphAttr(String key, String value) {
            if (key.equalsIgnoreCase("rankdir")) {
                try {
                    rankdir = DotGraph.Rankdir.valueOf(value.trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    // leave default
                }
            }
        }

        private static void mergeInto(Map<String, String> target, Map<String, String> add) {
            target.putAll(add);
        }

        private boolean isId(String word) {
            Tok t = peek();
            return t.kind == Kind.ID && t.text.equalsIgnoreCase(word);
        }

        private Tok peek() {
            return pos < toks.size() ? toks.get(pos) : EOF;
        }

        private void expect(Kind kind, String what) {
            if (peek().kind != kind) throw new DotParseException("expected '" + what + "'");
            pos++;
        }
    }
}
