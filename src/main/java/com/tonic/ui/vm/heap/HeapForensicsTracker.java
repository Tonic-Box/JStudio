package com.tonic.ui.vm.heap;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.ui.vm.heap.model.*;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class HeapForensicsTracker {

    @Getter
    private final HeapManager heapManager;
    private final List<AllocationEvent> allocations;
    private final List<MutationEvent> mutations;
    private final List<HeapSnapshot> snapshots;
    private final Map<Integer, ObjectInstance> liveObjects;
    private final Map<Integer, Long> allocationTimes;
    private final Map<Integer, String> objectClassNames;
    private final Map<Integer, ProvenanceInfo> provenanceMap;
    private final Map<Integer, List<MutationEvent>> objectMutations;
    private final Map<Integer, Map<String, FieldValue>> objectFields;

    private long lastInstructionCount;
    @Getter
    private boolean tracking = true;

    private final List<ForensicsEventListener> listeners;

    public HeapForensicsTracker(HeapManager heapManager) {
        this.heapManager = heapManager;
        this.allocations = new CopyOnWriteArrayList<>();
        this.mutations = new CopyOnWriteArrayList<>();
        this.snapshots = new CopyOnWriteArrayList<>();
        this.liveObjects = new ConcurrentHashMap<>();
        this.allocationTimes = new ConcurrentHashMap<>();
        this.objectClassNames = new ConcurrentHashMap<>();
        this.provenanceMap = new ConcurrentHashMap<>();
        this.objectMutations = new ConcurrentHashMap<>();
        this.objectFields = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    public void onExecutionStart() {
        lastInstructionCount = 0;
    }

    public void onExecutionEnd(long instructionCount) {
        lastInstructionCount = instructionCount;
        fireExecutionEnded(instructionCount);
    }

    public void recordAllocation(AllocationEvent event, ObjectInstance instance) {
        if (!tracking) return;

        allocations.add(event);
        liveObjects.put(event.getObjectId(), instance);
        allocationTimes.put(event.getObjectId(), event.getInstructionCount());
        objectClassNames.put(event.getObjectId(), event.getClassName());
        objectFields.put(event.getObjectId(), new ConcurrentHashMap<>());

        if (event.getProvenance() != null) {
            provenanceMap.put(event.getObjectId(), event.getProvenance());
        }

        fireAllocationRecorded(event);
    }

    public void recordMutation(MutationEvent event) {
        if (!tracking) return;

        mutations.add(event);
        objectMutations.computeIfAbsent(event.getObjectId(), k -> new CopyOnWriteArrayList<>()).add(event);

        String fieldKey = event.getFieldOwner() + "." + event.getFieldName() + ":" + event.getFieldDescriptor();
        FieldValue fv = FieldValue.builder()
            .owner(event.getFieldOwner())
            .name(event.getFieldName())
            .descriptor(event.getFieldDescriptor())
            .value(event.getNewValue())
            .build();

        objectFields.computeIfAbsent(event.getObjectId(), k -> new ConcurrentHashMap<>()).put(fieldKey, fv);

        fireMutationRecorded(event);
    }

    public HeapSnapshot takeSnapshot(String label) {
        HeapSnapshot.Builder builder = HeapSnapshot.builder()
            .label(label)
            .instructionCount(lastInstructionCount);

        for (Map.Entry<Integer, ObjectInstance> entry : liveObjects.entrySet()) {
            int id = entry.getKey();
            ObjectInstance instance = entry.getValue();
            long allocTime = allocationTimes.getOrDefault(id, 0L);
            ProvenanceInfo prov = provenanceMap.get(id);
            List<MutationEvent> objMutations = objectMutations.getOrDefault(id, Collections.emptyList());

            HeapObject heapObj;
            if (instance instanceof ArrayInstance) {
                heapObj = HeapArray.fromArrayInstance((ArrayInstance) instance, allocTime, prov, objMutations);
            } else {
                heapObj = createHeapObject(instance, allocTime, prov, objMutations);
            }

            builder.addObject(heapObj);
        }

        HeapSnapshot snapshot = builder.build();
        snapshots.add(snapshot);

        fireSnapshotTaken(snapshot);
        return snapshot;
    }

    private HeapObject createHeapObject(ObjectInstance instance, long allocTime,
                                         ProvenanceInfo prov, List<MutationEvent> mutations) {
        HeapObject.Builder builder = HeapObject.builder()
            .id(instance.getId())
            .className(instance.getClassName())
            .allocationTime(allocTime)
            .provenance(prov)
            .mutations(mutations)
            .isArray(instance instanceof ArrayInstance);

        Map<String, FieldValue> fields = objectFields.get(instance.getId());
        if (fields != null) {
            for (FieldValue fv : fields.values()) {
                builder.addField(fv);
            }
        }

        return builder.build();
    }

    public HeapDiff compareSnapshots(HeapSnapshot before, HeapSnapshot after) {
        return HeapDiff.compare(before, after);
    }

    public List<HeapObject> getObjectsByClass(String className) {
        List<HeapObject> result = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : objectClassNames.entrySet()) {
            if (className.equals(entry.getValue())) {
                int id = entry.getKey();
                ObjectInstance instance = liveObjects.get(id);
                if (instance == null) continue;

                long allocTime = allocationTimes.getOrDefault(id, 0L);
                ProvenanceInfo prov = provenanceMap.get(id);
                List<MutationEvent> objMutations = objectMutations.getOrDefault(id, Collections.emptyList());

                if (instance instanceof ArrayInstance) {
                    result.add(HeapArray.fromArrayInstance((ArrayInstance) instance, allocTime, prov, objMutations));
                } else {
                    result.add(createHeapObject(instance, allocTime, prov, objMutations));
                }
            }
        }
        return result;
    }

    public Map<String, Integer> getClassCounts() {
        Map<String, Integer> counts = new HashMap<>();
        for (String className : objectClassNames.values()) {
            counts.merge(className, 1, Integer::sum);
        }
        return counts;
    }

    public List<String> getClassesSortedByCount() {
        return getClassCounts().entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toList());
    }

    public List<AllocationEvent> getAllocationsInRange(long start, long end) {
        return allocations.stream()
            .filter(e -> e.getInstructionCount() >= start && e.getInstructionCount() <= end)
            .collect(Collectors.toList());
    }

    public List<AllocationEvent> getAllocationsForClass(String className) {
        return allocations.stream()
            .filter(e -> className.equals(e.getClassName()))
            .collect(Collectors.toList());
    }

    public List<MutationEvent> getMutationsForObject(int objectId) {
        return objectMutations.getOrDefault(objectId, Collections.emptyList());
    }

    public List<MutationEvent> getMutationsInRange(long start, long end) {
        return mutations.stream()
            .filter(e -> e.getInstructionCount() >= start && e.getInstructionCount() <= end)
            .collect(Collectors.toList());
    }

    public List<TimelineEvent> getTimeline() {
        List<TimelineEvent> events = new ArrayList<>();

        for (AllocationEvent alloc : allocations) {
            events.add(TimelineEvent.allocation(alloc));
        }

        for (MutationEvent mut : mutations) {
            events.add(TimelineEvent.mutation(mut));
        }

        for (HeapSnapshot snap : snapshots) {
            events.add(TimelineEvent.snapshot(snap.getInstructionCount(), snap.getLabel(), snap));
        }

        events.sort(Comparator.comparingLong(TimelineEvent::getInstructionCount));
        return events;
    }

    public int getTotalObjectCount() {
        return liveObjects.size();
    }

    public int getTotalAllocationCount() {
        return allocations.size();
    }

    public int getTotalMutationCount() {
        return mutations.size();
    }

    public long getCurrentInstructionCount() {
        return lastInstructionCount;
    }

    public List<AllocationEvent> getAllocations() {
        return Collections.unmodifiableList(allocations);
    }

    public List<MutationEvent> getMutations() {
        return Collections.unmodifiableList(mutations);
    }

    public List<HeapSnapshot> getSnapshots() {
        return Collections.unmodifiableList(snapshots);
    }

    public HeapSnapshot getLatestSnapshot() {
        return snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1);
    }

    public ObjectInstance getLiveObject(int objectId) {
        return liveObjects.get(objectId);
    }

    public ProvenanceInfo getProvenance(int objectId) {
        return provenanceMap.get(objectId);
    }

    public void setTracking(boolean tracking) {
        this.tracking = tracking;
    }

    public void reset() {
        allocations.clear();
        mutations.clear();
        snapshots.clear();
        liveObjects.clear();
        allocationTimes.clear();
        objectClassNames.clear();
        provenanceMap.clear();
        objectMutations.clear();
        objectFields.clear();
        lastInstructionCount = 0;
    }

    public void addListener(ForensicsEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ForensicsEventListener listener) {
        listeners.remove(listener);
    }

    private void fireAllocationRecorded(AllocationEvent event) {
        for (ForensicsEventListener listener : listeners) {
            try {
                listener.onAllocationRecorded(event);
            } catch (Exception ignored) {
            }
        }
    }

    private void fireMutationRecorded(MutationEvent event) {
        for (ForensicsEventListener listener : listeners) {
            try {
                listener.onMutationRecorded(event);
            } catch (Exception ignored) {
            }
        }
    }

    private void fireSnapshotTaken(HeapSnapshot snapshot) {
        for (ForensicsEventListener listener : listeners) {
            try {
                listener.onSnapshotTaken(snapshot);
            } catch (Exception ignored) {
            }
        }
    }

    private void fireExecutionEnded(long instructionCount) {
        for (ForensicsEventListener listener : listeners) {
            try {
                listener.onExecutionEnded(instructionCount);
            } catch (Exception ignored) {
            }
        }
    }

    public interface ForensicsEventListener {
        default void onAllocationRecorded(AllocationEvent event) {}
        default void onMutationRecorded(MutationEvent event) {}
        default void onSnapshotTaken(HeapSnapshot snapshot) {}
        default void onExecutionEnded(long instructionCount) {}
    }
}
