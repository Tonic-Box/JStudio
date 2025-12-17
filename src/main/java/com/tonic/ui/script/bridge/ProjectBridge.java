package com.tonic.ui.script.bridge;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.RuntimeVisibleAnnotationsAttribute;
import com.tonic.parser.attribute.anotation.Annotation;
import com.tonic.parser.constpool.ClassRefItem;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.model.MethodEntryModel;
import com.tonic.ui.model.ProjectModel;
import com.tonic.ui.script.engine.ScriptFunction;
import com.tonic.ui.script.engine.ScriptInterpreter;
import com.tonic.ui.script.engine.ScriptValue;

import java.util.*;
import java.util.function.Consumer;

/**
 * Bridge for project-wide queries and iteration.
 * Exposes a 'project' global object for accessing classes, methods, and fields.
 */
public class ProjectBridge {

    private final ScriptInterpreter interpreter;
    private final ProjectModel projectModel;
    private Consumer<String> logCallback;

    public ProjectBridge(ScriptInterpreter interpreter, ProjectModel projectModel) {
        this.interpreter = interpreter;
        this.projectModel = projectModel;
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    public ScriptValue createProjectObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("classCount", ScriptValue.function(
            ScriptFunction.native0("classCount", () ->
                ScriptValue.number(projectModel.getAllClasses().size()))
        ));

        props.put("getClasses", ScriptValue.function(
            ScriptFunction.native0("getClasses", this::getAllClasses)
        ));

        props.put("forEachClass", ScriptValue.function(
            ScriptFunction.native1("forEachClass", this::forEachClass)
        ));

        props.put("forEachMethod", ScriptValue.function(
            ScriptFunction.native1("forEachMethod", this::forEachMethod)
        ));

        props.put("forEachField", ScriptValue.function(
            ScriptFunction.native1("forEachField", this::forEachField)
        ));

        props.put("findClasses", ScriptValue.function(
            ScriptFunction.native1("findClasses", this::findClasses)
        ));

        props.put("findMethods", ScriptValue.function(
            ScriptFunction.native1("findMethods", this::findMethods)
        ));

        props.put("findAnnotated", ScriptValue.function(
            ScriptFunction.native1("findAnnotated", this::findAnnotated)
        ));

        props.put("getClass", ScriptValue.function(
            ScriptFunction.native1("getClass", this::getClassByName)
        ));

        props.put("getMethod", ScriptValue.function(
            ScriptFunction.native2("getMethod", this::getMethod)
        ));

        props.put("findUsages", ScriptValue.function(
            ScriptFunction.native1("findUsages", this::findUsages)
        ));

        props.put("getPackages", ScriptValue.function(
            ScriptFunction.native0("getPackages", this::getPackages)
        ));

        props.put("getClassesInPackage", ScriptValue.function(
            ScriptFunction.native1("getClassesInPackage", this::getClassesInPackage)
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue getAllClasses() {
        List<ScriptValue> classes = new ArrayList<>();
        for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
            classes.add(wrapClass(classEntry));
        }
        return ScriptValue.array(classes);
    }

    private ScriptValue forEachClass(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("forEachClass requires a function argument");
        }
        ScriptFunction fn = callback.asFunction();
        for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
            List<ScriptValue> args = new ArrayList<>();
            args.add(wrapClass(classEntry));
            fn.call(interpreter, args);
        }
        return ScriptValue.NULL;
    }

