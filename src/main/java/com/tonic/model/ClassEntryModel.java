package com.tonic.model;

import com.tonic.analysis.source.decompile.DecompileResult;
import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.util.AccessFlags;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/** The UI-side model of one class: its parsed class file, member models keyed by name plus descriptor, display data, and the cached decompiled source. */
@Getter
public class ClassEntryModel
{

    private ClassFile classFile;
    private final Map<String, MethodEntryModel> methods = new HashMap<>();
    private final Map<String, FieldEntryModel> fields = new HashMap<>();

    @Setter
    private boolean expanded;
    @Setter
    private boolean selected;
    @Setter
    private boolean dirty;
    @Setter
    private boolean analyzed;

    private String simpleName;
    private String packageName;
    private String displayName;
    private String iconKey;

    private String decompilationCache;
    private long decompilationTimestamp;
    private Map<String, NavigableMap<Integer, Integer>> sourceLineMaps;
    private Map<String, DecompileResult.MethodSpan> methodSpans;
    private Map<String, DecompileResult.MemberSpan> fieldSpans;
    private DecompileResult.MemberSpan classSpan;

    /**
     * Wraps a parsed class, building its display data and member models.
     *
     * @param classFile the parsed class
     */
    public ClassEntryModel(ClassFile classFile)
    {
        this.classFile = classFile;
        buildDisplayData();
        buildMemberModels();
    }

    private void buildDisplayData()
    {
        String className = classFile.getClassName();
        int lastSlash = className.lastIndexOf('/');
        if (lastSlash >= 0)
        {
            this.packageName = className.substring(0, lastSlash).replace('/', '.');
            this.simpleName = className.substring(lastSlash + 1);
        }
        else
        {
            this.packageName = "";
            this.simpleName = className;
        }
        this.displayName = simpleName;

        int access = classFile.getAccess();
        if (AccessFlags.isInterface(access))
        {
            this.iconKey = "interface";
        }
        else if (AccessFlags.isEnum(access))
        {
            this.iconKey = "enum";
        }
        else if (AccessFlags.isAnnotation(access))
        {
            this.iconKey = "annotation";
        }
        else
        {
            this.iconKey = "class";
        }
    }

    /** Rebuilds the names and icon from the class file, as after a rename, and drops the decompilation cache. */
    public void refreshDisplayData()
    {
        buildDisplayData();
        invalidateDecompilationCache();
    }

    private void buildMemberModels()
    {
        for (MethodEntry method : classFile.getMethods())
        {
            String key = method.getName() + method.getDesc();
            MethodEntryModel model = new MethodEntryModel(method, this);
            methods.put(key, model);
        }

        for (FieldEntry field : classFile.getFields())
        {
            String key = field.getName() + field.getDesc();
            FieldEntryModel model = new FieldEntryModel(field, this);
            fields.put(key, model);
        }
    }

    /**
     * The class's name.
     *
     * @return the internal name, with slashes
     */
    public String getClassName()
    {
        return classFile.getClassName();
    }

    /**
     * The superclass's name.
     *
     * @return the superclass's internal name, as the class file records it
     */
    public String getSuperClassName()
    {
        return classFile.getSuperClassName();
    }

    /**
     * Resolves the names of the directly implemented interfaces from the constant pool.
     *
     * @return a new list of internal names, skipping entries that do not resolve
     */
    public List<String> getInterfaceNames()
    {
        List<String> names = new ArrayList<>();
        for (Integer ifaceIndex : classFile.getInterfaces())
        {
            ClassRefItem classRef = (ClassRefItem) classFile.getConstPool().getItem(ifaceIndex);
            if (classRef != null)
            {
                Utf8Item nameItem = (Utf8Item) classFile.getConstPool().getItem(classRef.getValue());
                if (nameItem != null)
                {
                    names.add(nameItem.getValue());
                }
            }
        }
        return names;
    }

    /**
     * The class's access flags.
     *
     * @return the raw access flags from the class file
     */
    public int getAccessFlags()
    {
        return classFile.getAccess();
    }

    /**
     * Reports whether the class is an interface.
     *
     * @return true if the interface flag is set
     */
    public boolean isInterface()
    {
        return AccessFlags.isInterface(classFile.getAccess());
    }

    /**
     * Reports whether the class is an enum.
     *
     * @return true if the enum flag is set
     */
    public boolean isEnum()
    {
        return AccessFlags.isEnum(classFile.getAccess());
    }

    /**
     * Reports whether the class is an annotation type.
     *
     * @return true if the annotation flag is set
     */
    public boolean isAnnotation()
    {
        return AccessFlags.isAnnotation(classFile.getAccess());
    }

    /**
     * Reports whether the class is abstract.
     *
     * @return true if the abstract flag is set
     */
    public boolean isAbstract()
    {
        return AccessFlags.isAbstract(classFile.getAccess());
    }

