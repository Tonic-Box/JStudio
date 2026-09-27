package com.tonic.plugin.api;

import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Analyses over the project: call graph, data flow, patterns, types, strings, decompiled source, cross-references, bytecode queries and dead code; state such as a built call graph or registered patterns lives in this instance, and in the GUI getAnalysis returns a new one on each call, so keep the instance you use. */
public interface AnalysisApi
{

    /**
     * Returns the call-graph analyses.
     *
     * @return the API, the same one for this instance
     */
    CallGraphApi getCallGraph();

    /**
     * Returns the data-flow analyses, which are not implemented yet.
     *
     * @return the API, the same one for this instance
     */
    DataFlowApi getDataFlow();

    /**
     * Returns the pattern searches.
     *
     * @return the API, the same one for this instance
     */
    PatternApi getPatterns();

    /**
     * Returns the type-hierarchy queries.
     *
     * @return the API, the same one for this instance
     */
    TypeApi getTypes();

    /**
     * Returns the string-constant searches.
     *
     * @return the API, the same one for this instance
     */
    StringApi getStrings();

    /**
     * Returns decompiled-source access.
     *
     * @return the API, the same one for this instance
     */
    DecompileApi getDecompile();

    /**
     * Returns the cross-reference search.
     *
     * @return the API, the same one for this instance
     */
    XrefApi getXrefs();

    /**
     * Returns the bytecode query runner.
     *
     * @return the API, the same one for this instance
     */
    QueryApi getQuery();

    /**
     * Returns the dead-code analysis.
     *
     * @return the API, the same one for this instance
     */
    DeadCodeApi getDeadCode();

    /** Call-graph queries over the whole project; the graph is built on first use and kept by this instance, and building is slow on a large project, so call off the EDT. Methods are matched by class and name only, taking the first overload found, except in the ToDepth walks. */
    interface CallGraphApi
    {

        /** Builds, or rebuilds, the call graph of the whole project; does nothing when the project has no class pool. */
        void build();

        /**
         * Rebuilds the call graph; the graph always covers the whole project, so this is the same as build.
         *
         * @param className the class the caller is interested in; the graph is built for every class
         */
        void buildForClass(String className);

        /**
         * Returns the methods that call any overload of a method.
         *
         * @param className the method's class, internal or dotted
         * @param methodName the method's name
         * @return a new list with one entry per distinct caller and line -1, empty when no overload is in the graph
         */
        List<CallSite> getCallersOf(String className, String methodName);

        /**
         * Returns the methods any overload of a method calls.
         *
         * @param className the method's class, internal or dotted
         * @param methodName the method's name
         * @return a new list with one entry per distinct callee and line -1, empty when no overload is in the graph
         */
        List<CallSite> getCalleesOf(String className, String methodName);

        /**
         * Finds a call chain between two methods; not implemented yet, so it builds the graph if needed and returns an empty list.
         *
         * @param fromClass the starting method's class
         * @param fromMethod the starting method's name
         * @param toClass the target method's class
         * @param toMethod the target method's name
         * @return an empty list
         */
        List<String> getCallChain(String fromClass, String fromMethod, String toClass, String toMethod);

        /**
         * Returns every method transitively reachable from any overload of a method.
         *
         * @param className the method's class, internal or dotted
         * @param methodName the method's name
         * @return a new set of "owner.name" strings with internal owner names, empty when no overload is in the graph
         */
        Set<String> getReachableMethods(String className, String methodName);

        /**
         * Reports whether any overload of one method can transitively call any overload of another.
         *
         * @param fromClass the starting method's class, internal or dotted
         * @param fromMethod the starting method's name
         * @param toClass the target method's class, internal or dotted
         * @param toMethod the target method's name
         * @return true when a call path exists; false when either method has no overload in the graph
         */
        boolean canReach(String fromClass, String fromMethod, String toClass, String toMethod);

        /**
         * Counts the distinct callers of any overload of a method, not its call sites.
         *
         * @param className the method's class, internal or dotted
         * @param methodName the method's name
         * @return the number of distinct callers
         */
        int getCallCount(String className, String methodName);

        /**
         * Walks callers breadth-first, up to a depth and a result count.
         *
         * @param className the method's class, internal or dotted
         * @param methodName the method's name
         * @param descriptor the method's exact JVM descriptor
         * @param depth the most call edges to follow
         * @param maxNodes the most methods to return
         * @return a new list in discovery order, each with its distance from the method, which itself is not included
         */
        List<CallNode> getCallersToDepth(String className, String methodName, String descriptor, int depth, int maxNodes);

