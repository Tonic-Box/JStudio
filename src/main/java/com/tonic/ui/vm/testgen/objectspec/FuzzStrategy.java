package com.tonic.ui.vm.testgen.objectspec;

public class FuzzStrategy {

    public enum Type {
        DEFAULT("Default for Type"),
        INT_RANGE("Integer Range"),
        LONG_RANGE("Long Range"),
        DOUBLE_RANGE("Double Range"),
        STRING_SET("String Set"),
        STRING_PATTERN("String Pattern"),
        BOOLEAN("Boolean"),
        ENUM_VALUES("Enum Values"),
        COLLECTION_SIZE("Collection Size Range");

        private final String displayName;

        Type(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private Type type = Type.DEFAULT;
    private long minInt = Integer.MIN_VALUE;
    private long maxInt = Integer.MAX_VALUE;
    private double minDouble = -1000.0;
    private double maxDouble = 1000.0;
    private String[] stringSet;
    private String stringPattern;
    private int minCollectionSize = 0;
    private int maxCollectionSize = 10;
    private boolean includeEdgeCases = true;
    private boolean includeNull = true;
    private int sampleCount = 5;

    public FuzzStrategy() {
    }

    public FuzzStrategy(Type type) {
        this.type = type;
    }

    public static FuzzStrategy defaultStrategy() {
        return new FuzzStrategy(Type.DEFAULT);
    }

    public static FuzzStrategy intRange(int min, int max) {
        FuzzStrategy s = new FuzzStrategy(Type.INT_RANGE);
        s.minInt = min;
        s.maxInt = max;
        return s;
    }

    public static FuzzStrategy doubleRange(double min, double max) {
        FuzzStrategy s = new FuzzStrategy(Type.DOUBLE_RANGE);
        s.minDouble = min;
        s.maxDouble = max;
        return s;
    }

    public static FuzzStrategy stringSet(String... values) {
        FuzzStrategy s = new FuzzStrategy(Type.STRING_SET);
        s.stringSet = values;
        return s;
    }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public long getMinInt() { return minInt; }
    public void setMinInt(long minInt) { this.minInt = minInt; }

    public long getMaxInt() { return maxInt; }
    public void setMaxInt(long maxInt) { this.maxInt = maxInt; }

    public double getMinDouble() { return minDouble; }
    public void setMinDouble(double minDouble) { this.minDouble = minDouble; }

    public double getMaxDouble() { return maxDouble; }
    public void setMaxDouble(double maxDouble) { this.maxDouble = maxDouble; }

    public String[] getStringSet() { return stringSet; }
    public void setStringSet(String[] stringSet) { this.stringSet = stringSet; }

    public String getStringPattern() { return stringPattern; }
    public void setStringPattern(String stringPattern) { this.stringPattern = stringPattern; }

    public int getMinCollectionSize() { return minCollectionSize; }
    public void setMinCollectionSize(int minCollectionSize) { this.minCollectionSize = minCollectionSize; }

    public int getMaxCollectionSize() { return maxCollectionSize; }
    public void setMaxCollectionSize(int maxCollectionSize) { this.maxCollectionSize = maxCollectionSize; }

    public boolean isIncludeEdgeCases() { return includeEdgeCases; }
    public void setIncludeEdgeCases(boolean includeEdgeCases) { this.includeEdgeCases = includeEdgeCases; }

    public boolean isIncludeNull() { return includeNull; }
    public void setIncludeNull(boolean includeNull) { this.includeNull = includeNull; }

    public int getSampleCount() { return sampleCount; }
    public void setSampleCount(int sampleCount) { this.sampleCount = sampleCount; }

    public String getDescription() {
        switch (type) {
            case INT_RANGE:
                return "int[" + minInt + ".." + maxInt + "]";
            case LONG_RANGE:
                return "long[" + minInt + ".." + maxInt + "]";
            case DOUBLE_RANGE:
                return "double[" + minDouble + ".." + maxDouble + "]";
            case STRING_SET:
                if (stringSet != null && stringSet.length > 0) {
                    return "strings[" + stringSet.length + " values]";
                }
                return "strings";
            case STRING_PATTERN:
                return "pattern: " + stringPattern;
            case BOOLEAN:
                return "true/false";
            case COLLECTION_SIZE:
                return "size[" + minCollectionSize + ".." + maxCollectionSize + "]";
            default:
                return "default";
        }
    }
}
