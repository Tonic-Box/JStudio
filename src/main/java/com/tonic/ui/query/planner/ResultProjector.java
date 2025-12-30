package com.tonic.ui.query.planner;

import com.tonic.ui.query.ast.Target;
import com.tonic.ui.query.planner.probe.ProbeResult;

import java.util.List;

/**
 * Projects probe results into query result rows.
 * Shapes the output based on the query target type.
 */
public interface ResultProjector {

    List<ResultRow> project(ProbeResult result);

    Target getTargetType();

    static ResultProjector forTarget(Target target) {
        switch (target) {
            case METHODS: return new MethodProjector();
            case CLASSES: return new ClassProjector();
            case PATHS: return new PathProjector();
            case EVENTS: return new EventProjector();
            case STRINGS: return new StringProjector();
            case OBJECTS: return new ObjectProjector();
            default: throw new IllegalArgumentException("Unknown target: " + target);
        }
    }

    class MethodProjector implements ResultProjector {
        @Override
        public List<ResultRow> project(ProbeResult result) {
            return result.toMethodRows();
        }

        @Override
        public Target getTargetType() {
            return Target.METHODS;
        }
    }

    class ClassProjector implements ResultProjector {
        @Override
        public List<ResultRow> project(ProbeResult result) {
            return result.toClassRows();
        }

        @Override
        public Target getTargetType() {
            return Target.CLASSES;
        }
    }

    class PathProjector implements ResultProjector {
        @Override
        public List<ResultRow> project(ProbeResult result) {
            return result.toPathRows();
        }

        @Override
        public Target getTargetType() {
            return Target.PATHS;
        }
    }

    class EventProjector implements ResultProjector {
        @Override
        public List<ResultRow> project(ProbeResult result) {
            return result.toEventRows();
        }

        @Override
        public Target getTargetType() {
            return Target.EVENTS;
        }
    }

    class StringProjector implements ResultProjector {
        @Override
        public List<ResultRow> project(ProbeResult result) {
            return result.toStringRows();
        }

        @Override
        public Target getTargetType() {
            return Target.STRINGS;
        }
    }

    class ObjectProjector implements ResultProjector {
        @Override
        public List<ResultRow> project(ProbeResult result) {
            return result.toObjectRows();
        }

        @Override
        public Target getTargetType() {
            return Target.OBJECTS;
        }
    }
}