    private ScriptValue forEachMethod(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("forEachMethod requires a function argument");
        }
        ScriptFunction fn = callback.asFunction();
        for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
            for (MethodEntryModel methodModel : classEntry.getMethods()) {
                List<ScriptValue> args = new ArrayList<>();
                args.add(wrapMethod(classEntry, methodModel));
                fn.call(interpreter, args);
            }
        }
        return ScriptValue.NULL;
    }

    private ScriptValue forEachField(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("forEachField requires a function argument");
        }
        ScriptFunction fn = callback.asFunction();
        for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
            ClassFile cf = classEntry.getClassFile();
            for (FieldEntry field : cf.getFields()) {
                List<ScriptValue> args = new ArrayList<>();
                args.add(wrapField(classEntry, field));
                fn.call(interpreter, args);
            }
        }
        return ScriptValue.NULL;
    }

    private ScriptValue findClasses(ScriptValue filter) {
        List<ScriptValue> result = new ArrayList<>();

        if (filter.isFunction()) {
            ScriptFunction fn = filter.asFunction();
            for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
                List<ScriptValue> args = new ArrayList<>();
                args.add(wrapClass(classEntry));
                if (fn.call(interpreter, args).asBoolean()) {
                    result.add(wrapClass(classEntry));
                }
            }
        } else if (filter.isObject()) {
            Map<String, ScriptValue> criteria = filter.asObject();
            String namePattern = criteria.containsKey("name") ? criteria.get("name").asString() : null;
            String packagePattern = criteria.containsKey("package") ? criteria.get("package").asString() : null;
            String access = criteria.containsKey("access") ? criteria.get("access").asString() : null;
            String superClass = criteria.containsKey("extends") ? criteria.get("extends").asString() : null;
            String impl = criteria.containsKey("implements") ? criteria.get("implements").asString() : null;

            for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
                ClassFile cf = classEntry.getClassFile();
                boolean matches = true;

                if (namePattern != null && !matchesPattern(classEntry.getSimpleName(), namePattern)) {
                    matches = false;
                }
                if (packagePattern != null && !matchesPattern(classEntry.getPackageName(), packagePattern)) {
                    matches = false;
                }
                if (access != null && !matchesAccess(cf.getAccess(), access)) {
                    matches = false;
                }
                if (superClass != null && !cf.getSuperClassName().contains(superClass)) {
                    matches = false;
                }
                if (impl != null && !implementsInterface(cf, impl)) {
                    matches = false;
                }

                if (matches) {
                    result.add(wrapClass(classEntry));
                }
            }
        }

        return ScriptValue.array(result);
    }

    private ScriptValue findMethods(ScriptValue filter) {
        List<ScriptValue> result = new ArrayList<>();

        if (filter.isFunction()) {
            ScriptFunction fn = filter.asFunction();
            for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
                for (MethodEntryModel methodModel : classEntry.getMethods()) {
                    List<ScriptValue> args = new ArrayList<>();
                    args.add(wrapMethod(classEntry, methodModel));
                    if (fn.call(interpreter, args).asBoolean()) {
                        result.add(wrapMethod(classEntry, methodModel));
                    }
                }
            }
        } else if (filter.isObject()) {
            Map<String, ScriptValue> criteria = filter.asObject();
            String namePattern = criteria.containsKey("name") ? criteria.get("name").asString() : null;
            String access = criteria.containsKey("access") ? criteria.get("access").asString() : null;
            String returnType = criteria.containsKey("returns") ? criteria.get("returns").asString() : null;
            boolean staticOnly = criteria.containsKey("static") && criteria.get("static").asBoolean();

            for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
                for (MethodEntryModel methodModel : classEntry.getMethods()) {
                    MethodEntry method = methodModel.getMethodEntry();
                    boolean matches = true;

                    if (namePattern != null && !matchesPattern(method.getName(), namePattern)) {
                        matches = false;
                    }
                    if (access != null && !matchesAccess(method.getAccess(), access)) {
                        matches = false;
                    }
                    if (staticOnly && (method.getAccess() & 0x0008) == 0) {
                        matches = false;
                    }
                    if (returnType != null && !method.getDesc().endsWith(returnType)) {
                        matches = false;
                    }

                    if (matches) {
                        result.add(wrapMethod(classEntry, methodModel));
                    }
                }
            }
        }

        return ScriptValue.array(result);
    }

    private ScriptValue findAnnotated(ScriptValue annotationName) {
        String annoName = annotationName.asString();
        List<ScriptValue> result = new ArrayList<>();

        for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
            ClassFile cf = classEntry.getClassFile();

            if (hasAnnotation(cf.getClassAttributes(), annoName, cf)) {
                result.add(wrapClass(classEntry));
            }

            for (MethodEntryModel methodModel : classEntry.getMethods()) {
                MethodEntry method = methodModel.getMethodEntry();
                if (hasAnnotation(method.getAttributes(), annoName, cf)) {
                    result.add(wrapMethod(classEntry, methodModel));
                }
            }

            for (FieldEntry field : cf.getFields()) {
                if (hasAnnotation(field.getAttributes(), annoName, cf)) {
                    result.add(wrapField(classEntry, field));
                }
            }
        }

        return ScriptValue.array(result);
    }

    private ScriptValue getClassByName(ScriptValue nameValue) {
        String name = nameValue.asString();
        for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
            if (classEntry.getClassName().equals(name) ||
                classEntry.getSimpleName().equals(name) ||
                classEntry.getClassName().endsWith("/" + name)) {
                return wrapClass(classEntry);
            }
        }
        return ScriptValue.NULL;
    }

    private ScriptValue getMethod(ScriptValue className, ScriptValue methodSig) {
        String clsName = className.asString();
        String methodName = methodSig.asString();

        for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
            if (classEntry.getClassName().equals(clsName) ||
                classEntry.getSimpleName().equals(clsName)) {
                for (MethodEntryModel methodModel : classEntry.getMethods()) {
                    MethodEntry method = methodModel.getMethodEntry();
                    if (method.getName().equals(methodName) ||
                        (method.getName() + method.getDesc()).equals(methodName)) {
                        return wrapMethod(classEntry, methodModel);
                    }
                }
            }
        }
        return ScriptValue.NULL;
    }

    private ScriptValue findUsages(ScriptValue target) {
        List<ScriptValue> usages = new ArrayList<>();
        String targetRef;

        if (target.isObject()) {
            Map<String, ScriptValue> obj = target.asObject();
            if (obj.containsKey("fullName")) {
                targetRef = obj.get("fullName").asString();
            } else if (obj.containsKey("className")) {
                targetRef = obj.get("className").asString();
            } else {
                return ScriptValue.array(usages);
            }
        } else {
            targetRef = target.asString();
        }

        for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
            for (MethodEntryModel methodModel : classEntry.getMethods()) {
                MethodEntry method = methodModel.getMethodEntry();
                if (method.getCodeAttribute() != null) {
                    String code = method.getCodeAttribute().toString();
                    if (code.contains(targetRef)) {
                        usages.add(wrapMethod(classEntry, methodModel));
                    }
                }
            }
        }

        return ScriptValue.array(usages);
    }

    private ScriptValue getPackages() {
        Set<String> packages = new TreeSet<>();
        for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
            String pkg = classEntry.getPackageName();
            if (pkg != null && !pkg.isEmpty()) {
                packages.add(pkg);
            }
        }
        List<ScriptValue> result = new ArrayList<>();
        for (String pkg : packages) {
            result.add(ScriptValue.string(pkg));
        }
        return ScriptValue.array(result);
    }

    private ScriptValue getClassesInPackage(ScriptValue packageName) {
        String pkg = packageName.asString();
        List<ScriptValue> result = new ArrayList<>();
        for (ClassEntryModel classEntry : projectModel.getAllClasses()) {
            if (pkg.equals(classEntry.getPackageName())) {
                result.add(wrapClass(classEntry));
            }
        }
        return ScriptValue.array(result);
    }

    private ScriptValue wrapClass(ClassEntryModel classEntry) {
        Map<String, ScriptValue> props = new HashMap<>();
        ClassFile cf = classEntry.getClassFile();

        props.put("type", ScriptValue.string("class"));
        props.put("name", ScriptValue.string(classEntry.getSimpleName()));
        props.put("className", ScriptValue.string(classEntry.getClassName()));
        props.put("fullName", ScriptValue.string(classEntry.getClassName()));
        props.put("package", ScriptValue.string(classEntry.getPackageName()));
        props.put("superClass", ScriptValue.string(cf.getSuperClassName()));
        props.put("accessFlags", ScriptValue.number(cf.getAccess()));
        props.put("isPublic", ScriptValue.bool((cf.getAccess() & 0x0001) != 0));
        props.put("isAbstract", ScriptValue.bool((cf.getAccess() & 0x0400) != 0));
        props.put("isInterface", ScriptValue.bool((cf.getAccess() & 0x0200) != 0));
        props.put("isEnum", ScriptValue.bool((cf.getAccess() & 0x4000) != 0));

        List<ScriptValue> interfaces = new ArrayList<>();
        for (Integer ifaceIdx : cf.getInterfaces()) {
            String ifaceName = resolveClassName(cf, ifaceIdx);
            if (ifaceName != null) {
                interfaces.add(ScriptValue.string(ifaceName));
            }
        }
        props.put("interfaces", ScriptValue.array(interfaces));

        props.put("methodCount", ScriptValue.number(classEntry.getMethods().size()));
        props.put("fieldCount", ScriptValue.number(cf.getFields().size()));

        props.put("getMethods", ScriptValue.function(
            ScriptFunction.native0("getMethods", () -> {
                List<ScriptValue> methods = new ArrayList<>();
                for (MethodEntryModel m : classEntry.getMethods()) {
                    methods.add(wrapMethod(classEntry, m));
                }
                return ScriptValue.array(methods);
            })
        ));

        props.put("getFields", ScriptValue.function(
            ScriptFunction.native0("getFields", () -> {
                List<ScriptValue> fields = new ArrayList<>();
                for (FieldEntry f : cf.getFields()) {
                    fields.add(wrapField(classEntry, f));
                }
                return ScriptValue.array(fields);
            })
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue wrapMethod(ClassEntryModel classEntry, MethodEntryModel methodModel) {
        Map<String, ScriptValue> props = new HashMap<>();
        MethodEntry method = methodModel.getMethodEntry();

        props.put("type", ScriptValue.string("method"));
        props.put("name", ScriptValue.string(method.getName()));
        props.put("desc", ScriptValue.string(method.getDesc()));
        props.put("signature", ScriptValue.string(method.getName() + method.getDesc()));
        props.put("fullName", ScriptValue.string(classEntry.getClassName() + "." + method.getName() + method.getDesc()));
        props.put("className", ScriptValue.string(classEntry.getClassName()));
        props.put("accessFlags", ScriptValue.number(method.getAccess()));
        props.put("isPublic", ScriptValue.bool((method.getAccess() & 0x0001) != 0));
        props.put("isStatic", ScriptValue.bool((method.getAccess() & 0x0008) != 0));
        props.put("isPrivate", ScriptValue.bool((method.getAccess() & 0x0002) != 0));
        props.put("isAbstract", ScriptValue.bool((method.getAccess() & 0x0400) != 0));
        props.put("isNative", ScriptValue.bool((method.getAccess() & 0x0100) != 0));
        props.put("hasCode", ScriptValue.bool(method.getCodeAttribute() != null));

        if (method.getCodeAttribute() != null) {
            props.put("maxStack", ScriptValue.number(method.getCodeAttribute().getMaxStack()));
            props.put("maxLocals", ScriptValue.number(method.getCodeAttribute().getMaxLocals()));
            props.put("codeLength", ScriptValue.number(method.getCodeAttribute().getCode().length));
        }

        return ScriptValue.object(props);
    }

    private ScriptValue wrapField(ClassEntryModel classEntry, FieldEntry field) {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("type", ScriptValue.string("field"));
        props.put("name", ScriptValue.string(field.getName()));
        props.put("desc", ScriptValue.string(field.getDesc()));
        props.put("fullName", ScriptValue.string(classEntry.getClassName() + "." + field.getName()));
        props.put("className", ScriptValue.string(classEntry.getClassName()));
        props.put("accessFlags", ScriptValue.number(field.getAccess()));
        props.put("isPublic", ScriptValue.bool((field.getAccess() & 0x0001) != 0));
        props.put("isStatic", ScriptValue.bool((field.getAccess() & 0x0008) != 0));
        props.put("isPrivate", ScriptValue.bool((field.getAccess() & 0x0002) != 0));
        props.put("isFinal", ScriptValue.bool((field.getAccess() & 0x0010) != 0));

        return ScriptValue.object(props);
    }

    private String resolveClassName(ClassFile cf, int classIndex) {
        try {
            Item<?> item = cf.getConstPool().getItem(classIndex);
            if (item instanceof ClassRefItem) {
                return ((ClassRefItem) item).getClassName();
            }
        } catch (Exception e) {
        }
        return null;
    }

    private boolean matchesPattern(String text, String pattern) {
        if (pattern.startsWith("*") && pattern.endsWith("*")) {
            return text.contains(pattern.substring(1, pattern.length() - 1));
        } else if (pattern.startsWith("*")) {
            return text.endsWith(pattern.substring(1));
        } else if (pattern.endsWith("*")) {
            return text.startsWith(pattern.substring(0, pattern.length() - 1));
        } else {
            return text.equals(pattern) || text.matches(pattern);
        }
    }

    private boolean matchesAccess(int flags, String access) {
        switch (access.toLowerCase()) {
            case "public": return (flags & 0x0001) != 0;
            case "private": return (flags & 0x0002) != 0;
            case "protected": return (flags & 0x0004) != 0;
            case "static": return (flags & 0x0008) != 0;
            case "final": return (flags & 0x0010) != 0;
            case "abstract": return (flags & 0x0400) != 0;
            default: return true;
        }
    }

    private boolean implementsInterface(ClassFile cf, String interfaceName) {
        for (Integer ifaceIdx : cf.getInterfaces()) {
            String ifaceName = resolveClassName(cf, ifaceIdx);
            if (ifaceName != null && ifaceName.contains(interfaceName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnnotation(List<Attribute> attributes, String annotationName, ClassFile cf) {
        if (attributes == null) return false;
        for (Attribute attr : attributes) {
            if (attr instanceof RuntimeVisibleAnnotationsAttribute) {
                RuntimeVisibleAnnotationsAttribute annoAttr = (RuntimeVisibleAnnotationsAttribute) attr;
                for (Annotation anno : annoAttr.getAnnotations()) {
                    String type = resolveAnnotationType(anno, cf);
                    if (type != null && type.contains(annotationName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private String resolveAnnotationType(Annotation anno, ClassFile cf) {
        try {
            Item<?> typeItem = cf.getConstPool().getItem(anno.getTypeIndex());
            if (typeItem instanceof Utf8Item) {
                return ((Utf8Item) typeItem).getValue();
            }
        } catch (Exception e) {
        }
        return null;
    }
}
