package com.tonic.ui.vm.heap.model;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A heap object captured in a snapshot: its id, class, allocation site, field values and recorded mutations. */
@Getter
public class HeapObject
{
    private final int id;
    private final String className;
    private final long allocationTime;
    private final ProvenanceInfo provenance;
    private final Map<String, FieldValue> fields;
    private final List<MutationEvent> mutations;
    private final boolean lambda;
    private final boolean array;
    private final boolean string;

    protected HeapObject(Builder builder)
    {
        this.id = builder.id;
        this.className = builder.className;
        this.allocationTime = builder.allocationTime;
        this.provenance = builder.provenance;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(builder.fields));
        this.mutations = List.copyOf(builder.mutations);
        this.lambda = detectLambda(className);
        this.array = builder.isArray;
        this.string = "java/lang/String".equals(className);
    }

    private boolean detectLambda(String name)
    {
        return name != null && (name.contains("$Lambda$") || name.contains("$$Lambda$"));
    }

    /**
     * Strips the package from the class name.
     *
     * @return the class name after the last slash, or null as text when there is no class name
     */
    public String getSimpleClassName()
    {
        if (className == null)
        {
            return "null";
        }
        int lastSlash = className.lastIndexOf('/');
        return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
    }

    /**
     * Looks up a captured field.
     *
     * @param key the field's owner.name:descriptor key
     * @return the field value, or null when not captured
     */
    public FieldValue getField(String key)
    {
        return fields.get(key);
    }

    /**
     * Tells whether any writes to this object were recorded.
     *
     * @return true when there is at least one mutation
     */
    public boolean hasMutations()
    {
        return !mutations.isEmpty();
    }

    /**
     * Lists the fields of a lambda that hold captured values, recognized by the arg$, capture$ and val$ name prefixes.
     *
     * @return the captured field names, or an empty list when this is not a lambda
     */
    public List<String> getLambdaCaptureFieldNames()
    {
        if (!lambda)
        {
            return Collections.emptyList();
        }
        List<String> captures = new ArrayList<>();
        for (String key : fields.keySet())
        {
            FieldValue fv = fields.get(key);
            String name = fv.getName();
            if (name.startsWith("arg$") || name.startsWith("capture$") || name.startsWith("val$"))
            {
                captures.add(name);
            }
        }
        return captures;
    }

    /**
     * Lists the ids of objects this object's fields point to.
     *
     * @return the referenced object ids, in field order
     */
    public List<Integer> getReferencedObjectIds()
    {
        List<Integer> refs = new ArrayList<>();
        for (FieldValue fv : fields.values())
        {
            if (fv.hasReferenceId())
            {
                refs.add(fv.getReferenceId());
            }
        }
        return refs;
    }

    /**
     * Captures an object instance without its field values.
     *
     * @param instance the live object
     * @param allocationTime the instruction count at which the object was allocated
     * @param provenance where the object was allocated, or null if unknown
     * @param mutations the writes recorded against the object, or null for none
     * @return the captured object
     */
    public static HeapObject fromObjectInstance(ObjectInstance instance, long allocationTime, ProvenanceInfo provenance, List<MutationEvent> mutations)
    {
        Builder builder = builder()
                .id(instance.getId())
                .className(instance.getClassName())
                .allocationTime(allocationTime)
                .provenance(provenance)
                .isArray(instance instanceof ArrayInstance);

        if (mutations != null)
        {
            builder.mutations(mutations);
        }

        return builder.build();
    }

    @Override
    public String toString()
    {
        return className + " #" + id;
    }

    /**
     * Starts a new object builder.
     *
     * @return an empty builder
     */
    public static Builder builder()
    {
        return new Builder();
    }

    /** Builder for heap objects; the class name defaults to empty and the fields and mutations to empty collections. */
    public static class Builder
    {
        private int id;
        private String className = "";
        private long allocationTime;
        private ProvenanceInfo provenance;
        private Map<String, FieldValue> fields = new LinkedHashMap<>();
        private List<MutationEvent> mutations = new ArrayList<>();
        private boolean isArray;

        /**
         * Sets the object id.
         *
         * @param id the heap id
         * @return this builder
         */
        public Builder id(int id)
        {
            this.id = id;
            return this;
        }

        /**
         * Sets the class.
         *
         * @param className the class's internal name
         * @return this builder
         */
        public Builder className(String className)
        {
            this.className = className;
            return this;
        }

        /**
         * Sets when the object was allocated.
         *
         * @param allocationTime the instruction count at allocation
         * @return this builder
         */
        public Builder allocationTime(long allocationTime)
        {
            this.allocationTime = allocationTime;
            return this;
        }

        /**
         * Sets where the object was allocated.
         *
         * @param provenance the allocation site, or null if unknown
         * @return this builder
         */
        public Builder provenance(ProvenanceInfo provenance)
        {
            this.provenance = provenance;
            return this;
        }

        /**
         * Replaces the fields; the map is used directly, not copied.
         *
         * @param fields the fields keyed by owner.name:descriptor, or null for none
         * @return this builder
         */
        public Builder fields(Map<String, FieldValue> fields)
        {
            this.fields = fields != null ? fields : new LinkedHashMap<>();
            return this;
        }

        /**
         * Adds one field under its key.
         *
         * @param field the field value
         * @return this builder
         */
        public Builder addField(FieldValue field)
        {
            this.fields.put(field.getKey(), field);
            return this;
        }

        /**
         * Replaces the mutations; the list is used directly, not copied.
         *
         * @param mutations the recorded writes, or null for none
         * @return this builder
         */
        public Builder mutations(List<MutationEvent> mutations)
        {
            this.mutations = mutations != null ? mutations : new ArrayList<>();
            return this;
        }

        /**
         * Adds one mutation.
         *
         * @param mutation the recorded write
         * @return this builder
         */
        public Builder addMutation(MutationEvent mutation)
        {
            this.mutations.add(mutation);
            return this;
        }

        /**
         * Marks the object as an array or not.
         *
         * @param isArray true for arrays
         * @return this builder
         */
        public Builder isArray(boolean isArray)
        {
            this.isArray = isArray;
            return this;
        }

        /**
         * Builds the object, copying the fields and mutations.
         *
         * @return the heap object
         */
        public HeapObject build()
        {
            return new HeapObject(this);
        }
    }
}
