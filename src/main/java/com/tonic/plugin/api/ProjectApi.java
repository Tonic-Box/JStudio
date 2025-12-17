package com.tonic.plugin.api;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface ProjectApi {

    String getName();

    String getPath();

    List<ClassInfo> getClasses();

    List<ClassInfo> getClasses(Predicate<ClassInfo> filter);

    Optional<ClassInfo> getClass(String name);

    void forEachClass(Consumer<ClassInfo> action);

    void forEachMethod(Consumer<MethodInfo> action);

    List<MethodInfo> getMethods(String className);

    Optional<MethodInfo> getMethod(String className, String methodName, String descriptor);

    List<String> getPackages();

    List<ClassInfo> getClassesInPackage(String packageName);

    int getClassCount();

    int getMethodCount();

    interface ClassInfo {
        String getName();
        String getSimpleName();
        String getPackageName();
        String getSuperclass();
        List<String> getInterfaces();
        List<MethodInfo> getMethods();
        List<FieldInfo> getFields();
        int getAccessFlags();
        boolean isInterface();
        boolean isAbstract();
        boolean isEnum();
        boolean isAnnotation();
        byte[] getBytecode();
    }

    interface MethodInfo {
        String getName();
        String getDescriptor();
        String getClassName();
        String getSignature();
        int getAccessFlags();
        boolean isStatic();
        boolean isAbstract();
        boolean isNative();
        boolean isSynthetic();
        List<String> getParameterTypes();
        String getReturnType();
        int getInstructionCount();
        byte[] getBytecode();
    }

    interface FieldInfo {
        String getName();
        String getDescriptor();
        String getClassName();
        int getAccessFlags();
        boolean isStatic();
        boolean isFinal();
        Object getConstantValue();
    }
}
