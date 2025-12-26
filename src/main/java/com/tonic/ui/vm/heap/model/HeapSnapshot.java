package com.tonic.ui.vm.heap.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class HeapSnapshot {
    private final int snapshotId;
    private final String label;
    private final Instant timestamp;
    private final long instructionCount;
    private final Map<Integer, HeapObject> objects;
    private final Map<String, Integer> classCounts;
    private final int totalObjects;

    private HeapSnapshot(Builder builder) {
        this.snapshotId = builder.snapshotId;
        this.label = builder.label;
        this.timestamp = builder.timestamp;
        this.instructionCount = builder.instructionCount;
        this.objects = Collections.unmodifiableMap(new LinkedHashMap<>(builder.objects));
        this.classCounts = computeClassCounts(this.objects);
        this.totalObjects = this.objects.size();
    }

    private Map<String, Integer> computeClassCounts(Map<Integer, HeapObject> objs) {
        Map<String, Integer> counts = new HashMap<>();
        for (HeapObject obj : objs.values()) {
            counts.merge(obj.getClassName(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    public int getSnapshotId() {
        return snapshotId;
    }

    public String getLabel() {
        return label;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public long getInstructionCount() {
        return instructionCount;
    }

    public Map<Integer, HeapObject> getObjects() {
        return objects;
    }

    public HeapObject getObject(int id) {
        return objects.get(id);
    }

    public Map<String, Integer> getClassCounts() {
        return classCounts;
    }

    public int getTotalObjects() {
        return totalObjects;
    }

    public List<HeapObject> getObjectsByClass(String className) {
        return objects.values().stream()
            .filter(obj -> className.equals(obj.getClassName()))
            .collect(Collectors.toList());
    }

    public List<String> getClassesSortedByCount() {
        return classCounts.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    public int getClassCount(String className) {
        return classCounts.getOrDefault(className, 0);
    }

    public List<HeapObject> getStrings() {
        return getObjectsByClass("java/lang/String");
    }

    public List<HeapObject> getLambdas() {
        return objects.values().stream()
            .filter(HeapObject::isLambda)
            .collect(Collectors.toList());
    }

    public List<HeapObject> getArrays() {
        return objects.values().stream()
            .filter(HeapObject::isArray)
            .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "HeapSnapshot{" +
            "id=" + snapshotId +
            ", label='" + label + '\'' +
            ", objects=" + totalObjects +
            ", classes=" + classCounts.size() +
            ", at=" + instructionCount +
            '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private static int nextSnapshotId = 1;

        private int snapshotId;
        private String label = "";
        private Instant timestamp = Instant.now();
        private long instructionCount;
        private Map<Integer, HeapObject> objects = new LinkedHashMap<>();

        public Builder() {
            this.snapshotId = nextSnapshotId++;
        }

        public Builder snapshotId(int snapshotId) {
            this.snapshotId = snapshotId;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder instructionCount(long instructionCount) {
            this.instructionCount = instructionCount;
            return this;
        }

        public Builder objects(Map<Integer, HeapObject> objects) {
            this.objects = objects != null ? objects : new LinkedHashMap<>();
            return this;
        }

        public Builder addObject(HeapObject object) {
            this.objects.put(object.getId(), object);
            return this;
        }

        public HeapSnapshot build() {
            return new HeapSnapshot(this);
        }
    }
}
