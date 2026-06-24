package com.tonic.event.events;

import com.tonic.event.Event;
import lombok.Getter;

/**
 * Posted by the live heap/statics views to seed the Value Scanner from a "Scan for this value" action: it
 * carries a scanner value type ({@link com.tonic.live.protocol.LiveProtocol} {@code SCAN_*}), the value text,
 * and an optional package filter. MainFrame focuses the scanner tool and pre-fills its scan bar.
 */
@Getter
public class ScanSeedEvent extends Event {

    private final int valueType;
    private final String value;
    private final String packageFilter;

    public ScanSeedEvent(Object source, int valueType, String value, String packageFilter) {
        super(source);
        this.valueType = valueType;
        this.value = value;
        this.packageFilter = packageFilter;
    }
}
