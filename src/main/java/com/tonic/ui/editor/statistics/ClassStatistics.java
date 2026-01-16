package com.tonic.ui.editor.statistics;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ClassStatistics {

    private final int methodCount;
    private final int fieldCount;
    private final int totalBytecodeSize;
    private final double averageComplexity;
    private final int interfaceCount;
    private final int abstractMethodCount;
    private final int staticMethodCount;
    private final int nativeMethodCount;

    private final List<MethodSizeInfo> methodSizes;
    private final List<MethodDetailInfo> methodDetails;
    private final Map<OpcodeCategory, Integer> opcodeDistribution;

    private final int lowComplexityCount;
    private final int mediumComplexityCount;
    private final int highComplexityCount;

    @Getter
    @Builder
    public static class MethodSizeInfo {
        private final String name;
        private final int bytecodeSize;
        private final int maxStack;
        private final int maxLocals;
    }

    @Getter
    @Builder
    public static class MethodDetailInfo {
        private final String name;
        private final String descriptor;
        private final int bytecodeSize;
        private final int maxStack;
        private final int maxLocals;
        private final int cyclomaticComplexity;
        private final int loopCount;
        private final int branchCount;
        private final boolean isStatic;
        private final boolean isAbstract;
        private final boolean isNative;
    }

    @Getter
    public enum OpcodeCategory {
        INVOKE("Invoke", "Method invocations"),
        LOAD("Load", "Load operations"),
        STORE("Store", "Store operations"),
        BRANCH("Branch", "Control flow branches"),
        CONST("Const", "Constants and literals"),
        ARITHMETIC("Arithmetic", "Math operations"),
        ARRAY("Array", "Array operations"),
        FIELD("Field", "Field access"),
        STACK("Stack", "Stack manipulation"),
        RETURN("Return", "Return statements"),
        OTHER("Other", "Other instructions");

        private final String displayName;
        private final String description;

        OpcodeCategory(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }
}
