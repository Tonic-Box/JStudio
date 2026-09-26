package com.tonic.plugin.api;

import lombok.Getter;

import java.util.List;

/** A plugin that rewrites the project's bytecode; its execute runs transform over everything and discards the result. */
public interface TransformerPlugin extends Plugin
{

    /**
     * Transforms the classes and methods the scope selects, or only plans the changes when the scope is a dry run.
     *
     * @param scope what to transform and whether to apply it
     * @return the outcome and the actions taken or planned
     */
    TransformResult transform(TransformScope scope);

    @Override
    default void execute()
    {
        transform(TransformScope.all());
    }

    /** What a transform targets: class and method patterns, where empty lists mean everything, and whether to only plan; the plugin decides how patterns match. */
    @Getter
    final class TransformScope
    {
        private final List<String> targetClasses;
        private final List<String> targetMethods;
        private final boolean dryRun;

        /**
         * Creates a scope.
         *
         * @param targetClasses class patterns to target, empty for all
         * @param targetMethods method patterns to target, empty for all
         * @param dryRun true to plan without changing anything
         */
        public TransformScope(List<String> targetClasses, List<String> targetMethods, boolean dryRun)
        {
            this.targetClasses = targetClasses;
            this.targetMethods = targetMethods;
            this.dryRun = dryRun;
        }

        /**
         * Creates a scope that targets everything and applies changes.
         *
         * @return the scope
         */
        public static TransformScope all()
        {
            return new TransformScope(List.of(), List.of(), false);
        }

        /**
         * Creates a scope limited to matching classes that applies changes.
         *
         * @param classPatterns the class patterns to target
         * @return the scope
         */
        public static TransformScope classes(List<String> classPatterns)
        {
            return new TransformScope(classPatterns, List.of(), false);
        }

        /**
         * Creates a scope limited to matching methods that applies changes.
         *
         * @param methodPatterns the method patterns to target
         * @return the scope
         */
        public static TransformScope methods(List<String> methodPatterns)
        {
            return new TransformScope(List.of(), methodPatterns, false);
        }

        /**
         * Creates a scope that targets everything but only plans.
         *
         * @return the scope
         */
        public static TransformScope dryRun()
        {
            return new TransformScope(List.of(), List.of(), true);
        }

        /**
         * Copies this scope with the dry-run flag replaced.
         *
         * @param dryRun true to plan without changing anything
         * @return the new scope
         */
        public TransformScope withDryRun(boolean dryRun)
        {
            return new TransformScope(targetClasses, targetMethods, dryRun);
        }
    }

    /** The outcome of a transform: success, how many classes and methods changed, how long it took, a summary and the actions taken or planned. */
    @Getter
    final class TransformResult
    {
        private final boolean success;
        private final int classesModified;
        private final int methodsModified;
        private final long durationMs;
        private final String summary;
        private final List<TransformAction> actions;

        /**
         * Creates a result.
         *
         * @param success whether the transform completed
         * @param classesModified how many classes changed
         * @param methodsModified how many methods changed
         * @param durationMs how long it took, in milliseconds
         * @param summary a one-line summary, or the failure reason
         * @param actions the actions taken or planned
         */
        public TransformResult(boolean success, int classesModified, int methodsModified, long durationMs, String summary, List<TransformAction> actions)
        {
            this.success = success;
            this.classesModified = classesModified;
            this.methodsModified = methodsModified;
            this.durationMs = durationMs;
            this.summary = summary;
            this.actions = actions;
        }

        /**
         * Creates a successful result.
         *
         * @param classes how many classes changed
         * @param methods how many methods changed
         * @param durationMs how long it took, in milliseconds
         * @param summary a one-line summary
         * @param actions the actions taken
         * @return the result
         */
        public static TransformResult success(int classes, int methods, long durationMs, String summary, List<TransformAction> actions)
        {
            return new TransformResult(true, classes, methods, durationMs, summary, actions);
        }

        /**
         * Creates a failed result with no changes and no actions.
         *
         * @param reason why the transform failed; becomes the summary
         * @return the result
         */
        public static TransformResult failure(String reason)
        {
            return new TransformResult(false, 0, 0, 0, reason, List.of());
        }

        /**
         * Creates a successful result that changed nothing, summarised as the number of planned actions.
         *
         * @param plannedActions the actions the transform would take
         * @return the result
         */
        public static TransformResult dryRun(List<TransformAction> plannedActions)
        {
            return new TransformResult(true, 0, 0, 0, "Dry run: " + plannedActions.size() + " actions planned", plannedActions);
        }
    }

    /** One change a transform made or plans to make: its kind, its target and a description. */
    @Getter
    final class TransformAction
    {
        private final ActionType type;
        private final String target;
        private final String description;

        /**
         * Creates an action.
         *
         * @param type the kind of change
         * @param target what it changes, such as a class or method name
         * @param description what the change does
         */
        public TransformAction(ActionType type, String target, String description)
        {
            this.type = type;
            this.target = target;
            this.description = description;
        }

        /** The kinds of change a transform can make. */
        public enum ActionType
        {
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
