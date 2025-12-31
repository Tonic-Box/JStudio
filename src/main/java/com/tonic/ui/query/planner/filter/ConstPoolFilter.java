package com.tonic.ui.query.planner.filter;

import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Filter classes/methods by constant pool string content.
 */
public class ConstPoolFilter implements StaticFilter {

    private final Pattern pattern;

    private ConstPoolFilter(String pattern, boolean isRegex) {
        this.pattern = isRegex ? Pattern.compile(pattern) : Pattern.compile(Pattern.quote(pattern));
    }

    public static ConstPoolFilter containsString(String literal) {
        return new ConstPoolFilter(literal, false);
    }

    public static ConstPoolFilter matchesString(String regex) {
        return new ConstPoolFilter(regex, true);
    }

    @Override
    public Set<MethodEntry> filterMethods(Stream<MethodEntry> methods) {
        return methods
            .filter(m -> classContainsString(m.getClassFile()))
            .collect(Collectors.toSet());
    }

    @Override
    public Set<ClassFile> filterClasses(Stream<ClassFile> classes) {
        return classes
            .filter(this::classContainsString)
            .collect(Collectors.toSet());
    }

    private boolean classContainsString(ClassFile cf) {
        ConstPool cp = cf.getConstPool();
        if (cp == null) return false;

        Set<String> strings = new HashSet<>();
        var items = cp.getItems();

        for (int i = 1; i < items.size(); i++) {
            Object item = cp.getItem(i);
            if (item instanceof StringRefItem) {
                StringRefItem sri = (StringRefItem) item;
                Object utf8 = cp.getItem(sri.getValue());
                if (utf8 instanceof Utf8Item) {
                    Utf8Item u = (Utf8Item) utf8;
                    strings.add(u.getValue());
                }
            } else if (item instanceof Utf8Item) {
                Utf8Item u = (Utf8Item) item;
                strings.add(u.getValue());
            }
        }

        for (String s : strings) {
            if (pattern.matcher(s).find()) {
                return true;
            }
        }
        return false;
    }
}
