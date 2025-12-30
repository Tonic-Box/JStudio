package com.tonic.ui.query.planner.probe;

import java.util.Objects;

/**
 * Specification for a runtime probe - defines what events to capture.
 */
public interface ProbeSpec {

    ProbeType getType();

    enum ProbeType {
        CALL,
        ALLOCATION,
        FIELD,
        STRING,
        EXCEPTION,
        BRANCH,
        COVERAGE
    }

    final class CallProbe implements ProbeSpec {
        private final String targetOwner;
        private final String targetName;
        private final String targetDesc;

        public CallProbe(String targetOwner, String targetName, String targetDesc) {
            this.targetOwner = targetOwner;
            this.targetName = targetName;
            this.targetDesc = targetDesc;
        }

        public String targetOwner() { return targetOwner; }
        public String targetName() { return targetName; }
        public String targetDesc() { return targetDesc; }

        @Override
        public ProbeType getType() {
            return ProbeType.CALL;
        }

        public static CallProbe all() {
            return new CallProbe(null, null, null);
        }

        public static CallProbe forMethod(String owner, String name, String desc) {
            return new CallProbe(owner, name, desc);
        }

        public boolean matches(String owner, String name, String desc) {
            if (targetOwner != null && !targetOwner.equals(owner) && !owner.endsWith("/" + targetOwner)) {
                return false;
            }
            if (targetName != null && !targetName.equals(name)) {
                return false;
            }
            if (targetDesc != null && !targetDesc.equals(desc)) {
                return false;
            }
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CallProbe)) return false;
            CallProbe that = (CallProbe) o;
            return Objects.equals(targetOwner, that.targetOwner) &&
                   Objects.equals(targetName, that.targetName) &&
                   Objects.equals(targetDesc, that.targetDesc);
        }

        @Override
        public int hashCode() {
            return Objects.hash(targetOwner, targetName, targetDesc);
        }
    }

    final class AllocProbe implements ProbeSpec {
        private final String typeName;
        private final boolean trackCount;
        private final boolean trackInstances;

        public AllocProbe(String typeName, boolean trackCount, boolean trackInstances) {
            this.typeName = typeName;
            this.trackCount = trackCount;
            this.trackInstances = trackInstances;
        }

        public String typeName() { return typeName; }
        public boolean trackCount() { return trackCount; }
        public boolean trackInstances() { return trackInstances; }

        @Override
        public ProbeType getType() {
            return ProbeType.ALLOCATION;
        }

        public static AllocProbe all() {
            return new AllocProbe(null, true, false);
        }

        public static AllocProbe forType(String typeName) {
            return new AllocProbe(typeName, true, false);
        }

        public static AllocProbe withInstances(String typeName) {
            return new AllocProbe(typeName, true, true);
        }

        public boolean matches(String type) {
            if (typeName == null) return true;
            return typeName.equals(type) || type.endsWith("/" + typeName);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AllocProbe)) return false;
            AllocProbe that = (AllocProbe) o;
            return trackCount == that.trackCount &&
                   trackInstances == that.trackInstances &&
                   Objects.equals(typeName, that.typeName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(typeName, trackCount, trackInstances);
        }
    }

    final class FieldProbe implements ProbeSpec {
        private final String ownerClass;
        private final String fieldName;
        private final String descriptor;
        private final boolean trackReads;
        private final boolean trackWrites;
        private final boolean trackTransitions;

        public FieldProbe(String ownerClass, String fieldName, String descriptor,
                          boolean trackReads, boolean trackWrites, boolean trackTransitions) {
            this.ownerClass = ownerClass;
            this.fieldName = fieldName;
            this.descriptor = descriptor;
            this.trackReads = trackReads;
            this.trackWrites = trackWrites;
            this.trackTransitions = trackTransitions;
        }

        public String ownerClass() { return ownerClass; }
        public String fieldName() { return fieldName; }
        public String descriptor() { return descriptor; }
        public boolean trackReads() { return trackReads; }
        public boolean trackWrites() { return trackWrites; }
        public boolean trackTransitions() { return trackTransitions; }

        @Override
        public ProbeType getType() {
            return ProbeType.FIELD;
        }

        public static FieldProbe reads(String owner, String name) {
            return new FieldProbe(owner, name, null, true, false, false);
        }

        public static FieldProbe writes(String owner, String name) {
            return new FieldProbe(owner, name, null, false, true, false);
        }

        public static FieldProbe transitions(String owner, String name) {
            return new FieldProbe(owner, name, null, false, true, true);
        }

        public static FieldProbe all() {
            return new FieldProbe(null, null, null, true, true, false);
        }

        public boolean matches(String owner, String name) {
            if (ownerClass != null && !ownerClass.equals(owner) && !owner.endsWith("/" + ownerClass)) {
                return false;
            }
            if (fieldName != null && !fieldName.equals(name)) {
                return false;
            }
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FieldProbe)) return false;
            FieldProbe that = (FieldProbe) o;
            return trackReads == that.trackReads &&
                   trackWrites == that.trackWrites &&
                   trackTransitions == that.trackTransitions &&
                   Objects.equals(ownerClass, that.ownerClass) &&
                   Objects.equals(fieldName, that.fieldName) &&
                   Objects.equals(descriptor, that.descriptor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ownerClass, fieldName, descriptor, trackReads, trackWrites, trackTransitions);
        }
    }

    final class StringProbe implements ProbeSpec {
        private final String pattern;
        private final boolean isRegex;
        private final boolean captureValue;

        public StringProbe(String pattern, boolean isRegex, boolean captureValue) {
            this.pattern = pattern;
            this.isRegex = isRegex;
            this.captureValue = captureValue;
        }

        public String pattern() { return pattern; }
        public boolean isRegex() { return isRegex; }
        public boolean captureValue() { return captureValue; }

        @Override
        public ProbeType getType() {
            return ProbeType.STRING;
        }

        public static StringProbe all() {
            return new StringProbe(null, false, true);
        }

        public static StringProbe matching(String pattern, boolean isRegex) {
            return new StringProbe(pattern, isRegex, true);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof StringProbe)) return false;
            StringProbe that = (StringProbe) o;
            return isRegex == that.isRegex &&
                   captureValue == that.captureValue &&
                   Objects.equals(pattern, that.pattern);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pattern, isRegex, captureValue);
        }
    }

    final class ExceptionProbe implements ProbeSpec {
        private final String exceptionType;
        private final boolean includeSubtypes;
        private final boolean trackThrows;
        private final boolean trackCatches;

        public ExceptionProbe(String exceptionType, boolean includeSubtypes,
                              boolean trackThrows, boolean trackCatches) {
            this.exceptionType = exceptionType;
            this.includeSubtypes = includeSubtypes;
            this.trackThrows = trackThrows;
            this.trackCatches = trackCatches;
        }

        public String exceptionType() { return exceptionType; }
        public boolean includeSubtypes() { return includeSubtypes; }
        public boolean trackThrows() { return trackThrows; }
        public boolean trackCatches() { return trackCatches; }

        @Override
        public ProbeType getType() {
            return ProbeType.EXCEPTION;
        }

        public static ExceptionProbe all() {
            return new ExceptionProbe(null, true, true, true);
        }

        public static ExceptionProbe forType(String type) {
            return new ExceptionProbe(type, true, true, true);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ExceptionProbe)) return false;
            ExceptionProbe that = (ExceptionProbe) o;
            return includeSubtypes == that.includeSubtypes &&
                   trackThrows == that.trackThrows &&
                   trackCatches == that.trackCatches &&
                   Objects.equals(exceptionType, that.exceptionType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(exceptionType, includeSubtypes, trackThrows, trackCatches);
        }
    }

    final class BranchProbe implements ProbeSpec {
        private final boolean trackTaken;
        private final boolean trackNotTaken;

        public BranchProbe(boolean trackTaken, boolean trackNotTaken) {
            this.trackTaken = trackTaken;
            this.trackNotTaken = trackNotTaken;
        }

        public boolean trackTaken() { return trackTaken; }
        public boolean trackNotTaken() { return trackNotTaken; }

        @Override
        public ProbeType getType() {
            return ProbeType.BRANCH;
        }

        public static BranchProbe all() {
            return new BranchProbe(true, true);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BranchProbe)) return false;
            BranchProbe that = (BranchProbe) o;
            return trackTaken == that.trackTaken && trackNotTaken == that.trackNotTaken;
        }

        @Override
        public int hashCode() {
            return Objects.hash(trackTaken, trackNotTaken);
        }
    }

    final class CoverageProbe implements ProbeSpec {
        private final boolean blockLevel;
        private final boolean edgeLevel;

        public CoverageProbe(boolean blockLevel, boolean edgeLevel) {
            this.blockLevel = blockLevel;
            this.edgeLevel = edgeLevel;
        }

        public boolean blockLevel() { return blockLevel; }
        public boolean edgeLevel() { return edgeLevel; }

        @Override
        public ProbeType getType() {
            return ProbeType.COVERAGE;
        }

        public static CoverageProbe blocks() {
            return new CoverageProbe(true, false);
        }

        public static CoverageProbe edges() {
            return new CoverageProbe(false, true);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CoverageProbe)) return false;
            CoverageProbe that = (CoverageProbe) o;
            return blockLevel == that.blockLevel && edgeLevel == that.edgeLevel;
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockLevel, edgeLevel);
        }
    }
}
