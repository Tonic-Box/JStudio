package com.tonic.service.deadcode;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/** The result of {@link DeadCodeAnalyzer}: the dead classes, methods, and fields it found. */
@Getter
public final class DeadCodeReport {

    private final List<DeadItem> deadClasses = new ArrayList<>();
    private final List<DeadItem> deadMethods = new ArrayList<>();
    private final List<DeadItem> deadFields = new ArrayList<>();

    public boolean isEmpty() {
        return deadClasses.isEmpty() && deadMethods.isEmpty() && deadFields.isEmpty();
    }

    public int total() {
        return deadClasses.size() + deadMethods.size() + deadFields.size();
    }
}
