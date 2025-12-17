package com.tonic.ui.script.bridge;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.script.engine.ScriptFunction;
import com.tonic.ui.script.engine.ScriptInterpreter;
import com.tonic.ui.script.engine.ScriptValue;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Bridge for string analysis.
 * Exposes a 'strings' global object for finding and analyzing string constants.
 */
public class StringBridge {

    private final ScriptInterpreter interpreter;
    private final ProjectModel projectModel;
    private Consumer<String> logCallback;
    private List<StringEntry> cachedStrings;

    public StringBridge(ScriptInterpreter interpreter, ProjectModel projectModel) {
        this.interpreter = interpreter;
        this.projectModel = projectModel;
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    private void log(String message) {
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    public ScriptValue createStringsObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("extract", ScriptValue.function(
            ScriptFunction.native0("extract", this::extractAll)
        ));

        props.put("getAll", ScriptValue.function(
            ScriptFunction.native0("getAll", this::getAll)
        ));

        props.put("find", ScriptValue.function(
            ScriptFunction.native1("find", this::findStrings)
        ));

        props.put("findRegex", ScriptValue.function(
            ScriptFunction.native1("findRegex", this::findByRegex)
        ));

        props.put("count", ScriptValue.function(
            ScriptFunction.native0("count", () ->
                ScriptValue.number(cachedStrings != null ? cachedStrings.size() : 0))
        ));

        props.put("inClass", ScriptValue.function(
            ScriptFunction.native1("inClass", this::stringsInClass)
        ));

        props.put("findUrls", ScriptValue.function(
            ScriptFunction.native0("findUrls", this::findUrls)
        ));

        props.put("findPaths", ScriptValue.function(
            ScriptFunction.native0("findPaths", this::findPaths)
        ));

        props.put("findSql", ScriptValue.function(
            ScriptFunction.native0("findSql", this::findSql)
        ));

        props.put("findSecrets", ScriptValue.function(
            ScriptFunction.native0("findSecrets", this::findSecrets)
        ));

        props.put("groupByClass", ScriptValue.function(
            ScriptFunction.native0("groupByClass", this::groupByClass)
        ));

        props.put("unique", ScriptValue.function(
            ScriptFunction.native0("unique", this::uniqueStrings)
        ));

        return ScriptValue.object(props);
    }

    private void ensureExtracted() {
        if (cachedStrings == null) {
            extractAll();
        }
    }

    private ScriptValue extractAll() {
        cachedStrings = new ArrayList<>();

        if (projectModel.getClassPool() == null) {
            log("No class pool available");
            return ScriptValue.number(0);
        }

        for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
            extractFromClass(classEntry);
        }

        log("Extracted " + cachedStrings.size() + " strings from " +
            projectModel.getClassCount() + " classes");
        return ScriptValue.number(cachedStrings.size());
    }

    private void extractFromClass(ClassEntryModel classEntry) {
        ClassFile cf = classEntry.getClassFile();
        ConstPool constPool = cf.getConstPool();
        List<Item<?>> items = constPool.getItems();

        for (int i = 1; i < items.size(); i++) {
            try {
                Item<?> item = items.get(i);
                if (item instanceof StringRefItem) {
                    StringRefItem stringRef = (StringRefItem) item;
                    int utf8Index = stringRef.getValue();
                    Item<?> utf8Item = items.get(utf8Index);
                    if (utf8Item instanceof Utf8Item) {
                        String str = ((Utf8Item) utf8Item).getValue();
                        if (str != null && !str.isEmpty()) {
                            cachedStrings.add(new StringEntry(str, classEntry.getClassName()));
                        }
                    }
                }
            } catch (Exception e) {
                // Skip invalid entries
            }
        }
    }

    private ScriptValue getAll() {
        ensureExtracted();
        return wrapEntries(cachedStrings);
    }

    private ScriptValue findStrings(ScriptValue patternVal) {
        ensureExtracted();
        String search = patternVal.asString().toLowerCase();

        List<StringEntry> matches = new ArrayList<>();
        for (StringEntry entry : cachedStrings) {
            if (entry.value.toLowerCase().contains(search)) {
                matches.add(entry);
            }
        }

        log("Found " + matches.size() + " strings containing: " + search);
        return wrapEntries(matches);
    }

    private ScriptValue findByRegex(ScriptValue regexVal) {
        ensureExtracted();

        try {
            Pattern pattern = Pattern.compile(regexVal.asString());
            List<StringEntry> matches = new ArrayList<>();

            for (StringEntry entry : cachedStrings) {
                if (pattern.matcher(entry.value).find()) {
                    matches.add(entry);
                }
            }

            log("Found " + matches.size() + " strings matching regex");
            return wrapEntries(matches);
        } catch (Exception e) {
            log("Invalid regex: " + e.getMessage());
            return ScriptValue.array(new ArrayList<>());
        }
    }

