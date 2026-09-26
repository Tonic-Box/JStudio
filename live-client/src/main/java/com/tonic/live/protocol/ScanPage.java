package com.tonic.live.protocol;

import lombok.Getter;

import java.util.List;

/** A page of value-scan results: the total match count, whether the walk was capped, and the returned slice. */
@Getter
public final class ScanPage
{

    private final int total;
    /** True when a visited, match or time cap stopped the walk early, so the results are partial. */
    private final boolean truncated;
    private final List<ScanLocation> locations;

    /**
     * Creates a page.
     *
     * @param total the total match count
     * @param truncated whether a cap stopped the walk early
     * @param locations the returned slice of matches
     */
    public ScanPage(int total, boolean truncated, List<ScanLocation> locations)
    {
        this.total = total;
        this.truncated = truncated;
        this.locations = locations;
    }

}
