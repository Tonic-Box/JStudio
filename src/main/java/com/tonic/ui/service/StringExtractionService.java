package com.tonic.ui.service;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.ProjectModel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StringExtractionService {

    private final ProjectModel project;

    public StringExtractionService(ProjectModel project) {
        this.project = project;
    }

    public List<ExtractedString> extractAll() {
        return extractAll(null);
    }

    public List<ExtractedString> extractAll(Consumer<String> progressCallback) {
        List<ExtractedString> strings = new ArrayList<>();

        List<ClassEntryModel> classes = project.getUserClasses();
        int total = classes.size();
        int current = 0;

        for (ClassEntryModel classEntry : classes) {
            current++;
            if (progressCallback != null) {
                progressCallback.accept("Processing class " + current + "/" + total);
            }

            strings.addAll(extractFromClass(classEntry));
        }

        return strings;
    }

    public List<ExtractedString> extractFromClass(ClassEntryModel classEntry) {
        List<ExtractedString> strings = new ArrayList<>();
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
                            strings.add(new ExtractedString(
                                    str,
                                    classEntry.getClassName(),
                                    classEntry,
                                    i
                            ));
                        }
                    }
                }
            } catch (Exception e) {
                // Skip invalid entries
            }
        }

        return strings;
    }

    public List<ExtractedString> search(String pattern, boolean regex, boolean caseSensitive) {
        List<ExtractedString> all = extractAll();
        List<ExtractedString> matches = new ArrayList<>();

        for (ExtractedString s : all) {
            if (matches(s.getValue(), pattern, regex, caseSensitive)) {
                matches.add(s);
            }
        }

        return matches;
    }

    private boolean matches(String text, String pattern, boolean regex, boolean caseSensitive) {
        if (regex) {
            String flags = caseSensitive ? "" : "(?i)";
            return text.matches(flags + pattern);
        } else {
            if (caseSensitive) {
                return text.contains(pattern);
            } else {
                return text.toLowerCase().contains(pattern.toLowerCase());
            }
        }
    }

    @Getter
    public static class ExtractedString {
        private final String value;
        private final String className;
        private final ClassEntryModel classEntry;
        private final int constPoolIndex;

        public ExtractedString(String value, String className, ClassEntryModel classEntry, int constPoolIndex) {
            this.value = value;
            this.className = className;
            this.classEntry = classEntry;
            this.constPoolIndex = constPoolIndex;
        }

        public String getFormattedClassName() {
            return className != null ? className.replace('/', '.') : "?";
        }

    }
}
