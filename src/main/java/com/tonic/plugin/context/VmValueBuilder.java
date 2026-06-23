package com.tonic.plugin.context;

import com.tonic.analysis.execution.core.BytecodeResult;
import com.tonic.analysis.execution.heap.ArrayInstance;
import com.tonic.analysis.execution.heap.ObjectInstance;
import com.tonic.analysis.execution.heap.SimpleHeapManager;
import com.tonic.model.ClassEntryModel;
import com.tonic.model.FieldEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.plugin.api.VmDebugApi.ArgSpec;
import com.tonic.service.ProjectService;
import com.tonic.ui.vm.VmInstance;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Builds VM-engine-ready argument values from {@link ArgSpec}s, on the interpreter's heap. Each value is a boxed
 * primitive, an {@code ObjectInstance}/{@code ArrayInstance} reference, or null - all of which
 * {@code VMExecutionService}'s arg conversion accepts. Arrays and objects are recursive (elements / constructor
 * args / field values are themselves built).
 */
final class VmValueBuilder {

    private static final String PRIMITIVE_DESCRIPTORS = "ZBCSIJFD";

    private VmValueBuilder() {
    }

    static Object[] build(VmInstance vm, List<ArgSpec> specs) {
        if (specs == null) {
            return new Object[0];
        }
        Object[] out = new Object[specs.size()];
        for (int i = 0; i < specs.size(); i++) {
            out[i] = build(vm, specs.get(i));
        }
        return out;
    }

    /** Builds one value: boxed primitive | ObjectInstance | ArrayInstance | null. */
    static Object build(VmInstance vm, ArgSpec spec) {
        if (spec == null) {
            return null;
        }
        SimpleHeapManager heap = vm.getHeapManager();
        switch (spec.getKind()) {
            case NULL:
                return null;
            case INT:
                return ((Number) spec.getValue()).intValue();
            case LONG:
                return ((Number) spec.getValue()).longValue();
            case FLOAT:
                return ((Number) spec.getValue()).floatValue();
            case DOUBLE:
                return ((Number) spec.getValue()).doubleValue();
            case BYTE:
                return ((Number) spec.getValue()).byteValue();
            case SHORT:
                return ((Number) spec.getValue()).shortValue();
            case BOOLEAN:
                return asBoolean(spec.getValue());
            case CHAR:
                return asChar(spec.getValue());
            case STRING:
                return spec.getValue() == null ? null : heap.internString((String) spec.getValue());
            case ARRAY:
                return buildArray(vm, heap, spec);
            case OBJECT:
                return buildObject(vm, heap, spec);
            default:
                return null;
        }
    }

    private static ArrayInstance buildArray(VmInstance vm, SimpleHeapManager heap, ArgSpec spec) {
        String component = spec.getComponentType();
        List<ArgSpec> elements = spec.getElements() != null ? spec.getElements() : Collections.emptyList();
        ArrayInstance array = heap.newArray(component, elements.size());
        boolean primitive = component.length() == 1 && PRIMITIVE_DESCRIPTORS.indexOf(component.charAt(0)) >= 0;
        for (int i = 0; i < elements.size(); i++) {
            Object value = build(vm, elements.get(i));
            if (primitive) {
                setPrimitiveElement(array, component.charAt(0), i, value);
            } else {
                array.set(i, value);
            }
        }
        return array;
    }

    private static void setPrimitiveElement(ArrayInstance array, char type, int index, Object value) {
        long bits = value instanceof Boolean ? (((Boolean) value) ? 1 : 0)
                : value instanceof Character ? (long) (Character) value
                : asNumber(value).longValue();
        switch (type) {
            case 'I':
                array.setInt(index, (int) bits);
                break;
            case 'J':
                array.setLong(index, bits);
                break;
            case 'F':
                array.setFloat(index, asNumber(value).floatValue());
                break;
            case 'D':
                array.setDouble(index, asNumber(value).doubleValue());
                break;
            case 'B':
                array.setByte(index, (byte) bits);
                break;
            case 'S':
                array.setShort(index, (short) bits);
                break;
            case 'Z':
                array.setBoolean(index, bits != 0);
                break;
            case 'C':
                array.setChar(index, (char) bits);
                break;
            default:
                break;
        }
    }

    /**
     * Narrows a built primitive-array element to {@link Number}, throwing a descriptive
     * {@link IllegalArgumentException} for a malformed spec instead of a raw {@link ClassCastException}.
     */
    private static Number asNumber(Object value) {
        if (value instanceof Number) {
            return (Number) value;
        }
        throw new IllegalArgumentException("Expected a numeric array element but got "
                + (value == null ? "null" : value.getClass().getName()));
    }

    private static ObjectInstance buildObject(VmInstance vm, SimpleHeapManager heap, ArgSpec spec) {
        ObjectInstance object = heap.newObject(spec.getClassName());
        if (spec.getFields() != null) {
            for (Map.Entry<String, ArgSpec> entry : spec.getFields().entrySet()) {
                String descriptor = resolveFieldDescriptor(spec.getClassName(), entry.getKey());
                object.setField(spec.getClassName(), entry.getKey(), descriptor, build(vm, entry.getValue()));
            }
        } else if (spec.getConstructorDescriptor() != null) {
            Object[] ctorArgs = build(vm, spec.getConstructorArgs());
            BytecodeResult result = vm.executeMethod(spec.getClassName(), "<init>", spec.getConstructorDescriptor(),
                    object, ctorArgs);
            if (!result.isSuccess()) {
                String reason = result.hasException() ? String.valueOf(result.getException()) : "unknown";
                throw new IllegalArgumentException("Constructor " + spec.getClassName()
                        + spec.getConstructorDescriptor() + " failed: " + reason);
            }
        }
        return object;
    }

    private static String resolveFieldDescriptor(String className, String fieldName) {
        ProjectModel project = ProjectService.getInstance().getCurrentProject();
        if (project != null) {
            ClassEntryModel entry = project.findClassByName(className);
            if (entry != null) {
                for (FieldEntryModel field : entry.getFields()) {
                    if (field.getName().equals(fieldName)) {
                        return field.getDescriptor();
                    }
                }
            }
        }
        throw new IllegalArgumentException("Field not found: " + className + "." + fieldName);
    }

    private static boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static char asChar(Object value) {
        if (value instanceof Character) {
            return (Character) value;
        }
        if (value instanceof Number) {
            return (char) ((Number) value).intValue();
        }
        String s = String.valueOf(value);
        return s.isEmpty() ? '\0' : s.charAt(0);
    }
}
