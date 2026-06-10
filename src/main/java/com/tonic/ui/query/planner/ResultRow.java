package com.tonic.ui.query.planner;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A single row in query results: a primary target, computed columns, and optional child rows.
 */
public class ResultRow {

    @Getter
    private final String primaryLabel;
    @Getter
    private final ClickTarget primaryTarget;
    private final Map<String, Object> columns;
    @Getter
    private final List<ResultRow> children;
    private final boolean isChild;

    public ResultRow(String primaryLabel, ClickTarget primaryTarget,
                     Map<String, Object> columns, List<ResultRow> children, boolean isChild) {
        this.primaryLabel = primaryLabel;
        this.primaryTarget = primaryTarget;
        this.columns = columns != null ? new LinkedHashMap<>(columns) : new LinkedHashMap<>();
        this.children = children != null ? List.copyOf(children) : List.of();
        this.isChild = isChild;
    }

    public Map<String, Object> getColumns() {
        return Collections.unmodifiableMap(columns);
    }

    public Object getColumn(String name) {
        return columns.get(name);
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

        public Builder children(List<ResultRow> children) {
            this.children = children;
            return this;
        }

        public Builder asChild() {
            this.isChild = true;
            return this;
        }

        public ResultRow build() {
            return new ResultRow(primaryLabel, primaryTarget, columns, children, isChild);
        }
    }
}
