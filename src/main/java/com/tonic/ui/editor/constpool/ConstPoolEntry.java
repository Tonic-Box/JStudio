package com.tonic.ui.editor.constpool;

import lombok.Getter;

/** One constant pool row: its index, type name, display value and the raw value searched alongside it. */
@Getter
public class ConstPoolEntry
{
    private final int index;
    private final String type;
    private final String value;
    private final String rawValue;

    /**
     * Creates an entry.
     *
     * @param index the constant pool index
     * @param type the constant's type name, such as Utf8 or MethodRef
     * @param value the text shown in the table
     * @param rawValue the underlying text, such as the string itself, also matched by search
     */
    public ConstPoolEntry(int index, String type, String value, String rawValue)
    {
        this.index = index;
        this.type = type;
        this.value = value;
        this.rawValue = rawValue;
    }

    /**
     * Creates an entry whose raw value is its display value.
     *
     * @param index the constant pool index
     * @param type the constant's type name, such as Utf8 or MethodRef
     * @param value the text shown in the table
     */
    public ConstPoolEntry(int index, String type, String value)
    {
        this(index, type, value, value);
    }
}
