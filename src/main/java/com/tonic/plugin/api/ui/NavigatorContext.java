package com.tonic.plugin.api.ui;

import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ResourceEntryModel;

import java.util.Optional;

/**
 * The navigator-tree selection a {@link NavigatorActionProvider} is asked about when the context menu opens.
 * Exactly one of the accessors is typically present (matching the kind of node right-clicked); the rest are empty.
 * Wraps the selection in stable model types without exposing the internal tree-node classes.
 */
public interface NavigatorContext {

    Optional<ClassEntryModel> selectedClass();

    Optional<MethodEntryModel> selectedMethod();

    Optional<FieldEntryModel> selectedField();

    Optional<ResourceEntryModel> selectedResource();
}
