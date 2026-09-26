package com.tonic.ui.vm.heap.model;

import lombok.Getter;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** A labelled capture of every live heap object at one instruction count, with per-class counts. */
@Getter
public class HeapSnapshot
{
    private final int snapshotId;
    private final String label;
    private final Instant timestamp;
    private final long instructionCount;
    private final Map<Integer, HeapObject> objects;
    private final Map<String, Integer> classCounts;
    private final int totalObjects;

    private HeapSnapshot(Builder builder)
    {
        this.snapshotId = builder.snapshotId;
        this.label = builder.label;
        this.timestamp = builder.timestamp;
        this.instructionCount = builder.instructionCount;
        this.objects = Collections.unmodifiableMap(new LinkedHashMap<>(builder.objects));
        this.classCounts = computeClassCounts(this.objects);
        this.totalObjects = this.objects.size();
    }

    private Map<String, Integer> computeClassCounts(Map<Integer, HeapObject> objs)
    {
        Map<String, Integer> counts = new HashMap<>();
        for (HeapObject obj : objs.values())
        {
            counts.merge(obj.getClassName(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    /**
     * Looks up an object by id.
     *
     * @param id the heap id
     * @return the object, or null when not in this snapshot
     */
    public HeapObject getObject(int id)
    {
        return objects.get(id);
    }

    /**
     * Lists the objects of one class.
     *
     * @param className the class's internal name
     * @return the matching objects, possibly empty
     */
    public List<HeapObject> getObjectsByClass(String className)
    {
        return objects.values().stream()
                .filter(obj -> className.equals(obj.getClassName()))
                .collect(Collectors.toList());
    }

    /**
     * Lists the classes, most instances first.
     *
     * @return the class names sorted by descending instance count
     */
    public List<String> getClassesSortedByCount()
    {
        return classCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Counts the instances of one class.
     *
     * @param className the class's internal name
     * @return the instance count, 0 when absent
     */
    public int getClassCount(String className)
    {
        return classCounts.getOrDefault(className, 0);
    }

    /**
     * Lists the string objects.
     *
     * @return the java/lang/String instances
     */
    public List<HeapObject> getStrings()
    {
        return getObjectsByClass("java/lang/String");
    }

    /**
     * Lists the lambda objects.
     *
     * @return the objects whose class is a generated lambda class
     */
    public List<HeapObject> getLambdas()
    {
        return objects.values().stream()
                .filter(HeapObject::isLambda)
                .collect(Collectors.toList());
    }

    /**
     * Lists the arrays.
     *
     * @return the objects marked as arrays
     */
    public List<HeapObject> getArrays()
    {
        return objects.values().stream()
                .filter(HeapObject::isArray)
                .collect(Collectors.toList());
    }

    @Override
    public String toString()
    {
        return "HeapSnapshot{" +
                "id=" + snapshotId +
                ", label='" + label + '\'' +
                ", objects=" + totalObjects +
                ", classes=" + classCounts.size() +
                ", at=" + instructionCount +
                '}';
    }

    /**
     * Starts a new snapshot builder with the next snapshot id.
     *
     * @return an empty builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /** Builder for heap snapshots; the timestamp defaults to creation time and the id to a process-wide counter. */
    public static class Builder
    {
        private static int nextSnapshotId = 1;

        private int snapshotId;
        private String label = "";
        private Instant timestamp = Instant.now();
        private long instructionCount;
        private Map<Integer, HeapObject> objects = new LinkedHashMap<>();

        /** Creates a builder and assigns it the next snapshot id. */
        public Builder()
        {
            this.snapshotId = nextSnapshotId++;
        }

        /**
         * Overrides the assigned snapshot id.
         *
         * @param snapshotId the snapshot id
         * @return this builder
         */
        public Builder snapshotId(int snapshotId)
        {
            this.snapshotId = snapshotId;
            return this;
        }

        /**
         * Sets the label.
         *
         * @param label the name shown for the snapshot
         * @return this builder
         */
        public Builder label(String label)
        {
            this.label = label;
            return this;
        }

        /**
         * Sets when the snapshot was taken.
         *
         * @param timestamp the capture time
         * @return this builder
         */
        public Builder timestamp(Instant timestamp)
        {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Sets where in execution the snapshot was taken.
         *
         * @param instructionCount the executed instruction count at capture
         * @return this builder
         */
        public Builder instructionCount(long instructionCount)
        {
            this.instructionCount = instructionCount;
            return this;
        }

        /**
         * Replaces the objects; the map is used directly, not copied.
         *
         * @param objects the objects keyed by id, or null for none
         * @return this builder
         */
        public Builder objects(Map<Integer, HeapObject> objects)
        {
            this.objects = objects != null ? objects : new LinkedHashMap<>();
            return this;
        }

        /**
         * Adds one object under its id.
         *
         * @param object the heap object
         * @return this builder
         */
        public Builder addObject(HeapObject object)
        {
            this.objects.put(object.getId(), object);
            return this;
        }

        /**
         * Builds the snapshot, copying the objects and counting them by class.
         *
         * @return the snapshot
         */
        public HeapSnapshot build()
        {
            return new HeapSnapshot(this);
        }
    }
}