        /**
         * Walks callees breadth-first, up to a depth and a result count.
         *
         * @param className the method's class, internal or dotted
         * @param methodName the method's name
         * @param descriptor the method's exact JVM descriptor
         * @param depth the most call edges to follow
         * @param maxNodes the most methods to return
         * @return a new list in discovery order, each with its distance from the method, which itself is not included
         */
        List<CallNode> getCalleesToDepth(String className, String methodName, String descriptor, int depth, int maxNodes);
    }

    /** Decompiled Java source, cached per class and shared with the editor; decompiling is slow, so call off the EDT. */
    interface DecompileApi
    {

        /**
         * Returns a class's decompiled source, decompiling it when not cached.
         *
         * @param className the class, looked up as ProjectApi.getClass does
         * @return the source, or empty when the class is not found
         */
        Optional<String> getSource(String className);

        /**
         * Returns the source line span of each method in a class, decompiling it when not cached.
         *
         * @param className the class, looked up as ProjectApi.getClass does
         * @return a new list of 1-based inclusive spans, empty when the class is not found or has no spans
         */
        List<MethodSourceInfo> getMethodSpans(String className);

        /**
         * Returns one method's source line span, decompiling its class when not cached.
         *
         * @param className the class, looked up as ProjectApi.getClass does
         * @param methodName the method's name
         * @param descriptor the method's exact JVM descriptor, required
         * @return the 1-based inclusive span, or empty when the class or method is not found
         */
        Optional<MethodSourceInfo> getMethodSpan(String className, String methodName, String descriptor);
    }

    /** Find-usages over the project's code, backed by the project's cross-reference database; building it is slow, so call off the EDT. */
    interface XrefApi
    {

        /** What kind of member a usage search looks for. */
        enum TargetKind
        {CLASS, METHOD, FIELD}

        /** Builds the cross-reference database for this project when it is not built yet. */
        void ensureBuilt();

        /**
         * Finds the usages of a class, method or field, building the database first when needed.
         *
         * @param kind what to search for
         * @param className the target's class, looked up as ProjectApi.getClass does
         * @param memberName the method or field name; ignored for CLASS
         * @param descriptor the member's descriptor, or null for any; ignored for CLASS
         * @return a new list with one entry per usage
         */
        List<UsageInfo> findUsages(TargetKind kind, String className, String memberName, String descriptor);
    }

    /** Runs JStudio bytecode Query DSL queries, blocking until each finishes, so call off the EDT. */
    interface QueryApi
    {

        /**
         * Runs one query to completion on a fresh executor, which is shut down afterwards.
         *
         * @param dsl the query text
         * @param timeBudgetMs how long the query may run, in milliseconds
         * @param limit the most match labels to return
         * @return the labels, total count, time taken and whether the labels were cut short; or an error result with the parser or executor message
         */
        QueryResult run(String dsl, long timeBudgetMs, int limit);
    }

    /** Whole-project dead-code analysis by reachability; slow on a large project, so call off the EDT. */
    interface DeadCodeApi
    {

        /**
         * Finds the classes, methods and fields not reachable from the entry points.
         *
         * @param publicAsEntryPoints true to treat every public member as an entry point, as for a library
         * @return the dead classes, methods and fields
         */
        DeadCodeResult analyze(boolean publicAsEntryPoints);
    }

    /** Per-method data-flow analyses; not implemented yet, so every method returns an empty or false result. */
    interface DataFlowApi
    {

        /**
         * Builds a method's data-flow graph; not implemented yet.
         *
         * @param className the method's class
         * @param methodName the method's name
         * @return a result carrying the names with no nodes or edges
         */
        DataFlowResult analyze(String className, String methodName);

        /**
         * Tracks tainted data from a source to a sink; not implemented yet.
         *
         * @param source the taint source
         * @param sink the taint sink
         * @return an empty list
         */
        List<TaintFlow> trackTaint(String source, String sink);

        /**
         * Lists the definitions of a local variable; not implemented yet.
         *
         * @param className the method's class
         * @param methodName the method's name
         * @param varIndex the local-variable slot
         * @return an empty list
         */
        List<String> getDefinitions(String className, String methodName, int varIndex);

        /**
         * Lists the uses of a local variable; not implemented yet.
         *
         * @param className the method's class
         * @param methodName the method's name
         * @param varIndex the local-variable slot
         * @return an empty list
         */
        List<String> getUses(String className, String methodName, int varIndex);

        /**
         * Reports whether a method reads a variable before writing it; not implemented yet.
         *
         * @param className the method's class
         * @param methodName the method's name
         * @return false
         */
        boolean hasUninitializedRead(String className, String methodName);
    }

