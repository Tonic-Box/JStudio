package com.tonic.service;

import com.tonic.analysis.xref.Xref;
import com.tonic.analysis.xref.XrefBuilder;
import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.event.events.FindUsagesEvent;
import com.tonic.model.ProjectModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Shared access point for the project's cross-reference database: lazy building and the
 * user-class-filtered usage queries. Both the Find Usages results panel and the editor's
 * usage-count lenses go through here, so their numbers always agree.
 */
public final class XrefQueryService {

    private XrefQueryService() {
    }

    /**
     * The project's xref database, building and caching it on the project when absent or empty.
     * Building scans every class, so callers should invoke this off the EDT.
     */
    public static XrefDatabase ensureDatabase(ProjectModel project) {
        XrefDatabase db = project.getXrefDatabase();
        if (db == null || db.isEmpty()) {
            db = new XrefBuilder(project.getClassPool()).build();
            project.setXrefDatabase(db);
        }
        return db;
    }

    /**
     * All references to the given member from user classes (JDK callers and synthetic class-level
     * refs without a source method are excluded). Returns an empty list when no database exists;
     * callers wanting a build should use {@link #ensureDatabase} first.
     */
    public static List<Xref> getUsages(ProjectModel project, FindUsagesEvent.TargetType targetType,
                                       String className, String memberName, String memberDescriptor) {
        XrefDatabase db = project.getXrefDatabase();
        if (db == null) {
            return Collections.emptyList();
        }

        List<Xref> results;
        switch (targetType) {
            case METHOD:
                results = db.getRefsToMethod(className, memberName, memberDescriptor);
                break;
            case FIELD:
                results = db.getRefsToField(className, memberName, memberDescriptor);
                break;
            case CLASS:
            default:
                results = db.getRefsToClass(className);
                break;
        }

        boolean classQuery = targetType == FindUsagesEvent.TargetType.CLASS;
        List<Xref> filtered = new ArrayList<>();
        for (Xref xref : results) {
            if (!project.isUserClass(xref.getSourceClass()) || xref.getSourceMethod() == null) {
                continue;
            }
            // A class query indexes every ref whose target class matches, including reads/writes/calls
            // of the class's own members (e.g. an internal `this.field = ...`). Those are member usages,
            // not type usages, so a class search keeps only references to the class as a type
            // (new/cast/instanceof/type positions, which carry no target member).
            if (classQuery && xref.getTargetMember() != null) {
                continue;
            }
            filtered.add(xref);
        }
        return filtered;
    }
}
