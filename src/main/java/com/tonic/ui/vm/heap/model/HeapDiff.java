package com.tonic.ui.vm.heap.model;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The difference between two heap snapshots: objects added, removed and modified, with per-class counts; computed on creation. */
public class HeapDiff
{
    @Getter
    private final HeapSnapshot before;
    @Getter
    private final HeapSnapshot after;
    private final List<HeapObject> addedObjects;
    private final List<HeapObject> removedObjects;
    private final List<ModifiedObject> modifiedObjects;
    private final Map<String, ClassDiff> classDiffs;

    private HeapDiff(HeapSnapshot before, HeapSnapshot after)
    {
        this.before = before;
        this.after = after;
        this.addedObjects = new ArrayList<>();
        this.removedObjects = new ArrayList<>();
        this.modifiedObjects = new ArrayList<>();
        this.classDiffs = new HashMap<>();

        computeDiff();
    }

    private void computeDiff()
    {
        Set<Integer> beforeIds = before.getObjects().keySet();
        Set<Integer> afterIds = after.getObjects().keySet();

        Set<Integer> added = new HashSet<>(afterIds);
        added.removeAll(beforeIds);

        Set<Integer> removed = new HashSet<>(beforeIds);
        removed.removeAll(afterIds);

        Set<Integer> common = new HashSet<>(beforeIds);
        common.retainAll(afterIds);

        for (int id : added)
        {
            HeapObject obj = after.getObject(id);
            addedObjects.add(obj);
            getOrCreateClassDiff(obj.getClassName()).addedCount++;
        }

        for (int id : removed)
        {
            HeapObject obj = before.getObject(id);
            removedObjects.add(obj);
            getOrCreateClassDiff(obj.getClassName()).removedCount++;
        }

        for (int id : common)
        {
            HeapObject beforeObj = before.getObject(id);
            HeapObject afterObj = after.getObject(id);

            List<FieldChange> changes = compareFields(beforeObj, afterObj);
            if (!changes.isEmpty())
            {
                modifiedObjects.add(new ModifiedObject(beforeObj, afterObj, changes));
                getOrCreateClassDiff(afterObj.getClassName()).modifiedCount++;
            }
        }
    }

    private ClassDiff getOrCreateClassDiff(String className)
    {
        return classDiffs.computeIfAbsent(className, k -> new ClassDiff(className));
    }

    private List<FieldChange> compareFields(HeapObject before, HeapObject after)
    {
        List<FieldChange> changes = new ArrayList<>();

        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(before.getFields().keySet());
        allKeys.addAll(after.getFields().keySet());

        for (String key : allKeys)
        {
            FieldValue beforeVal = before.getFields().get(key);
            FieldValue afterVal = after.getFields().get(key);

            if (beforeVal == null && afterVal != null)
            {
                changes.add(new FieldChange(key, null, afterVal, FieldChange.ChangeType.ADDED));
            }
            else if (beforeVal != null && afterVal == null)
            {
                changes.add(new FieldChange(key, beforeVal, null, FieldChange.ChangeType.REMOVED));
            }
            else if (beforeVal != null)
            {
                if (!valuesEqual(beforeVal.getValue(), afterVal.getValue()))
                {
                    changes.add(new FieldChange(key, beforeVal, afterVal, FieldChange.ChangeType.MODIFIED));
                }
            }
        }

        return changes;
    }