    /** Searches for code patterns: plugin-registered matchers, method calls, field names and string literals; registered matchers live only in this instance. */
    interface PatternApi
    {

        /**
         * Runs a registered matcher over every class, giving it the same ClassInfo the project API returns.
         *
         * @param patternType the name the matcher was registered under
         * @return the matches from all classes, or an empty list when no matcher has the name
         */
        List<PatternMatch> findPattern(String patternType);

        /**
         * Finds calls to methods whose owner and name match the patterns, stopping at 100 results.
         *
         * @param ownerPattern the owner's internal name, where * matches anything and the rest is a regular expression matched against the whole name
         * @param namePattern the method's name, where * matches anything and the rest is a regular expression matched against the whole name
         * @return a new list with line -1, empty when the project has no class pool
         * @throws java.util.regex.PatternSyntaxException if a pattern is not a valid regular expression
         */
        List<PatternMatch> findMethodCalls(String ownerPattern, String namePattern);

        /**
         * Finds reads and writes of fields whose owner and name match the patterns, stopping at 100 results.
         *
         * @param ownerPattern the field owner's internal name, as named by the access instruction, where * matches anything and the rest is a regular expression matched against the whole name
         * @param namePattern the field's name, where * matches anything and the rest is a regular expression matched against the whole name
         * @return a new list with line -1, empty when the project has no class pool
         * @throws java.util.regex.PatternSyntaxException if a pattern is not a valid regular expression
         */
        List<PatternMatch> findFieldAccess(String ownerPattern, String namePattern);

        /**
         * Finds string constants containing a match of a regular expression.
         *
         * @param pattern a Java regular expression, matched anywhere in the string
         * @return a new list with an empty method name and line -1
         */
        List<PatternMatch> findStringLiterals(String pattern);

        /**
         * Finds uses of an annotation; not implemented yet.
         *
         * @param annotationType the annotation's type
         * @return an empty list
         */
        List<PatternMatch> findAnnotations(String annotationType);

        /**
         * Registers a matcher for findPattern, replacing any with the same name.
         *
         * @param name the pattern's name
         * @param matcher called once per class
         */
        void registerCustomPattern(String name, PatternMatcher matcher);
    }

    /** Type-hierarchy queries over the project's classes, using internal names; classes outside the project end the walk. */
    interface TypeApi
    {

        /**
         * Resolves a type descriptor; not implemented yet, so it returns the descriptor unchanged.
         *
         * @param className the context class, ignored
         * @param descriptor the descriptor to resolve
         * @return the descriptor
         */
        Optional<String> resolveType(String className, String descriptor);

        /**
         * Reports whether a type extends or implements another, walking superclasses and superinterfaces through the project.
         *
         * @param type the candidate subtype
         * @param supertype the candidate supertype, internal or dotted
         * @return true when type equals supertype or inherits from it
         */
        boolean isSubtypeOf(String type, String supertype);

        /**
         * Lists the project's classes that extend or implement a type, directly or through other project classes.
         *
         * @param type the type, internal or dotted
         * @return a new list of internal names, nearest first
         */
        List<String> getSubtypes(String type);

        /**
         * Lists a type's superclasses and superinterfaces, walked breadth-first through the project, nearest first.
         *
         * @param type the type, looked up as ProjectApi.getClass does
         * @return a new list of internal names, empty when the type is not in the project
         */
        List<String> getSupertypes(String type);

        /**
         * Finds the first type in the first type's supertype list that the second type also has.
         *
         * @param type1 the first type
         * @param type2 the second type
         * @return the shared type, the type itself when both are equal, or java/lang/Object when none is found
         */
        String getCommonSupertype(String type1, String type2);
    }

    /** Searches over the string constants in the project's constant pools; each result carries its class but no method and line -1. */
    interface StringApi
    {

        /**
         * Lists every non-empty string constant in every class.
         *
         * @return a new list, one entry per constant-pool string per class
         */
        List<StringInfo> getAllStrings();

        /**
         * Lists the string constants containing a match of a regular expression.
         *
         * @param pattern a Java regular expression, matched anywhere in the string
         * @return a new list
         */
        List<StringInfo> findStrings(String pattern);

        /**
         * Groups every string constant by class.
         *
         * @return a new map from internal class name to that class's strings
         */
        Map<String, List<StringInfo>> getStringsByClass();

        /**
         * Lists string constants mentioning password, secret, key, token, api key or auth, ignoring case.
         *
         * @return a new list
         */
        List<StringInfo> getPotentialSecrets();

        /**
         * Lists string constants containing an http or https URL.
         *
         * @return a new list
         */
        List<StringInfo> getUrls();

