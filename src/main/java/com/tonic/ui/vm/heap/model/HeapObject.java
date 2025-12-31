package com.tonic.ui.vm.heap.model;

import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class HeapObject {
    private final int id;
    private final String className;
    private final long allocationTime;
    private final ProvenanceInfo provenance;
    private final Map<String, FieldValue> fields;
    private final List<MutationEvent> mutations;
    private final boolean lambda;
    private final boolean array;
    private final boolean string;

    protected HeapObject(Builder builder) {
        this.id = builder.id;
        this.className = builder.className;
        this.allocationTime = builder.allocationTime;
        this.provenance = builder.provenance;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(builder.fields));
        this.mutations = Collections.unmodifiableList(new ArrayList<>(builder.mutations));
        this.lambda = detectLambda(className);
        this.array = builder.isArray;
        this.string = "java/lang/String".equals(className);
    }

    private boolean detectLambda(String name) {
        return name != null && (name.contains("$Lambda$") || name.contains("$$Lambda$"));
    }

    public String getSimpleClassName() {
        if (className == null) {
            return "null";
        }
        int lastSlash = className.lastIndexOf('/');
        return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
    }

    public FieldValue getField(String key) {
        return fields.get(key);
    }

    public boolean hasMutations() {
        return !mutations.isEmpty();
    }

    public List<String> getLambdaCaptureFieldNames() {
        if (!lambda) {
            return Collections.emptyList();
        }
        List<String> captures = new ArrayList<>();
        for (String key : fields.keySet()) {
            FieldValue fv = fields.get(key);
            String name = fv.getName();
            if (name.startsWith("arg$") || name.startsWith("capture$") || name.startsWith("val$")) {
                captures.add(name);
            }
        }
        return captures;
    }

    public List<Integer> getReferencedObjectIds() {
        List<Integer> refs = new ArrayList<>();
        for (FieldValue fv : fields.values()) {
            if (fv.hasReferenceId()) {
                refs.add(fv.getReferenceId());
            }
        }
        return refs;
    }

    public static HeapObject fromObjectInstance(ObjectInstance instance, long allocationTime,
                                                  ProvenanceInfo provenance,
                                                  List<MutationEvent> mutations) {
        Builder builder = builder()
            .id(instance.getId())
            .className(instance.getClassName())
            .allocationTime(allocationTime)
            .provenance(provenance)
            .isArray(instance instanceof ArrayInstance);

        if (mutations != null) {
            builder.mutations(mutations);
        }

        return builder.build();
    }

    @Override
    public String toString() {
        return className + " #" + id;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int id;
        private String className = "";
        private long allocationTime;
        private ProvenanceInfo provenance;
        private Map<String, FieldValue> fields = new LinkedHashMap<>();
        private List<MutationEvent> mutations = new ArrayList<>();
        private boolean isArray;

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder className(String className) {
            this.className = className;
            return this;
        }

        public Builder allocationTime(long allocationTime) {
            this.allocationTime = allocationTime;
            return this;
        }

        public Builder provenance(ProvenanceInfo provenance) {
            this.provenance = provenance;
            return this;
        }

        public Builder fields(Map<String, FieldValue> fields) {
            this.fields = fields != null ? fields : new LinkedHashMap<>();
            return this;
        }

        public Builder addField(FieldValue field) {
            this.fields.put(field.getKey(), field);
            return this;
        }

        public Builder mutations(List<MutationEvent> mutations) {
            this.mutations = mutations != null ? mutations : new ArrayList<>();
            return this;
        }

        public Builder addMutation(MutationEvent mutation) {
            this.mutations.add(mutation);
            return this;
        }

        public Builder isArray(boolean isArray) {
            this.isArray = isArray;
            return this;
        }

        public HeapObject build() {
            return new HeapObject(this);
        }
    }
}
