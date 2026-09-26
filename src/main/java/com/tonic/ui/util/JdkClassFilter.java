package com.tonic.ui.util;

import com.tonic.model.ProjectModel;
import com.tonic.service.ProjectService;

/** Tells project classes from library and JDK classes, using the open project when there is one and package prefixes otherwise. */
public class JdkClassFilter
{

    private JdkClassFilter()
    {
    }

    /**
     * Tells whether a class is not the user's: outside the open project's own classes, or with no project, in a java, javax, sun, com.sun or jdk package.
     *
     * @param className the class's internal name, with slashes
     * @return true for a library or JDK class; false for null
     */
    public static boolean isJdkClass(String className)
    {
        if (className == null)
        {
            return false;
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null)
        {
            return !project.isUserClass(className);
        }
        return isJdkClassByPrefix(className);
    }

    /**
     * Tells whether a class is the user's: one of the open project's own classes, or with no project, outside the JDK packages.
     *
     * @param className the class's internal name, with slashes
     * @return true for a user class; false for null
     */
    public static boolean isUserClass(String className)
    {
        if (className == null)
        {
            return false;
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null)
        {
            return project.isUserClass(className);
        }
        return !isJdkClassByPrefix(className);
    }

    private static boolean isJdkClassByPrefix(String className)
    {
        return className.startsWith("java/") ||
                className.startsWith("javax/") ||
                className.startsWith("sun/") ||
                className.startsWith("com/sun/") ||
                className.startsWith("jdk/");
    }
}