        /**
         * Lists string constants containing an SQL keyword such as SELECT or INSERT followed by whitespace, ignoring case.
         *
         * @return a new list
         */
        List<StringInfo> getSqlQueries();
    }

    /** A call from one method to another; the line is -1 when unknown. */
    @Getter
    final class CallSite
    {
        private final String callerClass;
        private final String callerMethod;
        private final String calleeClass;
        private final String calleeMethod;
        private final int lineNumber;

        /**
         * Creates a call site.
         *
         * @param callerClass the calling method's class
         * @param callerMethod the calling method's name
         * @param calleeClass the called method's class
         * @param calleeMethod the called method's name
         * @param lineNumber the source line of the call, or -1
         */
        public CallSite(String callerClass, String callerMethod, String calleeClass, String calleeMethod, int lineNumber)
        {
            this.callerClass = callerClass;
            this.callerMethod = callerMethod;
            this.calleeClass = calleeClass;
            this.calleeMethod = calleeMethod;
            this.lineNumber = lineNumber;
        }

    }

    /** A method's data-flow graph as nodes and edges. */
    @Getter
    final class DataFlowResult
    {
        private final String className;
        private final String methodName;
        private final List<DataFlowNode> nodes;
        private final List<DataFlowEdge> edges;

        /**
         * Creates a result.
         *
         * @param className the method's class
         * @param methodName the method's name
         * @param nodes the graph's nodes
         * @param edges the graph's edges
         */
        public DataFlowResult(String className, String methodName, List<DataFlowNode> nodes, List<DataFlowEdge> edges)
        {
            this.className = className;
            this.methodName = methodName;
            this.nodes = nodes;
            this.edges = edges;
        }

    }

    /** One node of a data-flow graph. */
    @Getter
    final class DataFlowNode
    {
        private final int id;
        private final String type;
        private final String value;
        private final int instructionIndex;

        /**
         * Creates a node.
         *
         * @param id the node's id within the graph
         * @param type the node's kind
         * @param value the node's value as text
         * @param instructionIndex the instruction it comes from
         */
        public DataFlowNode(int id, String type, String value, int instructionIndex)
        {
            this.id = id;
            this.type = type;
            this.value = value;
            this.instructionIndex = instructionIndex;
        }

    }

    /** One edge of a data-flow graph, between node ids. */
    @Getter
    final class DataFlowEdge
    {
        private final int fromNode;
        private final int toNode;
        private final String edgeType;

        /**
         * Creates an edge.
         *
         * @param fromNode the source node's id
         * @param toNode the target node's id
         * @param edgeType the edge's kind
         */
        public DataFlowEdge(int fromNode, int toNode, String edgeType)
        {
            this.fromNode = fromNode;
            this.toNode = toNode;
            this.edgeType = edgeType;
        }

    }

    /** A path along which tainted data flows from a source to a sink. */
    @Getter
    final class TaintFlow
    {
        private final String source;
        private final String sink;
        private final List<String> path;

        /**
         * Creates a flow.
         *
         * @param source where the data comes from
         * @param sink where it ends up
         * @param path the steps in between
         */
        public TaintFlow(String source, String sink, List<String> path)
        {
            this.source = source;
            this.sink = sink;
            this.path = path;
        }

    }

    /** One pattern match: where it is, the matched text and extra metadata; line -1 when unknown. */
    @Getter
    final class PatternMatch
    {
        private final String className;
        private final String methodName;
        private final int lineNumber;
        private final String matchedText;
        private final Map<String, Object> metadata;

        /**
         * Creates a match.
         *
         * @param className the class containing the match
         * @param methodName the method containing it, or empty
         * @param lineNumber the source line, or -1
         * @param matchedText what matched
         * @param metadata extra details, possibly empty
         */
        public PatternMatch(String className, String methodName, int lineNumber, String matchedText, Map<String, Object> metadata)
        {
            this.className = className;
            this.methodName = methodName;
            this.lineNumber = lineNumber;
            this.matchedText = matchedText;
            this.metadata = metadata;
        }

    }

    /** One string constant and the class it is in; method is empty and line -1 when unknown. */
    @Getter
    final class StringInfo
    {
        private final String value;
        private final String className;
        private final String methodName;
        private final int lineNumber;

        /**
         * Creates an entry.
         *
         * @param value the string
         * @param className the class's internal name
         * @param methodName the method using it, or empty
         * @param lineNumber the source line, or -1
         */
        public StringInfo(String value, String className, String methodName, int lineNumber)
        {
            this.value = value;
            this.className = className;
            this.methodName = methodName;
            this.lineNumber = lineNumber;
        }

    }

