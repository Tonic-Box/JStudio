package com.tonic.ui.util;

public class JdkClassFilter {

    private JdkClassFilter() {
    }

    public static boolean isJdkClass(String className) {
        if (className == null) {
            return false;
        }
        return className.startsWith("java/") ||
               className.startsWith("javax/") ||
               className.startsWith("sun/") ||
               className.startsWith("com/sun/") ||
               className.startsWith("jdk/");
    }

    public static boolean isUserClass(String className) {
        return !isJdkClass(className);
    }
}
