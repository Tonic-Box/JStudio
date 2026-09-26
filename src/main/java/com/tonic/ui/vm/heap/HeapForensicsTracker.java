package com.tonic.ui.vm.heap;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.HeapManager;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.ui.vm.heap.model.*;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/** The record of one execution's heap activity: every allocation and field write, the objects still reachable by id, and the snapshots taken; thread-safe. */
public class HeapForensicsTracker
{

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
    private LongSupplier instructionCounter;
    @Getter
    private boolean tracking = true;

    private final List<ForensicsEventListener> listeners;

    /**
     * Creates an empty tracker with tracking on.
     *
     * @param heapManager the heap the traced execution allocates in
     */
    public HeapForensicsTracker(HeapManager heapManager)
    {
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

    /**
     * Sets where the live instruction count of a running execution is read from, so snapshots taken mid-run are stamped correctly.
     *
     * @param instructionCounter the live count, or null to use the count recorded at the last execution end
     */
    public void setInstructionCounter(LongSupplier instructionCounter)
    {
        this.instructionCounter = instructionCounter;
    }

    /** Resets the current instruction count for a new run. */
    public void onExecutionStart()
    {
        lastInstructionCount = 0;
    }

    /**
     * Records the final instruction count and notifies listeners that execution ended.
     *
     * @param instructionCount the instructions executed
     */
    public void onExecutionEnd(long instructionCount)
    {
        lastInstructionCount = instructionCount;
        fireExecutionEnded(instructionCount);
    }

    /**
     * Records an allocation and starts tracking the new object; ignored while tracking is off.
     *
     * @param event the allocation
     * @param instance the allocated object
     */
    public void recordAllocation(AllocationEvent event, ObjectInstance instance)
    {
        if (!tracking) return;

        allocations.add(event);
        liveObjects.put(event.getObjectId(), instance);
        allocationTimes.put(event.getObjectId(), event.getInstructionCount());
        objectClassNames.put(event.getObjectId(), event.getClassName());
        objectFields.put(event.getObjectId(), new ConcurrentHashMap<>());

        if (event.getProvenance() != null)
        {
            provenanceMap.put(event.getObjectId(), event.getProvenance());
        }

        fireAllocationRecorded(event);
    }

    /**
     * Records a field write and updates the written object's captured field value; ignored while tracking is off.
     *
     * @param event the write
     */
    public void recordMutation(MutationEvent event)
    {
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

    /**
     * Captures every tracked object with its recorded fields and mutations, keeps the snapshot and notifies listeners.
     *
     * @param label the snapshot's name
     * @return the snapshot, stamped with the current instruction count
     */
    public HeapSnapshot takeSnapshot(String label)
    {
        HeapSnapshot.Builder builder = HeapSnapshot.builder()
                .label(label)
                .instructionCount(getCurrentInstructionCount());

        for (Map.Entry<Integer, ObjectInstance> entry : liveObjects.entrySet())
        {
            int id = entry.getKey();
            ObjectInstance instance = entry.getValue();
            long allocTime = allocationTimes.getOrDefault(id, 0L);
            ProvenanceInfo prov = provenanceMap.get(id);
            List<MutationEvent> objMutations = objectMutations.getOrDefault(id, Collections.emptyList());

            HeapObject heapObj;
            if (instance instanceof ArrayInstance)
            {
                heapObj = HeapArray.fromArrayInstance((ArrayInstance) instance, allocTime, prov, objMutations);
            }
            else
            {
                heapObj = createHeapObject(instance, allocTime, prov, objMutations);
            }

            builder.addObject(heapObj);
        }

        HeapSnapshot snapshot = builder.build();
        snapshots.add(snapshot);

        fireSnapshotTaken(snapshot);
        return snapshot;
    }

    private HeapObject createHeapObject(ObjectInstance instance, long allocTime, ProvenanceInfo prov, List<MutationEvent> mutations)
    {
        HeapObject.Builder builder = HeapObject.builder()
                .id(instance.getId())
                .className(instance.getClassName())
                .allocationTime(allocTime)
                .provenance(prov)
                .mutations(mutations)
                .isArray(instance instanceof ArrayInstance);

        Map<String, FieldValue> fields = objectFields.get(instance.getId());
        if (fields != null)
        {
            for (FieldValue fv : fields.values())
            {
                builder.addField(fv);
            }
        }

        return builder.build();
    }

    /**
     * Compares two snapshots.
     *
     * @param before the earlier snapshot
     * @param after the later snapshot
     * @return the diff
     */
    public HeapDiff compareSnapshots(HeapSnapshot before, HeapSnapshot after)
    {
        return HeapDiff.compare(before, after);
    }

    /**
     * Captures the tracked objects of one class.
     *
     * @param className the class's internal name
     * @return the objects, possibly empty
     */
    public List<HeapObject> getObjectsByClass(String className)
    {
        List<HeapObject> result = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : objectClassNames.entrySet())
        {
            if (className.equals(entry.getValue()))
            {
                int id = entry.getKey();
                ObjectInstance instance = liveObjects.get(id);
                if (instance == null) continue;

                long allocTime = allocationTimes.getOrDefault(id, 0L);
                ProvenanceInfo prov = provenanceMap.get(id);
                List<MutationEvent> objMutations = objectMutations.getOrDefault(id, Collections.emptyList());

                if (instance instanceof ArrayInstance)
                {
                    result.add(HeapArray.fromArrayInstance((ArrayInstance) instance, allocTime, prov, objMutations));
                }
                else
                {
                    result.add(createHeapObject(instance, allocTime, prov, objMutations));
                }
            }
        }
        return result;
    }

    /**
     * Counts the tracked objects per class.
     *
     * @return a new map from class name to object count
     */
    public Map<String, Integer> getClassCounts()
    {
        Map<String, Integer> counts = new HashMap<>();
        for (String className : objectClassNames.values())
        {
            counts.merge(className, 1, Integer::sum);
        }
        return counts;
    }

    /**
     * Lists the tracked classes, most objects first.
     *
     * @return the class names sorted by descending object count
     */
    public List<String> getClassesSortedByCount()
    {
        return getClassCounts().entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Lists the allocations within an instruction count range.
     *
     * @param start the first instruction count, inclusive
     * @param end the last instruction count, inclusive
     * @return the allocations in the range
     */
    public List<AllocationEvent> getAllocationsInRange(long start, long end)
    {
        return allocations.stream()
                .filter(e -> e.getInstructionCount() >= start && e.getInstructionCount() <= end)
                .collect(Collectors.toList());
    }

    /**
     * Lists the allocations of one class.
     *
     * @param className the class's internal name
     * @return the allocations, possibly empty
     */
    public List<AllocationEvent> getAllocationsForClass(String className)
    {
        return allocations.stream()
                .filter(e -> className.equals(e.getClassName()))
                .collect(Collectors.toList());
    }

    /**
     * Lists the writes to one object.
     *
     * @param objectId the object's heap id, or -1 for static fields
     * @return the writes in record order, unmodifiable, empty when none were recorded
     */
    public List<MutationEvent> getMutationsForObject(int objectId)
    {
        return Collections.unmodifiableList(objectMutations.getOrDefault(objectId, Collections.emptyList()));
    }

    /**
     * Lists the writes within an instruction count range.
     *
     * @param start the first instruction count, inclusive
     * @param end the last instruction count, inclusive
     * @return the writes in the range
     */
    public List<MutationEvent> getMutationsInRange(long start, long end)
    {
        return mutations.stream()
                .filter(e -> e.getInstructionCount() >= start && e.getInstructionCount() <= end)
                .collect(Collectors.toList());
    }

    /**
     * Merges allocations, writes and snapshots into one timeline.
     *
     * @return the events sorted by instruction count
     */
    public List<TimelineEvent> getTimeline()
    {
        List<TimelineEvent> events = new ArrayList<>();

        for (AllocationEvent alloc : allocations)
        {
            events.add(TimelineEvent.allocation(alloc));
        }

        for (MutationEvent mut : mutations)
        {
            events.add(TimelineEvent.mutation(mut));
        }

        for (HeapSnapshot snap : snapshots)
        {
            events.add(TimelineEvent.snapshot(snap.getInstructionCount(), snap.getLabel(), snap));
        }

        events.sort(Comparator.comparingLong(TimelineEvent::getInstructionCount));
        return events;
    }

    /**
     * Counts the tracked objects.
     *
     * @return the number of objects allocated and not cleared by reset
     */
    public int getTotalObjectCount()
    {
        return liveObjects.size();
    }

    /**
     * Counts the recorded allocations.
     *
     * @return the number of allocations
     */
    public int getTotalAllocationCount()
    {
        return allocations.size();
    }

    /**
     * Counts the recorded field writes.
     *
     * @return the number of writes
     */
    public int getTotalMutationCount()
    {
        return mutations.size();
    }

    /**
     * Reads the current instruction count.
     *
     * @return the live count when a counter is set, otherwise the count recorded at the last execution end
     */
    public long getCurrentInstructionCount()
    {
        return instructionCounter != null ? instructionCounter.getAsLong() : lastInstructionCount;
    }

    /**
     * Lists every recorded allocation.
     *
     * @return the allocations in record order, unmodifiable
     */
    public List<AllocationEvent> getAllocations()
    {
        return Collections.unmodifiableList(allocations);
    }

    /**
     * Lists every recorded field write.
     *
     * @return the writes in record order, unmodifiable
     */
    public List<MutationEvent> getMutations()
    {
        return Collections.unmodifiableList(mutations);
    }

    /**
     * Lists every snapshot taken.
     *
     * @return the snapshots in order taken, unmodifiable
     */
    public List<HeapSnapshot> getSnapshots()
    {
        return Collections.unmodifiableList(snapshots);
    }

    /**
     * Gets the most recent snapshot.
     *
     * @return the last snapshot, or null when none has been taken
     */
    public HeapSnapshot getLatestSnapshot()
    {
        return snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1);
    }

    /**
     * Looks up a tracked object.
     *
     * @param objectId the object's heap id
     * @return the live object, or null when not tracked
     */
    public ObjectInstance getLiveObject(int objectId)
    {
        return liveObjects.get(objectId);
    }

    /**
     * Looks up where a tracked object was allocated.
     *
     * @param objectId the object's heap id
     * @return the allocation site, or null when unknown
     */
    public ProvenanceInfo getProvenance(int objectId)
    {
        return provenanceMap.get(objectId);
    }

    /**
     * Turns recording of allocations and writes on or off.
     *
     * @param tracking true to record
     */
    public void setTracking(boolean tracking)
    {
        this.tracking = tracking;
    }

    /** Discards all recorded events, objects and snapshots; listeners and the tracking flag are kept. */
    public void reset()
    {
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

    /**
     * Registers a listener for recorded events.
     *
     * @param listener the listener
     */
    public void addListener(ForensicsEventListener listener)
    {
        listeners.add(listener);
    }

    /**
     * Unregisters a listener.
     *
     * @param listener the listener
     */
    public void removeListener(ForensicsEventListener listener)
    {
        listeners.remove(listener);
    }

    private void fireAllocationRecorded(AllocationEvent event)
    {
        for (ForensicsEventListener listener : listeners)
        {
            try
            {
                listener.onAllocationRecorded(event);
            }
            catch (Exception ignored)
            {
            }
        }
    }

    private void fireMutationRecorded(MutationEvent event)
    {
        for (ForensicsEventListener listener : listeners)
        {
            try
            {
                listener.onMutationRecorded(event);
            }
            catch (Exception ignored)
            {
            }
        }
    }

    private void fireSnapshotTaken(HeapSnapshot snapshot)
    {
        for (ForensicsEventListener listener : listeners)
        {
            try
            {
                listener.onSnapshotTaken(snapshot);
            }
            catch (Exception ignored)
            {
            }
        }
    }

    private void fireExecutionEnded(long instructionCount)
    {
        for (ForensicsEventListener listener : listeners)
        {
            try
            {
                listener.onExecutionEnded(instructionCount);
            }
            catch (Exception ignored)
            {
            }
        }
    }

    /** Receives the tracker's events; exceptions it throws are swallowed. */
    public interface ForensicsEventListener
    {
        /**
         * Called after an allocation is recorded.
         *
         * @param event the allocation
         */
        default void onAllocationRecorded(AllocationEvent event)
        {
        }

        /**
         * Called after a field write is recorded.
         *
         * @param event the write
         */
        default void onMutationRecorded(MutationEvent event)
        {
        }

        /**
         * Called after a snapshot is taken.
         *
         * @param snapshot the snapshot
         */
        default void onSnapshotTaken(HeapSnapshot snapshot)
        {
        }

        /**
         * Called when the traced execution ends.
         *
         * @param instructionCount the instructions executed
         */
        default void onExecutionEnded(long instructionCount)
        {
        }
    }
}
