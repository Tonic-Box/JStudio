package com.tonic.ui.vm.testgen.objectspec;

import lombok.Getter;
import lombok.Setter;

/** How to fuzz one value: a strategy type plus its range, string set, pattern or collection size bounds. */
@Getter
@Setter
public class FuzzStrategy
{

    /** A fuzzing strategy type. */
    @Getter
    public enum Type
    {
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

        Type(String displayName)
        {
            this.displayName = displayName;
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

    /** Creates a strategy of the default type for the value's type. */
    public FuzzStrategy()
    {
    }

    /**
     * Creates a strategy of the given type with default bounds.
     *
     * @param type the strategy type
     */
    public FuzzStrategy(Type type)
    {
        this.type = type;
    }

    /**
     * Copies the strategy, including its own copy of the string set.
     *
     * @return the copy
     */
    public FuzzStrategy copy()
    {
        FuzzStrategy copy = new FuzzStrategy(type);
        copy.minInt = minInt;
        copy.maxInt = maxInt;
        copy.minDouble = minDouble;
        copy.maxDouble = maxDouble;
        copy.stringSet = stringSet != null ? stringSet.clone() : null;
        copy.stringPattern = stringPattern;
        copy.minCollectionSize = minCollectionSize;
        copy.maxCollectionSize = maxCollectionSize;
        copy.includeEdgeCases = includeEdgeCases;
        copy.includeNull = includeNull;
        copy.sampleCount = sampleCount;
        return copy;
    }

    /**
     * Creates a strategy of the default type for the value's type.
     *
     * @return the new strategy
     */
    public static FuzzStrategy defaultStrategy()
    {
        return new FuzzStrategy(Type.DEFAULT);
    }

    /**
     * Creates an integer range strategy.
     *
     * @param min the lowest value
     * @param max the highest value
     * @return the new strategy
     */
    public static FuzzStrategy intRange(int min, int max)
    {
        FuzzStrategy s = new FuzzStrategy(Type.INT_RANGE);
        s.minInt = min;
        s.maxInt = max;
        return s;
    }

    /**
     * Creates a double range strategy.
     *
     * @param min the lowest value
     * @param max the highest value
     * @return the new strategy
     */
    public static FuzzStrategy doubleRange(double min, double max)
    {
        FuzzStrategy s = new FuzzStrategy(Type.DOUBLE_RANGE);
        s.minDouble = min;
        s.maxDouble = max;
        return s;
    }

    /**
     * Creates a strategy that picks from fixed strings.
     *
     * @param values the strings to pick from
     * @return the new strategy
     */
    public static FuzzStrategy stringSet(String... values)
    {
        FuzzStrategy s = new FuzzStrategy(Type.STRING_SET);
        s.stringSet = values;
        return s;
    }

    /**
     * Describes the strategy for display.
     *
     * @return a short description such as int[0..10], or "default"
     */
    public String getDescription()
    {
        switch (type)
        {
            case INT_RANGE:
                return "int[" + minInt + ".." + maxInt + "]";
            case LONG_RANGE:
                return "long[" + minInt + ".." + maxInt + "]";
            case DOUBLE_RANGE:
                return "double[" + minDouble + ".." + maxDouble + "]";
            case STRING_SET:
                if (stringSet != null && stringSet.length > 0)
                {
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