    /**
     * Reports whether the class is public.
     *
     * @return true if the public flag is set
     */
    public boolean isPublic()
    {
        return AccessFlags.isPublic(classFile.getAccess());
    }

    /**
     * Reports whether the class is final.
     *
     * @return true if the final flag is set
     */
    public boolean isFinal()
    {
        return AccessFlags.isFinal(classFile.getAccess());
    }

    /**
     * Looks up a method.
     *
     * @param name the method name
     * @param descriptor the method descriptor
     * @return the method, or null if the class declares none with that name and descriptor
     */
    public MethodEntryModel getMethod(String name, String descriptor)
    {
        return methods.get(name + descriptor);
    }

    /**
     * Lists the declared methods, constructors and static initializer included.
     *
     * @return a new list of the methods, in no fixed order
     */
    public List<MethodEntryModel> getMethods()
    {
        return new ArrayList<>(methods.values());
    }

    /**
     * Lists the declared constructors.
     *
     * @return a new list of the constructors, empty if none are declared
     */
    public List<MethodEntryModel> getConstructors()
    {
        List<MethodEntryModel> constructors = new ArrayList<>();
        for (MethodEntryModel method : methods.values())
        {
            if (method.getName().equals("<init>"))
            {
                constructors.add(method);
            }
        }
        return constructors;
    }

    /**
     * Finds the class's public static main entry point taking a string array.
     *
     * @return the main method, or null if the class has none
     */
    public MethodEntryModel getMainMethod()
    {
        MethodEntryModel main = methods.get("main([Ljava/lang/String;)V");
        return main != null && main.isPublic() && main.isStatic() ? main : null;
    }

    /**
     * Reports whether the class has a runnable public static main entry point.
     *
     * @return true if the main method exists
     */
    public boolean hasMainMethod()
    {
        return getMainMethod() != null;
    }

    /**
     * Looks up a field.
     *
     * @param name the field name
     * @param descriptor the field descriptor
     * @return the field, or null if the class declares none with that name and descriptor
     */
    public FieldEntryModel getField(String name, String descriptor)
    {
        return fields.get(name + descriptor);
    }

    /**
     * Lists the declared fields.
     *
     * @return a new list of the fields, in no fixed order
     */
    public List<FieldEntryModel> getFields()
    {
        return new ArrayList<>(fields.values());
    }

    /**
     * Caches decompiled source and stamps the time, clearing the line maps and spans.
     *
     * @param decompilationCache the decompiled source
     */
    public void setDecompilationCache(String decompilationCache)
    {
        this.decompilationCache = decompilationCache;
        this.decompilationTimestamp = System.currentTimeMillis();
        this.sourceLineMaps = null;
        this.methodSpans = null;
        this.fieldSpans = null;
        this.classSpan = null;
    }

    /**
     * Caches decompiled source with its line maps and member spans, which are invalidated together with it.
     *
     * @param decompilationCache the decompiled source
     * @param sourceLineMaps per method key, the map from bytecode offset to source line
     * @param methodSpans per method key, the method's line span in the source
     * @param fieldSpans per field key, the field's line span in the source
     * @param classSpan the class declaration's line span in the source
     */
    public void setDecompilationCache(String decompilationCache, Map<String, NavigableMap<Integer, Integer>> sourceLineMaps, Map<String, DecompileResult.MethodSpan> methodSpans, Map<String, DecompileResult.MemberSpan> fieldSpans, DecompileResult.MemberSpan classSpan)
    {
        setDecompilationCache(decompilationCache);
        this.sourceLineMaps = sourceLineMaps;
        this.methodSpans = methodSpans;
        this.fieldSpans = fieldSpans;
        this.classSpan = classSpan;
    }

    /** Drops the cached source, line maps and spans. */
    public void invalidateDecompilationCache()
    {
        this.decompilationCache = null;
        this.decompilationTimestamp = 0;
        this.sourceLineMaps = null;
        this.methodSpans = null;
        this.fieldSpans = null;
        this.classSpan = null;
    }

    /**
     * Swaps in new bytecode for the class, rebuilding members and display data, dropping the decompilation cache and marking the class dirty.
     *
     * @param newClassFile the replacement class file
     */
    public void updateClassFile(ClassFile newClassFile)
    {
        this.classFile = newClassFile;
        this.methods.clear();
        this.fields.clear();
        buildMemberModels();
        buildDisplayData();
        invalidateDecompilationCache();
        setDirty(true);
    }

    @Override
    public String toString()
    {
        return displayName;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ClassEntryModel other = (ClassEntryModel) obj;
        return getClassName().equals(other.getClassName());
    }

    @Override
    public int hashCode()
    {
        return getClassName().hashCode();
    }
}
