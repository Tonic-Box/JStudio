package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/** Posted by the live heap and statics views to focus the Value Scanner and pre-fill it with a value type, value and package filter. */
@Getter
public class ScanSeedEvent extends Event
{

    private final int valueType;
    private final String value;
    private final String packageFilter;

    /**
     * Creates the event.
     *
     * @param source the poster
     * @param valueType the scanner value type, one of the LiveProtocol SCAN_ constants
     * @param value the value text to scan for
     * @param packageFilter the package filter, or null for none
     */
    public ScanSeedEvent(Object source, int valueType, String value, String packageFilter)
    {
        super(source);
        this.valueType = valueType;
        this.value = value;
        this.packageFilter = packageFilter;
    }
}
