package com.tonic.ui.util;

import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.service.ProjectService;

public class JdkClassFilter {

    private JdkClassFilter() {
    }

    public static boolean isJdkClass(String className) {
        if (className == null) {
            return false;
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null) {
            return !project.isUserClass(className);
        }
        return isJdkClassByPrefix(className);
    }

    public static boolean isUserClass(String className) {
        if (className == null) {
            return false;
        }
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null) {
            return project.isUserClass(className);
        }
        return !isJdkClassByPrefix(className);
    }

    private static boolean isJdkClassByPrefix(String className) {
        return className.startsWith("java/") ||
               className.startsWith("javax/") ||
               className.startsWith("sun/") ||
               className.startsWith("com/sun/") ||
               className.startsWith("jdk/");
    }
}
