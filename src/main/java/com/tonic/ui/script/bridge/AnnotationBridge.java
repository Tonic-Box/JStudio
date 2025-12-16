package com.tonic.ui.script.bridge;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.Attribute;
import com.tonic.parser.attribute.RuntimeVisibleAnnotationsAttribute;
import com.tonic.parser.attribute.anotation.Annotation;
import com.tonic.parser.attribute.anotation.ElementValuePair;
import com.tonic.parser.constpool.Utf8Item;
import com.tonic.ui.model.ClassEntryModel;
import com.tonic.ui.script.engine.ScriptFunction;
import com.tonic.ui.script.engine.ScriptInterpreter;
import com.tonic.ui.script.engine.ScriptValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class AnnotationBridge {

    private final ScriptInterpreter interpreter;
    private final List<AnnotationHandler> classAnnotationHandlers = new ArrayList<>();
    private final List<AnnotationHandler> methodAnnotationHandlers = new ArrayList<>();
    private final List<AnnotationHandler> fieldAnnotationHandlers = new ArrayList<>();
    private Consumer<String> logCallback;

    public AnnotationBridge(ScriptInterpreter interpreter) {
        this.interpreter = interpreter;
    }

    public void setLogCallback(Consumer<String> callback) {
        this.logCallback = callback;
    }

    public ScriptValue createAnnotationObject() {
        Map<String, ScriptValue> props = new HashMap<>();

        props.put("onClassAnnotation", ScriptValue.function(
            ScriptFunction.native1("onClassAnnotation", this::registerClassAnnotationHandler)
        ));

        props.put("onMethodAnnotation", ScriptValue.function(
            ScriptFunction.native1("onMethodAnnotation", this::registerMethodAnnotationHandler)
        ));

        props.put("onFieldAnnotation", ScriptValue.function(
            ScriptFunction.native1("onFieldAnnotation", this::registerFieldAnnotationHandler)
        ));

        return ScriptValue.object(props);
    }

    private ScriptValue registerClassAnnotationHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onClassAnnotation requires a function argument");
        }
        classAnnotationHandlers.add(new AnnotationHandler(callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerMethodAnnotationHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onMethodAnnotation requires a function argument");
        }
        methodAnnotationHandlers.add(new AnnotationHandler(callback.asFunction()));
        return ScriptValue.NULL;
    }

    private ScriptValue registerFieldAnnotationHandler(ScriptValue callback) {
        if (!callback.isFunction()) {
            throw new RuntimeException("onFieldAnnotation requires a function argument");
        }
        fieldAnnotationHandlers.add(new AnnotationHandler(callback.asFunction()));
        return ScriptValue.NULL;
    }

    public int applyToClass(ClassEntryModel classEntry) {
        int modCount = 0;
        ClassFile classFile = classEntry.getClassFile();

        // Process class annotations
        if (!classAnnotationHandlers.isEmpty()) {
            modCount += processClassAnnotations(classFile);
        }

        // Process method annotations
        if (!methodAnnotationHandlers.isEmpty()) {
            for (MethodEntry method : classFile.getMethods()) {
                modCount += processMemberAnnotations(method.getAttributes(), methodAnnotationHandlers,
                    method.getName(), classFile);
            }
        }

        // Process field annotations
        if (!fieldAnnotationHandlers.isEmpty()) {
            for (FieldEntry field : classFile.getFields()) {
                modCount += processMemberAnnotations(field.getAttributes(), fieldAnnotationHandlers,
                    field.getName(), classFile);
            }
        }

        return modCount;
    }

    private int processClassAnnotations(ClassFile classFile) {
        int modCount = 0;
        List<Attribute> classAttrs = getClassAttributes(classFile);

        for (Attribute attr : classAttrs) {
            if (attr instanceof RuntimeVisibleAnnotationsAttribute) {
                RuntimeVisibleAnnotationsAttribute annoAttr = (RuntimeVisibleAnnotationsAttribute) attr;
                modCount += processAnnotationList(annoAttr.getAnnotations(), classAnnotationHandlers,
                    classFile.getClassName(), classFile);
            }
        }

        return modCount;
    }

    private List<Attribute> getClassAttributes(ClassFile classFile) {
        // Use reflection to access classAttributes since there's no getter
        try {
            java.lang.reflect.Field field = ClassFile.class.getDeclaredField("classAttributes");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<Attribute> attrs = (List<Attribute>) field.get(classFile);
            return attrs != null ? attrs : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private int processMemberAnnotations(List<Attribute> attributes, List<AnnotationHandler> handlers,
                                         String targetName, ClassFile classFile) {
        int modCount = 0;

        for (Attribute attr : attributes) {
            if (attr instanceof RuntimeVisibleAnnotationsAttribute) {
                RuntimeVisibleAnnotationsAttribute annoAttr = (RuntimeVisibleAnnotationsAttribute) attr;
                modCount += processAnnotationList(annoAttr.getAnnotations(), handlers, targetName, classFile);
            }
        }

        return modCount;
    }

    private int processAnnotationList(List<Annotation> annotations, List<AnnotationHandler> handlers,
                                      String targetName, ClassFile classFile) {
        int modCount = 0;

        Iterator<Annotation> iter = annotations.iterator();
        while (iter.hasNext()) {
            Annotation anno = iter.next();
            ScriptValue wrapped = wrapAnnotation(anno, targetName, classFile);

            for (AnnotationHandler handler : handlers) {
                try {
                    List<ScriptValue> args = new ArrayList<>();
                    args.add(wrapped);
                    ScriptValue result = handler.function.call(interpreter, args);

                    if (result == null || result.isNull()) {
                        iter.remove();
                        modCount++;
                        String typeName = resolveAnnotationType(anno, classFile);
                        if (logCallback != null) {
                            logCallback.accept("Removed annotation @" + typeName + " from " + targetName);
                        }
                        break;
                    }
                } catch (Exception e) {
                    if (logCallback != null) {
                        logCallback.accept("Annotation handler error: " + e.getMessage());
                    }
                }
            }
        }

        return modCount;
    }

    private String resolveAnnotationType(Annotation anno, ClassFile classFile) {
        try {
            Object item = classFile.getConstPool().getItem(anno.getTypeIndex());
            if (item instanceof Utf8Item) {
                String type = ((Utf8Item) item).getValue();
                // Convert Ljavax/inject/Named; to Named
                if (type.startsWith("L") && type.endsWith(";")) {
                    type = type.substring(1, type.length() - 1);
                }
                int lastSlash = type.lastIndexOf('/');
                return lastSlash >= 0 ? type.substring(lastSlash + 1) : type;
            }
        } catch (Exception e) {
            // Ignore
        }
        return "Unknown";
    }

    private ScriptValue wrapAnnotation(Annotation anno, String targetName, ClassFile classFile) {
        Map<String, ScriptValue> props = new HashMap<>();

        // Get the raw type descriptor
        String typeDescriptor = "";
        try {
            Object item = classFile.getConstPool().getItem(anno.getTypeIndex());
            if (item instanceof Utf8Item) {
                typeDescriptor = ((Utf8Item) item).getValue();
            }
        } catch (Exception e) {
            // Ignore
        }
        props.put("type", ScriptValue.string(typeDescriptor));

        // Extract simple name
        String simpleName = typeDescriptor;
        if (typeDescriptor.startsWith("L") && typeDescriptor.endsWith(";")) {
            simpleName = typeDescriptor.substring(1, typeDescriptor.length() - 1);
        }
        int lastSlash = simpleName.lastIndexOf('/');
        if (lastSlash >= 0) {
            simpleName = simpleName.substring(lastSlash + 1);
        }
        props.put("simpleName", ScriptValue.string(simpleName));
        props.put("target", ScriptValue.string(targetName));

        // Add element-value pairs
        Map<String, ScriptValue> valuesMap = new HashMap<>();
        List<ElementValuePair> pairs = anno.getElementValuePairs();
        if (pairs != null) {
            for (ElementValuePair pair : pairs) {
                valuesMap.put(pair.getElementName(), convertElementValue(pair.getValue(), classFile));
            }
        }
        props.put("values", ScriptValue.object(valuesMap));

        return ScriptValue.object(props);
    }

    private ScriptValue convertElementValue(com.tonic.parser.attribute.anotation.ElementValue ev, ClassFile classFile) {
        if (ev == null) {
            return ScriptValue.NULL;
        }

        int tag = ev.getTag();
        Object value = ev.getValue();

        switch (tag) {
            case 's': // String
                if (value instanceof Integer) {
                    try {
                        Object item = classFile.getConstPool().getItem((Integer) value);
                        if (item instanceof Utf8Item) {
                            return ScriptValue.string(((Utf8Item) item).getValue());
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
                return ScriptValue.string(String.valueOf(value));

            case 'I': // int
            case 'J': // long
            case 'F': // float
            case 'D': // double
            case 'B': // byte
            case 'S': // short
                if (value instanceof Number) {
                    return ScriptValue.number(((Number) value).doubleValue());
                }
                return ScriptValue.number(0);

            case 'Z': // boolean
                if (value instanceof Boolean) {
                    return ScriptValue.bool((Boolean) value);
                }
                return ScriptValue.bool(false);

            case 'C': // char
                return ScriptValue.string(String.valueOf((char) ((Integer) value).intValue()));

            default:
                return ScriptValue.string(String.valueOf(value));
        }
    }

    public void clearHandlers() {
        classAnnotationHandlers.clear();
        methodAnnotationHandlers.clear();
        fieldAnnotationHandlers.clear();
    }

    public boolean hasHandlers() {
        return !classAnnotationHandlers.isEmpty() ||
               !methodAnnotationHandlers.isEmpty() ||
               !fieldAnnotationHandlers.isEmpty();
    }

    private static class AnnotationHandler {
        final ScriptFunction function;

        AnnotationHandler(ScriptFunction function) {
            this.function = function;
        }
    }
}
