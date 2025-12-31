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

    interface CallGraphApi {

        void build();

        void buildForClass(String className);

        List<CallSite> getCallersOf(String className, String methodName);

        List<CallSite> getCalleesOf(String className, String methodName);

        List<String> getCallChain(String fromClass, String fromMethod, String toClass, String toMethod);

        Set<String> getReachableMethods(String className, String methodName);

        boolean canReach(String fromClass, String fromMethod, String toClass, String toMethod);

        int getCallCount(String className, String methodName);
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
}
