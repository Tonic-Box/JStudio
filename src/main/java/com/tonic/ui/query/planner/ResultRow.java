package com.tonic.ui.query.planner;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single row in query results.
 * Contains a primary target, computed columns, and evidence list.
 */
public class ResultRow {

    private final String primaryLabel;
    private final ClickTarget primaryTarget;
    private final Map<String, Object> columns;
    private final List<Evidence> evidence;
    private final List<ResultRow> children;
    private final boolean isChild;

    public ResultRow(String primaryLabel, ClickTarget primaryTarget,
                     Map<String, Object> columns, List<Evidence> evidence) {
        this(primaryLabel, primaryTarget, columns, evidence, Collections.emptyList(), false);
    }

    public ResultRow(String primaryLabel, ClickTarget primaryTarget,
                     Map<String, Object> columns, List<Evidence> evidence,
                     List<ResultRow> children, boolean isChild) {
        this.primaryLabel = primaryLabel;
        this.primaryTarget = primaryTarget;
        this.columns = columns != null ? new LinkedHashMap<>(columns) : new LinkedHashMap<>();
        this.evidence = evidence != null ? List.copyOf(evidence) : List.of();
        this.children = children != null ? List.copyOf(children) : List.of();
        this.isChild = isChild;
    }

    public String getPrimaryLabel() {
        return primaryLabel;
    }

    public ClickTarget getPrimaryTarget() {
        return primaryTarget;
    }

    public Map<String, Object> getColumns() {
        return Collections.unmodifiableMap(columns);
    }

    public Object getColumn(String name) {
        return columns.get(name);
    }

    public List<Evidence> getEvidence() {
        return evidence;
    }

    public List<ResultRow> getChildren() {
        return children;
    }

    public boolean hasChildren() {
        return !children.isEmpty();
    }

    public boolean isChild() {
        return isChild;
    }

    public static Builder builder(String primaryLabel) {
        return new Builder(primaryLabel);
    }

    public static class Builder {
        private final String primaryLabel;
        private ClickTarget primaryTarget;
        private final Map<String, Object> columns = new LinkedHashMap<>();
        private List<Evidence> evidence = List.of();
        private List<ResultRow> children = List.of();
        private boolean isChild = false;

        public Builder(String primaryLabel) {
            this.primaryLabel = primaryLabel;
        }

        public Builder target(ClickTarget target) {
            this.primaryTarget = target;
            return this;
        }

        public Builder column(String name, Object value) {
            columns.put(name, value);
            return this;
        }

        public Builder evidence(List<Evidence> evidence) {
            this.evidence = evidence;
            return this;
        }

        public Builder children(List<ResultRow> children) {
            this.children = children;
            return this;
        }

        public Builder asChild() {
            this.isChild = true;
            return this;
        }

        public ResultRow build() {
            return new ResultRow(primaryLabel, primaryTarget, columns, evidence, children, isChild);
        }
    }
}