    private boolean valuesEqual(Object a, Object b)
    {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /**
     * Compares two snapshots by object id and field values.
     *
     * @param before the earlier snapshot
     * @param after the later snapshot
     * @return the diff
     */
    public static HeapDiff compare(HeapSnapshot before, HeapSnapshot after)
    {
        return new HeapDiff(before, after);
    }

    /**
     * Lists the added objects.
     *
     * @return the objects present only in the later snapshot, unmodifiable
     */
    public List<HeapObject> getAddedObjects()
    {
        return Collections.unmodifiableList(addedObjects);
    }

    /**
     * Lists the removed objects.
     *
     * @return the objects present only in the earlier snapshot, unmodifiable
     */
    public List<HeapObject> getRemovedObjects()
    {
        return Collections.unmodifiableList(removedObjects);
    }

    /**
     * Lists the modified objects.
     *
     * @return the objects present in both snapshots whose fields differ, unmodifiable
     */
    public List<ModifiedObject> getModifiedObjects()
    {
        return Collections.unmodifiableList(modifiedObjects);
    }

    /**
     * Maps each class to its change counts.
     *
     * @return the per-class change counts keyed by class name, unmodifiable
     */
    public Map<String, ClassDiff> getClassDiffs()
    {
        return Collections.unmodifiableMap(classDiffs);
    }

    /**
     * Counts the added objects.
     *
     * @return the number of added objects
     */
    public int getTotalAdded()
    {
        return addedObjects.size();
    }

    /**
     * Counts the removed objects.
     *
     * @return the number of removed objects
     */
    public int getTotalRemoved()
    {
        return removedObjects.size();
    }

    /**
     * Counts the modified objects.
     *
     * @return the number of modified objects
     */
    public int getTotalModified()
    {
        return modifiedObjects.size();
    }

    /**
     * Tells whether anything was added, removed or modified.
     *
     * @return true when the diff is not empty
     */
    public boolean hasChanges()
    {
        return !addedObjects.isEmpty() || !removedObjects.isEmpty() || !modifiedObjects.isEmpty();
    }

    @Override
    public String toString()
    {
        return "HeapDiff{" +
                "added=" + addedObjects.size() +
                ", removed=" + removedObjects.size() +
                ", modified=" + modifiedObjects.size() +
                '}';
    }

    /** An object present in both snapshots with the field changes between them. */
    @Getter
    public static class ModifiedObject
    {
        private final HeapObject before;
        private final HeapObject after;
        private final List<FieldChange> fieldChanges;

        /**
         * Creates a modified object entry.
         *
         * @param before the object in the earlier snapshot
         * @param after the object in the later snapshot
         * @param fieldChanges the changed fields; copied
         */
        public ModifiedObject(HeapObject before, HeapObject after, List<FieldChange> fieldChanges)
        {
            this.before = before;
            this.after = after;
            this.fieldChanges = List.copyOf(fieldChanges);
        }

        /**
         * Gives the object's id.
         *
         * @return the object's id, taken from the later snapshot
         */
        public int getObjectId()
        {
            return after.getId();
        }

        /**
         * Gives the object's class.
         *
         * @return the object's class name, taken from the later snapshot
         */
        public String getClassName()
        {
            return after.getClassName();
        }
    }

    /** One field that was added, removed or given a new value between two snapshots. */
    @Getter
    public static class FieldChange
    {
        /** How a field changed. */
        public enum ChangeType
        {
            ADDED, REMOVED, MODIFIED
        }

        private final String fieldKey;
        private final FieldValue beforeValue;
        private final FieldValue afterValue;
        private final ChangeType changeType;

        /**
         * Creates a field change.
         *
         * @param fieldKey the field's owner.name:descriptor key
         * @param beforeValue the earlier value, or null when the field was added
         * @param afterValue the later value, or null when the field was removed
         * @param changeType how the field changed
         */
        public FieldChange(String fieldKey, FieldValue beforeValue, FieldValue afterValue, ChangeType changeType)
        {
            this.fieldKey = fieldKey;
            this.beforeValue = beforeValue;
            this.afterValue = afterValue;
            this.changeType = changeType;
        }

        /**
         * Finds the field's simple name.
         *
         * @return the name from whichever value exists, else parsed from the key
         */
        public String getFieldName()
        {
            if (afterValue != null) return afterValue.getName();
            if (beforeValue != null) return beforeValue.getName();
            int lastDot = fieldKey.lastIndexOf('.');
            int lastColon = fieldKey.lastIndexOf(':');
            if (lastDot >= 0 && lastColon > lastDot)
            {
                return fieldKey.substring(lastDot + 1, lastColon);
            }
            return fieldKey;
        }

        @Override
        public String toString()
        {
            switch (changeType)
            {
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

    /** Per-class counts of objects added, removed and modified between two snapshots. */
    @Getter
    public static class ClassDiff
    {
        private final String className;
        private int addedCount;
        private int removedCount;
        private int modifiedCount;

        /**
         * Creates an empty class diff.
         *
         * @param className the class these counts are for
         */
        public ClassDiff(String className)
        {
            this.className = className;
        }

        /**
         * Computes the net object count change.
         *
         * @return added minus removed
         */
        public int getNetChange()
        {
            return addedCount - removedCount;
        }

        /**
         * Formats the net change with its sign.
         *
         * @return the net change, prefixed with + when positive
         */
        public String getNetChangeString()
        {
            int net = getNetChange();
            if (net > 0) return "+" + net;
            if (net < 0) return String.valueOf(net);
            return "0";
        }
    }
}