    /** A plugin-supplied matcher that findPattern runs once per class. */
    @FunctionalInterface
    interface PatternMatcher
    {
        /**
         * Returns the matches in one class.
         *
         * @param classInfo the class to examine
         * @return the matches, possibly empty
         */
        List<PatternMatch> match(ProjectApi.ClassInfo classInfo);
    }

    /** A method reached in a call-graph walk and its distance, in call edges, from the start. */
    @Getter
    final class CallNode
    {
        private final String owner;
        private final String name;
        private final String descriptor;
        private final int depth;

        /**
         * Creates a node.
         *
         * @param owner the method's class, internal name
         * @param name the method's name
         * @param descriptor the method's descriptor
         * @param depth how many call edges from the start, 1 for direct
         */
        public CallNode(String owner, String name, String descriptor, int depth)
        {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.depth = depth;
        }
    }

    /** A method's line span in its class's decompiled source, 1-based and inclusive. */
    @Getter
    final class MethodSourceInfo
    {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final int startLine;
        private final int endLine;

        /**
         * Creates a span.
         *
         * @param className the class's internal name
         * @param methodName the method's name
         * @param descriptor the method's descriptor
         * @param startLine the first line, 1-based
         * @param endLine the last line, inclusive
         */
        public MethodSourceInfo(String className, String methodName, String descriptor, int startLine, int endLine)
        {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }

    /** One usage of a class or member: the using method and the kind of reference. */
    @Getter
    final class UsageInfo
    {
        private final String sourceClass;
        private final String sourceMethod;
        private final String sourceMethodDesc;
        private final String type;
        private final String targetMember;

        /**
         * Creates a usage.
         *
         * @param sourceClass the using class
         * @param sourceMethod the using method's name
         * @param sourceMethodDesc the using method's descriptor
         * @param type the kind of reference, such as a call or field read
         * @param targetMember the member referenced
         */
        public UsageInfo(String sourceClass, String sourceMethod, String sourceMethodDesc, String type, String targetMember)
        {
            this.sourceClass = sourceClass;
            this.sourceMethod = sourceMethod;
            this.sourceMethodDesc = sourceMethodDesc;
            this.type = type;
            this.targetMember = targetMember;
        }
    }

    /** The outcome of a query: an error with its message, or the first match labels, the total count, the time taken and whether the labels were cut short. */
    @Getter
    final class QueryResult
    {
        private final boolean error;
        private final String errorMessage;
        private final List<String> matchLabels;
        private final int totalCount;
        private final long executionMs;
        private final boolean truncated;

        /**
         * Creates a result.
         *
         * @param error whether the query failed
         * @param errorMessage why it failed, or null
         * @param matchLabels labels for the returned matches
         * @param totalCount how many matches there were in all
         * @param executionMs how long it ran, in milliseconds
         * @param truncated whether more matches exist than labels
         */
        public QueryResult(boolean error, String errorMessage, List<String> matchLabels, int totalCount, long executionMs, boolean truncated)
        {
            this.error = error;
            this.errorMessage = errorMessage;
            this.matchLabels = matchLabels;
            this.totalCount = totalCount;
            this.executionMs = executionMs;
            this.truncated = truncated;
        }
    }

    /** The dead classes, methods and fields a dead-code analysis found, with their counts. */
    @Getter
    final class DeadCodeResult
    {
        private final int deadClassCount;
        private final int deadMethodCount;
        private final int deadFieldCount;
        private final List<DeadItemInfo> deadClasses;
        private final List<DeadItemInfo> deadMethods;
        private final List<DeadItemInfo> deadFields;

        /**
         * Creates a result, taking the counts from the list sizes.
         *
         * @param deadClasses the unreachable classes
         * @param deadMethods the unreachable methods
         * @param deadFields the unreachable fields
         */
        public DeadCodeResult(List<DeadItemInfo> deadClasses, List<DeadItemInfo> deadMethods, List<DeadItemInfo> deadFields)
        {
            this.deadClasses = deadClasses;
            this.deadMethods = deadMethods;
            this.deadFields = deadFields;
            this.deadClassCount = deadClasses.size();
            this.deadMethodCount = deadMethods.size();
            this.deadFieldCount = deadFields.size();
        }
    }

    /** One dead item: its owning class and a label to show. */
    @Getter
    final class DeadItemInfo
    {
        private final String owner;
        private final String displayLabel;

        /**
         * Creates an item.
         *
         * @param owner the owning class
         * @param displayLabel a readable label for the item
         */
        public DeadItemInfo(String owner, String displayLabel)
        {
            this.owner = owner;
            this.displayLabel = displayLabel;
        }
    }
}
