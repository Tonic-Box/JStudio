package com.tonic.plugin.context;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.plugin.api.ProjectApi;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.FieldEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ProjectApiImpl implements ProjectApi {

    private final ProjectModel projectModel;

    public ProjectApiImpl(ProjectModel projectModel) {
        this.projectModel = projectModel;
    }

    @Override
    public String getName() {
        return projectModel.getProjectName();
    }

    @Override
    public String getPath() {
        return projectModel.getSourceFile() != null
            ? projectModel.getSourceFile().getAbsolutePath()
            : "";
    }

    @Override
    public List<ClassInfo> getClasses() {
        return projectModel.getAllClasses().stream()
            .map(ClassInfoImpl::new)
            .collect(Collectors.toList());
    }

    @Override
    public List<ClassInfo> getClasses(Predicate<ClassInfo> filter) {
        return getClasses().stream().filter(filter).collect(Collectors.toList());
    }

    @Override
    public Optional<ClassInfo> getClass(String name) {
        ClassEntryModel entry = projectModel.findClassByName(name);
        return entry != null ? Optional.of(new ClassInfoImpl(entry)) : Optional.empty();
    }

    @Override
    public void forEachClass(Consumer<ClassInfo> action) {
        for (ClassEntryModel entry : projectModel.getAllClasses()) {
            action.accept(new ClassInfoImpl(entry));
        }
    }

    @Override
    public void forEachMethod(Consumer<MethodInfo> action) {
        for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
            for (MethodEntryModel methodEntry : classEntry.getMethods()) {
                action.accept(new MethodInfoImpl(methodEntry));
            }
        }
    }

    @Override
    public List<MethodInfo> getMethods(String className) {
        ClassEntryModel entry = projectModel.findClassByName(className);
        if (entry == null) return List.of();
        return entry.getMethods().stream()
            .map(MethodInfoImpl::new)
            .collect(Collectors.toList());
    }

    @Override
    public Optional<MethodInfo> getMethod(String className, String methodName, String descriptor) {
        ClassEntryModel classEntry = projectModel.findClassByName(className);
        if (classEntry == null) return Optional.empty();
        MethodEntryModel methodEntry = classEntry.getMethod(methodName, descriptor);
        return methodEntry != null
            ? Optional.of(new MethodInfoImpl(methodEntry))
            : Optional.empty();
    }

    @Override
    public List<String> getPackages() {
        return projectModel.getPackages();
    }

    @Override
    public List<ClassInfo> getClassesInPackage(String packageName) {
        return projectModel.getClassesInPackage(packageName.replace('.', '/')).stream()
            .map(ClassInfoImpl::new)
            .collect(Collectors.toList());
    }

    @Override
    public int getClassCount() {
        return projectModel.getClassCount();
    }

    @Override
    public int getMethodCount() {
        int count = 0;
        for (ClassEntryModel entry : projectModel.getAllClasses()) {
            count += entry.getMethods().size();
        }
        return count;
    }

    private static class ClassInfoImpl implements ClassInfo {
        private final ClassEntryModel entry;

        ClassInfoImpl(ClassEntryModel entry) {
            this.entry = entry;
        }

        @Override
        public String getName() {
            return entry.getClassName();
        }

        @Override
        public String getSimpleName() {
            return entry.getSimpleName();
        }

        @Override
        public String getPackageName() {
            return entry.getPackageName();
        }

        @Override
        public String getSuperclass() {
            return entry.getSuperClassName();
        }

        @Override
        public List<String> getInterfaces() {
            return entry.getInterfaceNames();
        }

        @Override
        public List<MethodInfo> getMethods() {
            return entry.getMethods().stream()
                .map(MethodInfoImpl::new)
                .collect(Collectors.toList());
        }

        @Override
        public List<FieldInfo> getFields() {
            return entry.getFields().stream()
                .map(FieldInfoImpl::new)
                .collect(Collectors.toList());
        }

        @Override
        public int getAccessFlags() {
            return entry.getAccessFlags();
        }

        @Override
        public boolean isInterface() {
            return entry.isInterface();
        }

        @Override
        public boolean isAbstract() {
            return entry.isAbstract();
        }

        @Override
        public boolean isEnum() {
            return entry.isEnum();
        }

        @Override
        public boolean isAnnotation() {
            return entry.isAnnotation();
        }

        @Override
        public byte[] getBytecode() {
            try {
                ClassFile cf = entry.getClassFile();
                return cf != null ? cf.write() : new byte[0];
            } catch (Exception e) {
                return new byte[0];
            }
        }
    }

    private static class MethodInfoImpl implements MethodInfo {
        private final MethodEntryModel entry;

        MethodInfoImpl(MethodEntryModel entry) {
            this.entry = entry;
        }

        @Override
        public String getName() {
            return entry.getName();
        }

        @Override
        public String getDescriptor() {
            return entry.getDescriptor();
        }

        @Override
        public String getClassName() {
            return entry.getOwner().getClassName();
        }

        @Override
        public String getSignature() {
            return entry.getDisplaySignature();
        }

        @Override
        public int getAccessFlags() {
            return entry.getAccessFlags();
        }

        @Override
        public boolean isStatic() {
            return entry.isStatic();
        }

        @Override
        public boolean isAbstract() {
            return entry.isAbstract();
        }

        @Override
        public boolean isNative() {
            return entry.isNative();
        }

        @Override
        public boolean isSynthetic() {
            return (entry.getAccessFlags() & 0x1000) != 0;
        }

        @Override
        public List<String> getParameterTypes() {
            return parseParameterTypes(entry.getDescriptor());
        }

        @Override
        public String getReturnType() {
            String desc = entry.getDescriptor();
            int idx = desc.lastIndexOf(')');
            return idx >= 0 ? desc.substring(idx + 1) : "V";
        }

        @Override
        public int getInstructionCount() {
            MethodEntry me = entry.getMethodEntry();
            CodeAttribute code = me.getCodeAttribute();
            return code != null ? code.getCode().length : 0;
        }

        @Override
        public byte[] getBytecode() {
            MethodEntry me = entry.getMethodEntry();
            CodeAttribute code = me.getCodeAttribute();
            return code != null ? code.getCode() : new byte[0];
        }

        private List<String> parseParameterTypes(String descriptor) {
            List<String> types = new ArrayList<>();
            int i = descriptor.indexOf('(') + 1;
            int end = descriptor.indexOf(')');
            while (i < end) {
                char c = descriptor.charAt(i);
                StringBuilder type = new StringBuilder();
                while (c == '[') {
                    type.append('[');
                    i++;
                    c = descriptor.charAt(i);
                }
                if (c == 'L') {
                    int semi = descriptor.indexOf(';', i);
                    type.append(descriptor, i, semi + 1);
                    i = semi + 1;
                } else {
                    type.append(c);
                    i++;
                }
                types.add(type.toString());
            }
            return types;
        }
    }

    private static class FieldInfoImpl implements FieldInfo {
        private final FieldEntryModel entry;

        FieldInfoImpl(FieldEntryModel entry) {
            this.entry = entry;
        }

        @Override
        public String getName() {
            return entry.getName();
        }

        @Override
        public String getDescriptor() {
            return entry.getDescriptor();
        }

        @Override
        public String getClassName() {
            return entry.getOwner().getClassName();
        }

        @Override
        public int getAccessFlags() {
            return entry.getAccessFlags();
        }

        @Override
        public boolean isStatic() {
            return entry.isStatic();
        }

        @Override
        public boolean isFinal() {
            return entry.isFinal();
        }

        @Override
        public Object getConstantValue() {
            return null;
        }
    }
}