    private ScriptValue stringsInClass(ScriptValue classRef) {
        ensureExtracted();
        String className = classRef.asString().replace('.', '/');

        List<StringEntry> matches = new ArrayList<>();
        for (StringEntry entry : cachedStrings) {
            if (entry.className.equals(className) || entry.className.endsWith("/" + className)) {
                matches.add(entry);
            }
        }

        return wrapEntries(matches);
    }

    private ScriptValue findUrls() {
        ensureExtracted();
        Pattern urlPattern = Pattern.compile(
            "(https?://|ftp://|file://)[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
            Pattern.CASE_INSENSITIVE
        );

        List<StringEntry> matches = new ArrayList<>();
        for (StringEntry entry : cachedStrings) {
            if (urlPattern.matcher(entry.value).find()) {
                matches.add(entry);
            }
        }

        log("Found " + matches.size() + " URL strings");
        return wrapEntries(matches);
    }

    private ScriptValue findPaths() {
        ensureExtracted();
        Pattern pathPattern = Pattern.compile(
            "([A-Za-z]:\\\\[^\"<>|\\n]+)|(/[\\w./\\-]+)",
            Pattern.CASE_INSENSITIVE
        );

        List<StringEntry> matches = new ArrayList<>();
        for (StringEntry entry : cachedStrings) {
            if (pathPattern.matcher(entry.value).find() && entry.value.length() > 3) {
                matches.add(entry);
            }
        }

        log("Found " + matches.size() + " path strings");
        return wrapEntries(matches);
    }

    private ScriptValue findSql() {
        ensureExtracted();
        Pattern sqlPattern = Pattern.compile(
            "\\b(SELECT|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|FROM|WHERE|JOIN|TABLE)\\b",
            Pattern.CASE_INSENSITIVE
        );

        List<StringEntry> matches = new ArrayList<>();
        for (StringEntry entry : cachedStrings) {
            if (sqlPattern.matcher(entry.value).find()) {
                matches.add(entry);
            }
        }

        log("Found " + matches.size() + " SQL strings");
        return wrapEntries(matches);
    }

    private ScriptValue findSecrets() {
        ensureExtracted();
        Pattern secretPattern = Pattern.compile(
            "(password|secret|api[_-]?key|token|auth|credential|private[_-]?key)",
            Pattern.CASE_INSENSITIVE
        );

        List<StringEntry> matches = new ArrayList<>();
        for (StringEntry entry : cachedStrings) {
            if (secretPattern.matcher(entry.value).find()) {
                matches.add(entry);
            }
        }

        log("Found " + matches.size() + " potential secret strings");
        return wrapEntries(matches);
    }

    private ScriptValue groupByClass() {
        ensureExtracted();
        Map<String, List<StringEntry>> groups = new LinkedHashMap<>();

        for (StringEntry entry : cachedStrings) {
            groups.computeIfAbsent(entry.className, k -> new ArrayList<>()).add(entry);
        }

        Map<String, ScriptValue> result = new HashMap<>();
        for (Map.Entry<String, List<StringEntry>> e : groups.entrySet()) {
            List<ScriptValue> stringValues = new ArrayList<>();
            for (StringEntry se : e.getValue()) {
                stringValues.add(ScriptValue.string(se.value));
            }
            result.put(e.getKey(), ScriptValue.array(stringValues));
        }

        return ScriptValue.object(result);
    }

    private ScriptValue uniqueStrings() {
        ensureExtracted();
        Set<String> seen = new LinkedHashSet<>();
        List<ScriptValue> unique = new ArrayList<>();

        for (StringEntry entry : cachedStrings) {
            if (seen.add(entry.value)) {
                unique.add(ScriptValue.string(entry.value));
            }
        }

        log("Found " + unique.size() + " unique strings");
        return ScriptValue.array(unique);
    }

    private ScriptValue wrapEntries(List<StringEntry> entries) {
        List<ScriptValue> list = new ArrayList<>();
        for (StringEntry entry : entries) {
            Map<String, ScriptValue> props = new HashMap<>();
            props.put("value", ScriptValue.string(entry.value));
            props.put("className", ScriptValue.string(entry.className));
            props.put("length", ScriptValue.number(entry.value.length()));
            list.add(ScriptValue.object(props));
        }
        return ScriptValue.array(list);
    }

    private static class StringEntry {
        final String value;
        final String className;

        StringEntry(String value, String className) {
            this.value = value;
            this.className = className;
        }
    }
}
