package com.tonic.service.deadcode;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/** The result of a dead-code analysis: the dead classes, methods and fields it found. */
@Getter
public final class DeadCodeReport
{

    private final List<DeadItem> deadClasses = new ArrayList<>();
    private final List<DeadItem> deadMethods = new ArrayList<>();
    private final List<DeadItem> deadFields = new ArrayList<>();

    /**
     * Reports whether nothing dead was found.
     *
     * @return true if there are no dead classes, methods or fields
     */
    public boolean isEmpty()
    {
        return deadClasses.isEmpty() && deadMethods.isEmpty() && deadFields.isEmpty();
    }

    /**
     * Counts every dead item.
     *
     * @return the number of dead classes, methods and fields together
     */
    public int total()
    {
        return deadClasses.size() + deadMethods.size() + deadFields.size();
    }
}
