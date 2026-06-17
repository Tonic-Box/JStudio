package com.tonic.plugin.api;

import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface AnalysisApi {

    CallGraphApi getCallGraph();

    DataFlowApi getDataFlow();

    PatternApi getPatterns();

    TypeApi getTypes();

    StringApi getStrings();

    DecompileApi getDecompile();

    XrefApi getXrefs();

    QueryApi getQuery();

    DeadCodeApi getDeadCode();

    interface CallGraphApi {

        void build();

        void buildForClass(String className);

        List<CallSite> getCallersOf(String className, String methodName);

        List<CallSite> getCalleesOf(String className, String methodName);

        List<String> getCallChain(String fromClass, String fromMethod, String toClass, String toMethod);

        Set<String> getReachableMethods(String className, String methodName);

        boolean canReach(String fromClass, String fromMethod, String toClass, String toMethod);

        int getCallCount(String className, String methodName);

        /** Transitive callers up to {@code depth} edges, bounded to {@code maxNodes} results. */
        List<CallNode> getCallersToDepth(String className, String methodName, String descriptor, int depth, int maxNodes);

        /** Transitive callees up to {@code depth} edges, bounded to {@code maxNodes} results. */
        List<CallNode> getCalleesToDepth(String className, String methodName, String descriptor, int depth, int maxNodes);
    }

    /** Decompiled Java source access, backed by the per-class decompilation cache. Call off the EDT. */
    interface DecompileApi {

        /** Full decompiled source for a class, or empty if it cannot be resolved/decompiled. */
        Optional<String> getSource(String className);

        /** Per-method source line spans (1-based, inclusive) for a class. */
        List<MethodSourceInfo> getMethodSpans(String className);

        /** The source span for one method (descriptor required to disambiguate overloads). */
        Optional<MethodSourceInfo> getMethodSpan(String className, String methodName, String descriptor);
    }

    /** Cross-reference / "find usages" over user code. Call {@link #ensureBuilt()} (off the EDT) first. */
    interface XrefApi {

        enum TargetKind { CLASS, METHOD, FIELD }

        /** Builds the xref database for the current project if needed (expensive; call off the EDT). */
        void ensureBuilt();

        /** Usages of a class/method/field. {@code memberName}/{@code descriptor} are ignored for CLASS. */
        List<UsageInfo> findUsages(TargetKind kind, String className, String memberName, String descriptor);
    }

    /** Runs a JStudio bytecode Query DSL query. Owns the query executor lifecycle. Call off the EDT. */
    interface QueryApi {

        QueryResult run(String dsl, long timeBudgetMs, int limit);
    }

    /** Whole-project dead-code (reachability) analysis. Call off the EDT. */
    interface DeadCodeApi {

        DeadCodeResult analyze(boolean publicAsEntryPoints);
    }

    interface DataFlowApi {

        DataFlowResult analyze(String className, String methodName);

        List<TaintFlow> trackTaint(String source, String sink);

        List<String> getDefinitions(String className, String methodName, int varIndex);

        List<String> getUses(String className, String methodName, int varIndex);

        boolean hasUninitializedRead(String className, String methodName);
    }

    interface PatternApi {

        List<PatternMatch> findPattern(String patternType);

        List<PatternMatch> findMethodCalls(String ownerPattern, String namePattern);

        List<PatternMatch> findFieldAccess(String ownerPattern, String namePattern);

        List<PatternMatch> findStringLiterals(String pattern);

        List<PatternMatch> findAnnotations(String annotationType);

        void registerCustomPattern(String name, PatternMatcher matcher);
    }

    interface TypeApi {

        Optional<String> resolveType(String className, String descriptor);

        boolean isSubtypeOf(String type, String supertype);

        List<String> getSubtypes(String type);

        List<String> getSupertypes(String type);

        String getCommonSupertype(String type1, String type2);
    }

    interface StringApi {

        List<StringInfo> getAllStrings();

        List<StringInfo> findStrings(String pattern);

        Map<String, List<StringInfo>> getStringsByClass();

        List<StringInfo> getPotentialSecrets();

        List<StringInfo> getUrls();

        List<StringInfo> getSqlQueries();
    }

    @Getter
    final class CallSite {
        private final String callerClass;
        private final String callerMethod;
        private final String calleeClass;
        private final String calleeMethod;
        private final int lineNumber;

        public CallSite(String callerClass, String callerMethod, String calleeClass, String calleeMethod, int lineNumber) {
            this.callerClass = callerClass;
            this.callerMethod = callerMethod;
            this.calleeClass = calleeClass;
            this.calleeMethod = calleeMethod;
            this.lineNumber = lineNumber;
        }

    }

    @Getter
    final class DataFlowResult {
        private final String className;
        private final String methodName;
        private final List<DataFlowNode> nodes;
        private final List<DataFlowEdge> edges;

        public DataFlowResult(String className, String methodName, List<DataFlowNode> nodes, List<DataFlowEdge> edges) {
            this.className = className;
            this.methodName = methodName;
            this.nodes = nodes;
            this.edges = edges;
        }

    }

    @Getter
    final class DataFlowNode {
        private final int id;
        private final String type;
        private final String value;
        private final int instructionIndex;

        public DataFlowNode(int id, String type, String value, int instructionIndex) {
            this.id = id;
            this.type = type;
            this.value = value;
            this.instructionIndex = instructionIndex;
        }

    }

    @Getter
    final class DataFlowEdge {
        private final int fromNode;
        private final int toNode;
        private final String edgeType;

        public DataFlowEdge(int fromNode, int toNode, String edgeType) {
            this.fromNode = fromNode;
            this.toNode = toNode;
            this.edgeType = edgeType;
        }

    }

    @Getter
    final class TaintFlow {
        private final String source;
        private final String sink;
        private final List<String> path;

        public TaintFlow(String source, String sink, List<String> path) {
            this.source = source;
            this.sink = sink;
            this.path = path;
        }

    }

    @Getter
    final class PatternMatch {
        private final String className;
        private final String methodName;
        private final int lineNumber;
        private final String matchedText;
        private final Map<String, Object> metadata;

        public PatternMatch(String className, String methodName, int lineNumber, String matchedText, Map<String, Object> metadata) {
            this.className = className;
            this.methodName = methodName;
            this.lineNumber = lineNumber;
            this.matchedText = matchedText;
            this.metadata = metadata;
        }

    }

    @Getter
    final class StringInfo {
        private final String value;
        private final String className;
        private final String methodName;
        private final int lineNumber;

        public StringInfo(String value, String className, String methodName, int lineNumber) {
            this.value = value;
            this.className = className;
            this.methodName = methodName;
            this.lineNumber = lineNumber;
        }

    }

    @FunctionalInterface
    interface PatternMatcher {
        List<PatternMatch> match(ProjectApi.ClassInfo classInfo);
    }

    @Getter
    final class CallNode {
        private final String owner;
        private final String name;
        private final String descriptor;
        private final int depth;

        public CallNode(String owner, String name, String descriptor, int depth) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.depth = depth;
        }
    }

    @Getter
    final class MethodSourceInfo {
        private final String className;
        private final String methodName;
        private final String descriptor;
        private final int startLine;
        private final int endLine;

        public MethodSourceInfo(String className, String methodName, String descriptor, int startLine, int endLine) {
            this.className = className;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.startLine = startLine;
            this.endLine = endLine;
        }
    }

    @Getter
    final class UsageInfo {
        private final String sourceClass;
        private final String sourceMethod;
        private final String sourceMethodDesc;
        private final String type;
        private final String targetMember;

        public UsageInfo(String sourceClass, String sourceMethod, String sourceMethodDesc,
                         String type, String targetMember) {
            this.sourceClass = sourceClass;
            this.sourceMethod = sourceMethod;
            this.sourceMethodDesc = sourceMethodDesc;
            this.type = type;
            this.targetMember = targetMember;
        }
    }

    @Getter
    final class QueryResult {
        private final boolean error;
        private final String errorMessage;
        private final List<String> matchLabels;
        private final int totalCount;
        private final long executionMs;
        private final boolean truncated;

        public QueryResult(boolean error, String errorMessage, List<String> matchLabels,
                           int totalCount, long executionMs, boolean truncated) {
            this.error = error;
            this.errorMessage = errorMessage;
            this.matchLabels = matchLabels;
            this.totalCount = totalCount;
            this.executionMs = executionMs;
            this.truncated = truncated;
        }
    }

    @Getter
    final class DeadCodeResult {
        private final int deadClassCount;
        private final int deadMethodCount;
        private final int deadFieldCount;
        private final List<DeadItemInfo> deadClasses;
        private final List<DeadItemInfo> deadMethods;
        private final List<DeadItemInfo> deadFields;

        public DeadCodeResult(List<DeadItemInfo> deadClasses, List<DeadItemInfo> deadMethods,
                              List<DeadItemInfo> deadFields) {
            this.deadClasses = deadClasses;
            this.deadMethods = deadMethods;
            this.deadFields = deadFields;
            this.deadClassCount = deadClasses.size();
            this.deadMethodCount = deadMethods.size();
            this.deadFieldCount = deadFields.size();
        }
    }

    @Getter
    final class DeadItemInfo {
        private final String owner;
        private final String displayLabel;

        public DeadItemInfo(String owner, String displayLabel) {
            this.owner = owner;
            this.displayLabel = displayLabel;
        }
    }
}
