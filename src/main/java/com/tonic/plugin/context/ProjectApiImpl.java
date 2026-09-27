package com.tonic.plugin.context;

import com.tonic.analysis.CodeWriter;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ConstPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.parser.attribute.ConstantValueAttribute;
import com.tonic.parser.constpool.DoubleItem;
import com.tonic.parser.constpool.FloatItem;
import com.tonic.parser.constpool.IntegerItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.LongItem;
import com.tonic.parser.constpool.StringRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.util.DescriptorParser;
import com.tonic.plugin.api.ProjectApi;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.MethodEntryModel;
import com.tonic.model.ProjectModel;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** The ProjectApi over one fixed project model. */
public class ProjectApiImpl implements ProjectApi
{

    private final ProjectModel projectModel;

    /**
     * Creates the API over a project.
     *
     * @param projectModel the project to read
     */
    public ProjectApiImpl(ProjectModel projectModel)
    {
        this.projectModel = projectModel;
    }

    @Override
    public String getName()
    {
        return projectModel.getProjectName();
    }

    @Override
    public String getPath()
    {
        return projectModel.getSourceFile() != null
                ? projectModel.getSourceFile().getAbsolutePath()
                : "";
    }

    @Override
    public List<ClassInfo> getClasses()
    {
        return projectModel.getAllClasses().stream()
                .map(ClassInfoImpl::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<ClassInfo> getClasses(Predicate<ClassInfo> filter)
    {
        return getClasses().stream().filter(filter).collect(Collectors.toList());
    }

    @Override
    public Optional<ClassInfo> getClass(String name)
    {
        ClassEntryModel entry = projectModel.findClassByName(name);
        return entry != null ? Optional.of(new ClassInfoImpl(entry)) : Optional.empty();
    }

    @Override
    public void forEachClass(Consumer<ClassInfo> action)
    {
        for (ClassEntryModel entry : projectModel.getAllClasses())
        {
            action.accept(new ClassInfoImpl(entry));
        }
    }

    @Override
    public void forEachMethod(Consumer<MethodInfo> action)
    {
        for (ClassEntryModel classEntry : projectModel.getAllClasses())
        {
            for (MethodEntryModel methodEntry : classEntry.getMethods())
            {
                action.accept(new MethodInfoImpl(methodEntry));
            }
        }
    }

    @Override
    public List<MethodInfo> getMethods(String className)
    {
        ClassEntryModel entry = projectModel.findClassByName(className);
        if (entry == null) return List.of();
        return entry.getMethods().stream()
                .map(MethodInfoImpl::new)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<MethodInfo> getMethod(String className, String methodName, String descriptor)
    {
        ClassEntryModel classEntry = projectModel.findClassByName(className);
        if (classEntry == null) return Optional.empty();
        MethodEntryModel methodEntry = classEntry.getMethod(methodName, descriptor);
        return methodEntry != null
                ? Optional.of(new MethodInfoImpl(methodEntry))
                : Optional.empty();
    }

    @Override
    public List<String> getPackages()
    {
        return projectModel.getPackages();
    }

    @Override
    public List<ClassInfo> getClassesInPackage(String packageName)
    {
        return projectModel.getClassesInPackage(packageName.replace('/', '.')).stream()
                .map(ClassInfoImpl::new)
                .collect(Collectors.toList());
    }

    @Override
    public int getClassCount()
    {
        return projectModel.getClassCount();
    }

    @Override
    public int getMethodCount()
    {
        int count = 0;
        for (ClassEntryModel entry : projectModel.getAllClasses())
        {
            count += entry.getMethods().size();
        }
        return count;
    }

    static final class ClassInfoImpl implements ClassInfo
    {
        private final ClassEntryModel entry;

        ClassInfoImpl(ClassEntryModel entry)
        {
            this.entry = entry;
        }

        @Override
        public String getName()
        {
            return entry.getClassName();
        }

        @Override
        public String getSimpleName()
        {
            return entry.getSimpleName();
        }

        @Override
        public String getPackageName()
        {
            return entry.getPackageName();
        }

        @Override
        public String getSuperclass()
        {
            return entry.getSuperClassName();
        }

        @Override
        public List<String> getInterfaces()
        {
            return entry.getInterfaceNames();
        }

        @Override
        public List<MethodInfo> getMethods()
        {
            return entry.getMethods().stream()
                    .map(MethodInfoImpl::new)
                    .collect(Collectors.toList());
        }

        @Override
        public List<FieldInfo> getFields()
        {
            return entry.getFields().stream()
                    .map(FieldInfoImpl::new)
                    .collect(Collectors.toList());
        }

        @Override
        public int getAccessFlags()
        {
            return entry.getAccessFlags();
        }

        @Override
        public boolean isInterface()
        {
            return entry.isInterface();
        }

        @Override
        public boolean isAbstract()
        {
            return entry.isAbstract();
        }

        @Override
        public boolean isEnum()
        {
            return entry.isEnum();
        }

        @Override
        public boolean isAnnotation()
        {
            return entry.isAnnotation();
        }

        @Override
        public byte[] getBytecode()
        {
            try
            {
                ClassFile cf = entry.getClassFile();
                return cf != null ? cf.write() : new byte[0];
            }
            catch (Exception e)
            {
                return new byte[0];
            }
        }
    }

    private static class MethodInfoImpl implements MethodInfo
    {
        private final MethodEntryModel entry;

        MethodInfoImpl(MethodEntryModel entry)
        {
            this.entry = entry;
        }

        @Override
        public String getName()
        {
            return entry.getName();
        }

        @Override
        public String getDescriptor()
        {
            return entry.getDescriptor();
        }

        @Override
        public String getClassName()
        {
            return entry.getOwner().getClassName();
        }

        @Override
        public String getSignature()
        {
            return entry.getDisplaySignature();
        }

        @Override
        public int getAccessFlags()
        {
            return entry.getAccessFlags();
        }

        @Override
        public boolean isStatic()
        {
            return entry.isStatic();
        }

        @Override
        public boolean isAbstract()
        {
            return entry.isAbstract();
        }

        @Override
        public boolean isNative()
        {
            return entry.isNative();
        }

        @Override
        public boolean isSynthetic()
        {
            return (entry.getAccessFlags() & 0x1000) != 0;
        }

        @Override
        public List<String> getParameterTypes()
        {
            return DescriptorParser.parameterDescriptors(entry.getDescriptor());
        }

        @Override
        public String getReturnType()
        {
            String desc = entry.getDescriptor();
            int idx = desc.lastIndexOf(')');
            return idx >= 0 ? desc.substring(idx + 1) : "V";
        }

        @Override
        public int getInstructionCount()
        {
            MethodEntry method = entry.getMethodEntry();
            return method.getCodeAttribute() != null ? new CodeWriter(method).getInstructionCount() : 0;
        }

        @Override
        public byte[] getBytecode()
        {
            CodeAttribute code = entry.getMethodEntry().getCodeAttribute();
            return code != null ? code.getCode().clone() : new byte[0];
        }
    }

    private static class FieldInfoImpl implements FieldInfo
    {
        private final FieldEntryModel entry;

        FieldInfoImpl(FieldEntryModel entry)
        {
            this.entry = entry;
        }

        @Override
        public String getName()
        {
            return entry.getName();
        }

        @Override
        public String getDescriptor()
        {
            return entry.getDescriptor();
        }

        @Override
        public String getClassName()
        {
            return entry.getOwner().getClassName();
        }

        @Override
        public int getAccessFlags()
        {
            return entry.getAccessFlags();
        }

        @Override
        public boolean isStatic()
        {
            return entry.isStatic();
        }

        @Override
        public boolean isFinal()
        {
            return entry.isFinal();
        }

        @Override
        public Object getConstantValue()
        {
            ConstPool constPool = entry.getFieldEntry().getClassFile().getConstPool();
            for (Attribute attribute : entry.getFieldEntry().getAttributes())
            {
                if (attribute instanceof ConstantValueAttribute)
                {
                    return constantOf(constPool, constPool.getItem(((ConstantValueAttribute) attribute).getConstantValueIndex()));
                }
            }
            return null;
        }

        private static Object constantOf(ConstPool constPool, Item<?> item)
        {
            if (item instanceof StringRefItem)
            {
                Item<?> utf8 = constPool.getItem(((StringRefItem) item).getValue());
                return utf8 instanceof Utf8Item ? ((Utf8Item) utf8).getValue() : null;
            }
            return item instanceof IntegerItem || item instanceof LongItem || item instanceof FloatItem || item instanceof DoubleItem ? item.getValue() : null;
        }
    }
}
