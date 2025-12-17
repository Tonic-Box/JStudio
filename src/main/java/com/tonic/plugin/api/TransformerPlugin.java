package com.tonic.plugin.api;

import java.util.List;

public interface TransformerPlugin extends Plugin {

    TransformResult transform(TransformScope scope);

    @Override
    default void execute() {
        transform(TransformScope.all());
    }

    final class TransformScope {
        private final List<String> targetClasses;
        private final List<String> targetMethods;
        private final boolean dryRun;

        public TransformScope(List<String> targetClasses, List<String> targetMethods, boolean dryRun) {
            this.targetClasses = targetClasses;
            this.targetMethods = targetMethods;
            this.dryRun = dryRun;
        }

        public List<String> getTargetClasses() { return targetClasses; }
        public List<String> getTargetMethods() { return targetMethods; }
        public boolean isDryRun() { return dryRun; }

        public static TransformScope all() {
            return new TransformScope(List.of(), List.of(), false);
        }

        public static TransformScope classes(List<String> classPatterns) {
            return new TransformScope(classPatterns, List.of(), false);
        }

        public static TransformScope methods(List<String> methodPatterns) {
            return new TransformScope(List.of(), methodPatterns, false);
        }

        public static TransformScope dryRun() {
            return new TransformScope(List.of(), List.of(), true);
        }

        public TransformScope withDryRun(boolean dryRun) {
            return new TransformScope(targetClasses, targetMethods, dryRun);
        }
    }

    final class TransformResult {
        private final boolean success;
        private final int classesModified;
        private final int methodsModified;
        private final long durationMs;
        private final String summary;
        private final List<TransformAction> actions;

        public TransformResult(boolean success, int classesModified, int methodsModified,
                               long durationMs, String summary, List<TransformAction> actions) {
            this.success = success;
            this.classesModified = classesModified;
            this.methodsModified = methodsModified;
            this.durationMs = durationMs;
            this.summary = summary;
            this.actions = actions;
        }

        public boolean isSuccess() { return success; }
        public int getClassesModified() { return classesModified; }
        public int getMethodsModified() { return methodsModified; }
        public long getDurationMs() { return durationMs; }
        public String getSummary() { return summary; }
        public List<TransformAction> getActions() { return actions; }

        public static TransformResult success(int classes, int methods, long durationMs,
                                              String summary, List<TransformAction> actions) {
            return new TransformResult(true, classes, methods, durationMs, summary, actions);
        }

        public static TransformResult failure(String reason) {
            return new TransformResult(false, 0, 0, 0, reason, List.of());
        }

        public static TransformResult dryRun(List<TransformAction> plannedActions) {
            return new TransformResult(true, 0, 0, 0,
                "Dry run: " + plannedActions.size() + " actions planned", plannedActions);
        }
    }

    final class TransformAction {
        private final ActionType type;
        private final String target;
        private final String description;

        public TransformAction(ActionType type, String target, String description) {
            this.type = type;
            this.target = target;
            this.description = description;
        }

        public ActionType getType() { return type; }
        public String getTarget() { return target; }
        public String getDescription() { return description; }

        public enum ActionType {
            ADD_INSTRUCTION,
            REMOVE_INSTRUCTION,
            MODIFY_INSTRUCTION,
            ADD_METHOD,
            REMOVE_METHOD,
            MODIFY_METHOD,
            ADD_FIELD,
            REMOVE_FIELD,
            MODIFY_CLASS
        }
    }
}
