package com.tonic.service;

import com.tonic.analysis.xref.Xref;
import com.tonic.analysis.xref.XrefBuilder;
import com.tonic.analysis.xref.XrefDatabase;
import com.tonic.event.events.FindUsagesEvent;
import com.tonic.model.ProjectModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The single access point for a project's cross-reference database and its user-class usage queries, so Find Usages and the usage-count lenses agree. */
public final class XrefQueryService
{

    private XrefQueryService()
    {
    }

    /**
     * Returns the project's xref database, building it by scanning every class and caching it on the project when absent or empty.
     *
     * @param project the project
     * @return the database
     */
    public static XrefDatabase ensureDatabase(ProjectModel project)
    {
        XrefDatabase db = project.getXrefDatabase();
        if (db == null || db.isEmpty())
        {
            db = new XrefBuilder(project.getClassPool()).build();
            project.setXrefDatabase(db);
        }
        return db;
    }

    /**
     * Lists the references to a class, method or field made from methods of user classes; a class query leaves out references to its members.
     *
     * @param project the project
     * @param targetType whether the target is a class, method or field
     * @param className the target class's internal name, with slashes
     * @param memberName the method or field name; unused for a class
     * @param memberDescriptor the method or field descriptor; unused for a class
     * @return the references, or an empty list when the project has no database yet
     */
    public static List<Xref> getUsages(ProjectModel project, FindUsagesEvent.TargetType targetType, String className, String memberName, String memberDescriptor)
    {
        XrefDatabase db = project.getXrefDatabase();
        if (db == null)
        {
            return Collections.emptyList();
        }

        List<Xref> results;
        switch (targetType)
        {
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
        for (Xref xref : results)
        {
            if (!project.isUserClass(xref.getSourceClass()) || xref.getSourceMethod() == null)
            {
                continue;
            }
            if (classQuery && xref.getTargetMember() != null)
            {
                continue;
            }
            filtered.add(xref);
        }
        return filtered;
    }
}
