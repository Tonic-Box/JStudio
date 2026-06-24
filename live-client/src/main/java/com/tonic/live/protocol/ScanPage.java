package com.tonic.live.protocol;

import lombok.Getter;

import java.util.List;

/** A page of value-scan results: the total match count, whether the walk was capped, and the returned slice. */
@Getter
public final class ScanPage {

    private final int total;
    /**
     * -- GETTER --
     * True when caps (visited/matches/time) stopped the walk early - results are a partial view.
     */
    private final boolean truncated;
    private final List<ScanLocation> locations;

    public ScanPage(int total, boolean truncated, List<ScanLocation> locations) {
        this.total = total;
        this.truncated = truncated;
        this.locations = locations;
    }

}
