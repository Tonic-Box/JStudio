package com.tonic.ui.vm.heap.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HeapDiff {
    @Getter
    private final HeapSnapshot before;
    @Getter
    private final HeapSnapshot after;
    private final List<HeapObject> addedObjects;
    private final List<HeapObject> removedObjects;
    private final List<ModifiedObject> modifiedObjects;
    private final Map<String, ClassDiff> classDiffs;

    private HeapDiff(HeapSnapshot before, HeapSnapshot after) {
        this.before = before;
        this.after = after;
        this.addedObjects = new ArrayList<>();
        this.removedObjects = new ArrayList<>();
        this.modifiedObjects = new ArrayList<>();
        this.classDiffs = new HashMap<>();

        computeDiff();
    }

    private void computeDiff() {
        Set<Integer> beforeIds = before.getObjects().keySet();
        Set<Integer> afterIds = after.getObjects().keySet();

        Set<Integer> added = new HashSet<>(afterIds);
        added.removeAll(beforeIds);

        Set<Integer> removed = new HashSet<>(beforeIds);
        removed.removeAll(afterIds);

        Set<Integer> common = new HashSet<>(beforeIds);
        common.retainAll(afterIds);

        for (int id : added) {
            HeapObject obj = after.getObject(id);
            addedObjects.add(obj);
            getOrCreateClassDiff(obj.getClassName()).addedCount++;
        }

        for (int id : removed) {
            HeapObject obj = before.getObject(id);
            removedObjects.add(obj);
            getOrCreateClassDiff(obj.getClassName()).removedCount++;
        }

        for (int id : common) {
            HeapObject beforeObj = before.getObject(id);
            HeapObject afterObj = after.getObject(id);

            List<FieldChange> changes = compareFields(beforeObj, afterObj);
            if (!changes.isEmpty()) {
                modifiedObjects.add(new ModifiedObject(beforeObj, afterObj, changes));
                getOrCreateClassDiff(afterObj.getClassName()).modifiedCount++;
            }
        }
    }

    private ClassDiff getOrCreateClassDiff(String className) {
        return classDiffs.computeIfAbsent(className, k -> new ClassDiff(className));
    }

    private List<FieldChange> compareFields(HeapObject before, HeapObject after) {
        List<FieldChange> changes = new ArrayList<>();

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(before.getFields().keySet());
        allKeys.addAll(after.getFields().keySet());

        for (String key : allKeys) {
            FieldValue beforeVal = before.getFields().get(key);
            FieldValue afterVal = after.getFields().get(key);

            if (beforeVal == null && afterVal != null) {
                changes.add(new FieldChange(key, null, afterVal, FieldChange.ChangeType.ADDED));
            } else if (beforeVal != null && afterVal == null) {
                changes.add(new FieldChange(key, beforeVal, null, FieldChange.ChangeType.REMOVED));
            } else if (beforeVal != null) {
                if (!valuesEqual(beforeVal.getValue(), afterVal.getValue())) {
                    changes.add(new FieldChange(key, beforeVal, afterVal, FieldChange.ChangeType.MODIFIED));
                }
            }
        }

        return changes;
    }

    private boolean valuesEqual(Object a, Object b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    public static HeapDiff compare(HeapSnapshot before, HeapSnapshot after) {
        return new HeapDiff(before, after);
    }

    public List<HeapObject> getAddedObjects() {
        return Collections.unmodifiableList(addedObjects);
    }

    public List<HeapObject> getRemovedObjects() {
        return Collections.unmodifiableList(removedObjects);
    }

    public List<ModifiedObject> getModifiedObjects() {
        return Collections.unmodifiableList(modifiedObjects);
    }

    public Map<String, ClassDiff> getClassDiffs() {
        return Collections.unmodifiableMap(classDiffs);
    }

    public int getTotalAdded() {
        return addedObjects.size();
    }

    public int getTotalRemoved() {
        return removedObjects.size();
    }

    public int getTotalModified() {
        return modifiedObjects.size();
    }

    public boolean hasChanges() {
        return !addedObjects.isEmpty() || !removedObjects.isEmpty() || !modifiedObjects.isEmpty();
    }

    @Override
    public String toString() {
        return "HeapDiff{" +
            "added=" + addedObjects.size() +
            ", removed=" + removedObjects.size() +
            ", modified=" + modifiedObjects.size() +
            '}';
    }

    @Getter
    public static class ModifiedObject {
        private final HeapObject before;
        private final HeapObject after;
        private final List<FieldChange> fieldChanges;

        public ModifiedObject(HeapObject before, HeapObject after, List<FieldChange> fieldChanges) {
            this.before = before;
            this.after = after;
            this.fieldChanges = List.copyOf(fieldChanges);
        }

        public int getObjectId() {
            return after.getId();
        }

        public String getClassName() {
            return after.getClassName();
        }
    }

    @Getter
    public static class FieldChange {
        public enum ChangeType {
            ADDED, REMOVED, MODIFIED
        }

        private final String fieldKey;
        private final FieldValue beforeValue;
        private final FieldValue afterValue;
        private final ChangeType changeType;

        public FieldChange(String fieldKey, FieldValue beforeValue, FieldValue afterValue, ChangeType changeType) {
            this.fieldKey = fieldKey;
            this.beforeValue = beforeValue;
            this.afterValue = afterValue;
            this.changeType = changeType;
        }

        public String getFieldName() {
            if (afterValue != null) return afterValue.getName();
            if (beforeValue != null) return beforeValue.getName();
            int lastDot = fieldKey.lastIndexOf('.');
            int lastColon = fieldKey.lastIndexOf(':');
            if (lastDot >= 0 && lastColon > lastDot) {
                return fieldKey.substring(lastDot + 1, lastColon);
            }
            return fieldKey;
        }

        @Override
        public String toString() {
            switch (changeType) {
                case ADDED:
                    return "+ " + getFieldName() + " = " + afterValue.getDisplayValue();
                case REMOVED:
                    return "- " + getFieldName();
                case MODIFIED:
                    return "~ " + getFieldName() + ": " +
                           beforeValue.getDisplayValue() + " -> " + afterValue.getDisplayValue();
                default:
                    return fieldKey;
            }
        }
    }

    @Getter
    public static class ClassDiff {
        private final String className;
        private int addedCount;
        private int removedCount;
        private int modifiedCount;

        public ClassDiff(String className) {
            this.className = className;
        }

        public int getNetChange() {
            return addedCount - removedCount;
        }

        public String getNetChangeString() {
            int net = getNetChange();
            if (net > 0) return "+" + net;
            if (net < 0) return String.valueOf(net);
            return "0";
        }
    }
}
