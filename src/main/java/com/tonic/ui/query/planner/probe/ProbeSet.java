package com.tonic.ui.query.planner.probe;

import java.util.*;

/**
 * Collection of probe specifications for a query.
 * Determines which listener capabilities are needed.
 */
public class ProbeSet {

    public enum Capability {
        CALL_TRACKING,
        ALLOCATION_TRACKING,
        FIELD_TRACKING,
        STRING_TRACKING,
        EXCEPTION_TRACKING,
        BRANCH_TRACKING,
        COVERAGE_TRACKING
    }

    private final List<ProbeSpec> probes;
    private final EnumSet<Capability> requiredCapabilities;

    private ProbeSet(List<ProbeSpec> probes) {
        this.probes = List.copyOf(probes);
        this.requiredCapabilities = computeCapabilities(probes);
    }

    public List<ProbeSpec> getProbes() {
        return probes;
    }

    public Set<Capability> getRequiredCapabilities() {
        return Collections.unmodifiableSet(requiredCapabilities);
    }

    public boolean hasProbeType(ProbeSpec.ProbeType type) {
        return probes.stream().anyMatch(p -> p.getType() == type);
    }

    public <T extends ProbeSpec> List<T> getProbesOfType(Class<T> type) {
        List<T> result = new ArrayList<>();
        for (ProbeSpec probe : probes) {
            if (type.isInstance(probe)) {
                result.add(type.cast(probe));
            }
        }
        return result;
    }

    private static EnumSet<Capability> computeCapabilities(List<ProbeSpec> probes) {
        EnumSet<Capability> caps = EnumSet.noneOf(Capability.class);

        for (ProbeSpec probe : probes) {
            switch (probe.getType()) {
                case CALL:
                    caps.add(Capability.CALL_TRACKING);
                    break;
                case ALLOCATION:
                    caps.add(Capability.ALLOCATION_TRACKING);
                    break;
                case FIELD:
                    caps.add(Capability.FIELD_TRACKING);
                    break;
                case STRING:
                    caps.add(Capability.STRING_TRACKING);
                    break;
                case EXCEPTION:
                    caps.add(Capability.EXCEPTION_TRACKING);
                    break;
                case BRANCH:
                    caps.add(Capability.BRANCH_TRACKING);
                    break;
                case COVERAGE:
                    caps.add(Capability.COVERAGE_TRACKING);
                    break;
            }
        }

        return caps;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ProbeSet empty() {
        return new ProbeSet(List.of());
    }

    public static class Builder {
        private final List<ProbeSpec> probes = new ArrayList<>();

        public Builder add(ProbeSpec probe) {
            probes.add(probe);
            return this;
        }

        public Builder addCallProbe(String owner, String name, String desc) {
            probes.add(ProbeSpec.CallProbe.forMethod(owner, name, desc));
            return this;
        }

        public Builder addAllCallsProbe() {
            probes.add(ProbeSpec.CallProbe.all());
            return this;
        }

        public Builder addAllocProbe(String typeName) {
            probes.add(ProbeSpec.AllocProbe.forType(typeName));
            return this;
        }

        public Builder addAllAllocsProbe() {
            probes.add(ProbeSpec.AllocProbe.all());
            return this;
        }

        public Builder addFieldWriteProbe(String owner, String name) {
            probes.add(ProbeSpec.FieldProbe.writes(owner, name));
            return this;
        }

        public Builder addFieldReadProbe(String owner, String name) {
            probes.add(ProbeSpec.FieldProbe.reads(owner, name));
            return this;
        }

        public Builder addFieldTransitionProbe(String owner, String name) {
            probes.add(ProbeSpec.FieldProbe.transitions(owner, name));
            return this;
        }

        public Builder addStringProbe() {
            probes.add(ProbeSpec.StringProbe.all());
            return this;
        }

        public Builder addStringProbe(String pattern, boolean isRegex) {
            probes.add(ProbeSpec.StringProbe.matching(pattern, isRegex));
            return this;
        }

        public Builder addExceptionProbe() {
            probes.add(ProbeSpec.ExceptionProbe.all());
            return this;
        }

        public Builder addExceptionProbe(String exceptionType) {
            probes.add(ProbeSpec.ExceptionProbe.forType(exceptionType));
            return this;
        }

        public Builder addBranchProbe() {
            probes.add(ProbeSpec.BranchProbe.all());
            return this;
        }

        public Builder addCoverageProbe(boolean edges) {
            probes.add(edges ? ProbeSpec.CoverageProbe.edges() : ProbeSpec.CoverageProbe.blocks());
            return this;
        }

        public ProbeSet build() {
            return new ProbeSet(probes);
        }
    }
}
