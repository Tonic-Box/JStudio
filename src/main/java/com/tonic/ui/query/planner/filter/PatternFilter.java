package com.tonic.ui.query.planner.filter;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Filter by class or method name pattern.
 */
public class PatternFilter implements StaticFilter {

    private final Pattern classPattern;
    private final Pattern methodPattern;

    private PatternFilter(Pattern classPattern, Pattern methodPattern) {
        this.classPattern = classPattern;
        this.methodPattern = methodPattern;
    }

    public static PatternFilter classMatching(String regex) {
        return new PatternFilter(Pattern.compile(regex), null);
    }

    public static PatternFilter methodMatching(String regex) {
        return new PatternFilter(null, Pattern.compile(regex));
    }

    public static PatternFilter classAndMethod(String classRegex, String methodRegex) {
        return new PatternFilter(
            classRegex != null ? Pattern.compile(classRegex) : null,
            methodRegex != null ? Pattern.compile(methodRegex) : null
        );
    }

    public static PatternFilter clinitMethods() {
        return methodMatching(".*\\.<clinit>\\(\\)V");
    }

    @Override
    public Set<MethodEntry> filterMethods(Stream<MethodEntry> methods) {
        return methods
            .filter(m -> {
                if (classPattern != null) {
                    String className = m.getOwnerName();
                    if (!classPattern.matcher(className).matches() &&
                        !classPattern.matcher(className).find()) {
                        return false;
                    }
                }
                if (methodPattern != null) {
                    String methodSig = m.getOwnerName() + "." + m.getName() + m.getDesc();
                    String methodName = m.getName();
                    return methodPattern.matcher(methodSig).matches() ||
                            methodPattern.matcher(methodSig).find() ||
                            methodPattern.matcher(methodName).matches() ||
                            methodPattern.matcher(methodName).find();
                }
                return true;
            })
            .collect(Collectors.toSet());
    }

    @Override
    public Set<ClassFile> filterClasses(Stream<ClassFile> classes) {
        if (classPattern == null) {
            return classes.collect(Collectors.toSet());
        }

        return classes
            .filter(cf -> {
                String className = cf.getClassName();
                return classPattern.matcher(className).matches() ||
                       classPattern.matcher(className).find();
            })
            .collect(Collectors.toSet());
    }
}
