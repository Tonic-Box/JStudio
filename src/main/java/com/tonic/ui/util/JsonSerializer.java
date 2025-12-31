package com.tonic.ui.util;

import com.tonic.ui.model.Bookmark;
import com.tonic.ui.model.BookmarkStore;
import com.tonic.ui.model.Comment;
import com.tonic.ui.model.CommentStore;
import com.tonic.ui.model.ProjectDatabase;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonSerializer {

    public static void save(ProjectDatabase db, File file) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write(toJson(db));
        }
    }

    public static ProjectDatabase load(File file) throws IOException {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }
        return fromJson(content.toString());
    }

    private static String toJson(ProjectDatabase db) {
        String sb = "{\n" +
                "  \"version\": " + quote(db.getVersion()) + ",\n" +
                "  \"target\": {\n" +
                "    \"path\": " + quote(db.getTargetPath()) + ",\n" +
                "    \"hash\": " + quote(db.getTargetHash()) + "\n" +
                "  },\n" +
                "  \"created\": " + db.getCreated() + ",\n" +
                "  \"modified\": " + db.getModified() + ",\n" +
                "  \"comments\": " + commentsToJson(db.getComments()) + ",\n" +
                "  \"bookmarks\": " + bookmarksToJson(db.getBookmarks()) + ",\n" +
                "  \"quickSlots\": " + quickSlotsToJson(db.getBookmarks()) + ",\n" +
                "  \"renames\": " + mapToJson(db.getRenames()) + "\n" +
                "}";
        return sb;
    }

    private static String commentsToJson(CommentStore store) {
        List<Comment> comments = store.getAllComments();
        if (comments.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < comments.size(); i++) {
            Comment c = comments.get(i);
            sb.append("    {\n");
            sb.append("      \"id\": ").append(quote(c.getId())).append(",\n");
            sb.append("      \"class\": ").append(quote(c.getClassName())).append(",\n");
            sb.append("      \"member\": ").append(quote(c.getMemberName())).append(",\n");
            sb.append("      \"line\": ").append(c.getLineNumber()).append(",\n");
            sb.append("      \"text\": ").append(quote(c.getText())).append(",\n");
            sb.append("      \"type\": ").append(quote(c.getType().name())).append(",\n");
            sb.append("      \"timestamp\": ").append(c.getTimestamp()).append("\n");
            sb.append("    }");
            if (i < comments.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ]");
        return sb.toString();
    }

    private static String bookmarksToJson(BookmarkStore store) {
        List<Bookmark> bookmarks = store.getAll();
        if (bookmarks.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < bookmarks.size(); i++) {
            Bookmark b = bookmarks.get(i);
            sb.append("    {\n");
            sb.append("      \"id\": ").append(quote(b.getId())).append(",\n");
            sb.append("      \"name\": ").append(quote(b.getName())).append(",\n");
            sb.append("      \"class\": ").append(quote(b.getClassName())).append(",\n");
            sb.append("      \"member\": ").append(quote(b.getMemberName())).append(",\n");
            sb.append("      \"line\": ").append(b.getLineNumber()).append(",\n");
            sb.append("      \"slot\": ").append(b.getSlot()).append(",\n");
            sb.append("      \"notes\": ").append(quote(b.getNotes())).append(",\n");
            sb.append("      \"timestamp\": ").append(b.getTimestamp()).append("\n");
            sb.append("    }");
            if (i < bookmarks.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ]");
        return sb.toString();
    }

    private static String quickSlotsToJson(BookmarkStore store) {
        Map<Integer, String> slots = store.getQuickSlotIds();
        if (slots.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<Integer, String> entry : slots.entrySet()) {
            if (!first) sb.append(", ");
            sb.append("\"").append(entry.getKey()).append("\": ").append(quote(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String mapToJson(Map<String, String> map) {
        if (map.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        boolean first = true;
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!first) sb.append(",\n");
            sb.append("    ").append(quote(entry.getKey())).append(": ").append(quote(entry.getValue()));
            first = false;
        }
        sb.append("\n  }");
        return sb.toString();
    }

    private static String quote(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default: sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static ProjectDatabase fromJson(String json) {
        ProjectDatabase db = new ProjectDatabase();
        JsonParser parser = new JsonParser(json);
        Map<String, Object> root = parser.parseObject();

        if (root.containsKey("version")) {
            db.setVersion((String) root.get("version"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) root.get("target");
        if (target != null) {
            db.setTargetPath((String) target.get("path"));
            db.setTargetHash((String) target.get("hash"));
        }

        if (root.containsKey("created")) {
            db.setCreated(((Number) root.get("created")).longValue());
        }
        if (root.containsKey("modified")) {
            db.setModified(((Number) root.get("modified")).longValue());
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> commentsList = (List<Map<String, Object>>) root.get("comments");
        if (commentsList != null) {
            List<Comment> comments = new ArrayList<>();
            for (Map<String, Object> cm : commentsList) {
                Comment c = new Comment();
                c.setId((String) cm.get("id"));
                c.setClassName((String) cm.get("class"));
                c.setMemberName((String) cm.get("member"));
                c.setLineNumber(((Number) cm.get("line")).intValue());
                c.setText((String) cm.get("text"));
                String typeStr = (String) cm.get("type");
                if (typeStr != null) {
                    c.setType(Comment.Type.valueOf(typeStr));
                }
                if (cm.containsKey("timestamp")) {
                    c.setTimestamp(((Number) cm.get("timestamp")).longValue());
                }
                comments.add(c);
            }
            db.getComments().setComments(comments);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bookmarksList = (List<Map<String, Object>>) root.get("bookmarks");
        if (bookmarksList != null) {
            List<Bookmark> bookmarks = new ArrayList<>();
            for (Map<String, Object> bm : bookmarksList) {
                Bookmark b = new Bookmark();
                b.setId((String) bm.get("id"));
                b.setName((String) bm.get("name"));
                b.setClassName((String) bm.get("class"));
                b.setMemberName((String) bm.get("member"));
                b.setLineNumber(((Number) bm.get("line")).intValue());
                b.setSlot(((Number) bm.get("slot")).intValue());
                b.setNotes((String) bm.get("notes"));
                if (bm.containsKey("timestamp")) {
                    b.setTimestamp(((Number) bm.get("timestamp")).longValue());
                }
                bookmarks.add(b);
            }
            db.getBookmarks().setBookmarks(bookmarks);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> quickSlots = (Map<String, Object>) root.get("quickSlots");
        if (quickSlots != null) {
            Map<Integer, String> slotMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : quickSlots.entrySet()) {
                try {
                    int slot = Integer.parseInt(entry.getKey());
                    slotMap.put(slot, (String) entry.getValue());
                } catch (NumberFormatException e) {
                    // skip invalid slots
                }
            }
            db.getBookmarks().restoreQuickSlots(slotMap);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> renames = (Map<String, Object>) root.get("renames");
        if (renames != null) {
            Map<String, String> renameMap = new HashMap<>();
            for (Map.Entry<String, Object> entry : renames.entrySet()) {
                renameMap.put(entry.getKey(), (String) entry.getValue());
            }
            db.setRenames(renameMap);
        }

        return db;
    }

    private static class JsonParser {
        private final String json;
        private int pos = 0;

        JsonParser(String json) {
            this.json = json;
        }

        Map<String, Object> parseObject() {
            Map<String, Object> result = new HashMap<>();
            skipWhitespace();
            if (peek() != '{') {
                throw new RuntimeException("Expected '{' at position " + pos);
            }
            pos++;
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (peek() != ':') {
                    throw new RuntimeException("Expected ':' at position " + pos);
                }
                pos++;
                skipWhitespace();
                Object value = parseValue();
                result.put(key, value);
                skipWhitespace();
                if (peek() == '}') {
                    pos++;
                    return result;
                }
                if (peek() != ',') {
                    throw new RuntimeException("Expected ',' or '}' at position " + pos);
                }
                pos++;
            }
        }

        List<Object> parseArray() {
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek() != '[') {
                throw new RuntimeException("Expected '[' at position " + pos);
            }
            pos++;
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                result.add(parseValue());
                skipWhitespace();
                if (peek() == ']') {
                    pos++;
                    return result;
                }
                if (peek() != ',') {
                    throw new RuntimeException("Expected ',' or ']' at position " + pos);
                }
                pos++;
            }
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            if (c == '"') {
                return parseString();
            } else if (c == '{') {
                return parseObject();
            } else if (c == '[') {
                return parseArray();
            } else if (c == 't' || c == 'f') {
                return parseBoolean();
            } else if (c == 'n') {
                return parseNull();
            } else if (c == '-' || Character.isDigit(c)) {
                return parseNumber();
            }
            throw new RuntimeException("Unexpected character '" + c + "' at position " + pos);
        }

        String parseString() {
            if (peek() != '"') {
                throw new RuntimeException("Expected '\"' at position " + pos);
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < json.length()) {
                char c = json.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                } else if (c == '\\') {
                    if (pos >= json.length()) {
                        throw new RuntimeException("Unexpected end of string");
                    }
                    char escape = json.charAt(pos++);
                    switch (escape) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            if (pos + 4 > json.length()) {
                                throw new RuntimeException("Invalid unicode escape");
                            }
                            String hex = json.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        default:
                            sb.append(escape);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new RuntimeException("Unterminated string");
        }

        Number parseNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
            boolean isFloat = false;
            if (pos < json.length() && json.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
            }
            if (pos < json.length() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < json.length() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) pos++;
                while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
            }
            String numStr = json.substring(start, pos);
            if (isFloat) {
                return Double.parseDouble(numStr);
            } else {
                long val = Long.parseLong(numStr);
                if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                    return (int) val;
                }
                return val;
            }
        }

        Boolean parseBoolean() {
            if (json.startsWith("true", pos)) {
                pos += 4;
                return true;
            } else if (json.startsWith("false", pos)) {
                pos += 5;
                return false;
            }
            throw new RuntimeException("Expected boolean at position " + pos);
        }

        Object parseNull() {
            if (json.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new RuntimeException("Expected null at position " + pos);
        }

        void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
                pos++;
            }
        }

        char peek() {
            if (pos >= json.length()) {
                return '\0';
            }
            return json.charAt(pos);
        }
    }
}
