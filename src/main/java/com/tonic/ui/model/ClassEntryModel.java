package com.tonic.ui.model;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.ui.theme.Icons;
import lombok.Getter;
import lombok.Setter;

import javax.swing.Icon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class ClassEntryModel {

    private final ClassFile classFile;
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
    private Icon icon;

    // Decompilation cache
    private String decompilationCache;
    private long decompilationTimestamp;

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

        // Determine icon based on class type (using access flags)
        int access = classFile.getAccess();
        if ((access & 0x0200) != 0) { // ACC_INTERFACE
            this.icon = Icons.getIcon("interface");
        } else if ((access & 0x4000) != 0) { // ACC_ENUM
            this.icon = Icons.getIcon("enum");
        } else if ((access & 0x2000) != 0) { // ACC_ANNOTATION
            this.icon = Icons.getIcon("annotation");
        } else {
            this.icon = Icons.getIcon("class");
        }
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
        return (classFile.getAccess() & 0x0200) != 0;
    }

    public boolean isEnum() {
        return (classFile.getAccess() & 0x4000) != 0;
    }

    public boolean isAnnotation() {
        return (classFile.getAccess() & 0x2000) != 0;
    }

    public boolean isAbstract() {
        return (classFile.getAccess() & 0x0400) != 0;
    }

    public boolean isPublic() {
        return (classFile.getAccess() & 0x0001) != 0;
    }

    public boolean isFinal() {
        return (classFile.getAccess() & 0x0010) != 0;
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

    public FieldEntryModel getField(String name, String descriptor) {
        return fields.get(name + descriptor);
    }

    public List<FieldEntryModel> getFields() {
        return new ArrayList<>(fields.values());
    }

    public void setDecompilationCache(String decompilationCache) {
        this.decompilationCache = decompilationCache;
        this.decompilationTimestamp = System.currentTimeMillis();
    }

    public void invalidateDecompilationCache() {
        this.decompilationCache = null;
        this.decompilationTimestamp = 0;
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
