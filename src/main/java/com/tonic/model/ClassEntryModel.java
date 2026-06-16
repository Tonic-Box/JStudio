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

@Getter
public class ClassEntryModel {

    private ClassFile classFile;
    private final Map<String, MethodEntryModel> methods = new HashMap<>();
    private final Map<String, FieldEntryModel> fields = new HashMap<>();

    // UI state
    @Setter
    private boolean expanded;
    @Setter
    private boolean selected;
    @Setter
    private boolean dirty;
    @Setter
    private boolean analyzed;

    // Cached display data
    private String simpleName;
    private String packageName;
    private String displayName;
    private String iconKey;

    // Decompilation cache
    private String decompilationCache;
    private long decompilationTimestamp;
    private Map<String, NavigableMap<Integer, Integer>> sourceLineMaps;
    private Map<String, DecompileResult.MethodSpan> methodSpans;
    private Map<String, DecompileResult.MemberSpan> fieldSpans;
    private DecompileResult.MemberSpan classSpan;

    public ClassEntryModel(ClassFile classFile) {
        this.classFile = classFile;
        buildDisplayData();
        buildMemberModels();
    }

    private void buildDisplayData() {
        String className = classFile.getClassName();
        int lastSlash = className.lastIndexOf('/');
        if (lastSlash >= 0) {
            this.packageName = className.substring(0, lastSlash).replace('/', '.');
            this.simpleName = className.substring(lastSlash + 1);
        } else {
            this.packageName = "";
            this.simpleName = className;
        }
        this.displayName = simpleName;

        int access = classFile.getAccess();
        if (AccessFlags.isInterface(access)) {
            this.iconKey = "interface";
        } else if (AccessFlags.isEnum(access)) {
            this.iconKey = "enum";
        } else if (AccessFlags.isAnnotation(access)) {
            this.iconKey = "annotation";
        } else {
            this.iconKey = "class";
        }
    }

    public void refreshDisplayData() {
        buildDisplayData();
        invalidateDecompilationCache();
    }

    private void buildMemberModels() {
        // Build method models
        for (MethodEntry method : classFile.getMethods()) {
            String key = method.getName() + method.getDesc();
            MethodEntryModel model = new MethodEntryModel(method, this);
            methods.put(key, model);
        }

        // Build field models
        for (FieldEntry field : classFile.getFields()) {
            String key = field.getName() + field.getDesc();
            FieldEntryModel model = new FieldEntryModel(field, this);
            fields.put(key, model);
        }
    }

    // ClassFile delegated methods

    public String getClassName() {
        return classFile.getClassName();
    }

    public String getSuperClassName() {
        return classFile.getSuperClassName();
    }

    public List<String> getInterfaceNames() {
        // Resolve interface indices to names from constant pool
        List<String> names = new ArrayList<>();
        for (Integer ifaceIndex : classFile.getInterfaces()) {
            ClassRefItem classRef = (ClassRefItem) classFile.getConstPool().getItem(ifaceIndex);
            if (classRef != null) {
                Utf8Item nameItem = (Utf8Item) classFile.getConstPool().getItem(classRef.getValue());
                if (nameItem != null) {
                    names.add(nameItem.getValue());
                }
            }
        }
        return names;
    }

    public int getAccessFlags() {
        return classFile.getAccess();
    }

    public boolean isInterface() {
        return AccessFlags.isInterface(classFile.getAccess());
    }

    public boolean isEnum() {
        return AccessFlags.isEnum(classFile.getAccess());
    }

    public boolean isAnnotation() {
        return AccessFlags.isAnnotation(classFile.getAccess());
    }

    public boolean isAbstract() {
        return AccessFlags.isAbstract(classFile.getAccess());
    }

    public boolean isPublic() {
        return AccessFlags.isPublic(classFile.getAccess());
    }

    public boolean isFinal() {
        return AccessFlags.isFinal(classFile.getAccess());
    }

    // Member access

    public MethodEntryModel getMethod(String name, String descriptor) {
        return methods.get(name + descriptor);
    }

    public List<MethodEntryModel> getMethods() {
        return new ArrayList<>(methods.values());
    }

    public List<MethodEntryModel> getConstructors() {
        List<MethodEntryModel> constructors = new ArrayList<>();
        for (MethodEntryModel method : methods.values()) {
            if (method.getName().equals("<init>")) {
                constructors.add(method);
            }
        }
        return constructors;
    }

    /** The {@code public static void main(String[])} entry point of this class, or null if it has none. */
    public MethodEntryModel getMainMethod() {
        MethodEntryModel main = methods.get("main([Ljava/lang/String;)V");
        return main != null && main.isPublic() && main.isStatic() ? main : null;
    }

    /** Whether this class has a runnable {@code public static void main(String[])} entry point. */
    public boolean hasMainMethod() {
        return getMainMethod() != null;
    }

    public FieldEntryModel getField(String name, String descriptor) {
        return fields.get(name + descriptor);
    }

    public List<FieldEntryModel> getFields() {
        return new ArrayList<>(fields.values());
    }

    public void setDecompilationCache(String decompilationCache) {
        this.decompilationCache = decompilationCache;
        this.decompilationTimestamp = System.currentTimeMillis();
        this.sourceLineMaps = null;
        this.methodSpans = null;
        this.fieldSpans = null;
        this.classSpan = null;
    }

    /**
     * Caches decompiled source together with its per-member spans and per-method offset-to-line maps,
     * so PC navigation and declaration lenses can resolve exact source lines. All are invalidated with
     * the source.
     */
    public void setDecompilationCache(String decompilationCache,
                                      Map<String, NavigableMap<Integer, Integer>> sourceLineMaps,
                                      Map<String, DecompileResult.MethodSpan> methodSpans,
                                      Map<String, DecompileResult.MemberSpan> fieldSpans,
                                      DecompileResult.MemberSpan classSpan) {
        setDecompilationCache(decompilationCache);
        this.sourceLineMaps = sourceLineMaps;
        this.methodSpans = methodSpans;
        this.fieldSpans = fieldSpans;
        this.classSpan = classSpan;
    }

    public void invalidateDecompilationCache() {
        this.decompilationCache = null;
        this.decompilationTimestamp = 0;
        this.sourceLineMaps = null;
        this.methodSpans = null;
        this.fieldSpans = null;
        this.classSpan = null;
    }

    public void updateClassFile(ClassFile newClassFile) {
        this.classFile = newClassFile;
        this.methods.clear();
        this.fields.clear();
        buildMemberModels();
        buildDisplayData();
        invalidateDecompilationCache();
        setDirty(true);
    }

    @Override
    public String toString() {
        return displayName;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ClassEntryModel other = (ClassEntryModel) obj;
        return getClassName().equals(other.getClassName());
    }

    @Override
    public int hashCode() {
        return getClassName().hashCode();
    }
}
