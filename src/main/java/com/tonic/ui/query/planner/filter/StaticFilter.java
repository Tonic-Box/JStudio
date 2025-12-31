package com.tonic.ui.query.planner.filter;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Static prefilter that narrows candidate methods/classes without execution.
 * Uses xrefs, callgraph, pattern matching, and metadata.
 */
public interface StaticFilter {

    Set<MethodEntry> filterMethods(Stream<MethodEntry> methods);

    Set<ClassFile> filterClasses(Stream<ClassFile> classes);

    default StaticFilter and(StaticFilter other) {
        return new CompositeFilter(this, other, CompositeFilter.Op.AND);
    }

    default StaticFilter or(StaticFilter other) {
        return new CompositeFilter(this, other, CompositeFilter.Op.OR);
    }

    static StaticFilter all() {
        return new AllFilter();
    }

    static StaticFilter none() {
        return new NoneFilter();
    }

    class AllFilter implements StaticFilter {
        @Override
        public Set<MethodEntry> filterMethods(Stream<MethodEntry> methods) {
            return methods.collect(Collectors.toSet());
        }

        @Override
        public Set<ClassFile> filterClasses(Stream<ClassFile> classes) {
            return classes.collect(Collectors.toSet());
        }
    }

    class NoneFilter implements StaticFilter {
        @Override
        public Set<MethodEntry> filterMethods(Stream<MethodEntry> methods) {
            return Set.of();
        }

        @Override
        public Set<ClassFile> filterClasses(Stream<ClassFile> classes) {
            return Set.of();
        }
    }

    class CompositeFilter implements StaticFilter {
        public enum Op { AND, OR }

        private final StaticFilter left;
        private final StaticFilter right;
        private final Op op;

        public CompositeFilter(StaticFilter left, StaticFilter right, Op op) {
            this.left = left;
            this.right = right;
            this.op = op;
        }

        @Override
        public Set<MethodEntry> filterMethods(Stream<MethodEntry> methods) {
            Set<MethodEntry> all = methods.collect(Collectors.toSet());
            Set<MethodEntry> leftResult = left.filterMethods(all.stream());
            Set<MethodEntry> rightResult = right.filterMethods(all.stream());

            if (op == Op.AND) {
                leftResult.retainAll(rightResult);
            } else {
                leftResult.addAll(rightResult);
            }
            return leftResult;
        }

        @Override
        public Set<ClassFile> filterClasses(Stream<ClassFile> classes) {
            Set<ClassFile> all = classes.collect(Collectors.toSet());
            Set<ClassFile> leftResult = left.filterClasses(all.stream());
            Set<ClassFile> rightResult = right.filterClasses(all.stream());

            if (op == Op.AND) {
                leftResult.retainAll(rightResult);
            } else {
                leftResult.addAll(rightResult);
            }
            return leftResult;
        }
    }
}
