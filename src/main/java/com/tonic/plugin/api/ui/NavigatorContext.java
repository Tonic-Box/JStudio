package com.tonic.plugin.api.ui;

import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ResourceEntryModel;

import java.util.Optional;

/** The navigator node a NavigatorActionProvider is asked about: at most one accessor is present, matching the kind of node right-clicked, and all are empty for packages, folders and the project root. */
public interface NavigatorContext
{

    /**
     * Returns the right-clicked class.
     *
     * @return the class, or empty unless a class node was right-clicked
     */
    Optional<ClassEntryModel> selectedClass();

    /**
     * Returns the right-clicked method; its owner is on the method, not in selectedClass.
     *
     * @return the method, or empty unless a method node was right-clicked
     */
    Optional<MethodEntryModel> selectedMethod();

    /**
     * Returns the right-clicked field.
     *
     * @return the field, or empty unless a field node was right-clicked
     */
    Optional<FieldEntryModel> selectedField();

    /**
     * Returns the right-clicked resource.
     *
     * @return the resource, or empty unless a resource node was right-clicked
     */
    Optional<ResourceEntryModel> selectedResource();
}
