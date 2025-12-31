package com.tonic.ui.deobfuscation.model;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class DecryptorCandidate {

    private final ClassFile classFile;
    private final MethodEntry method;
    private final DecryptorType type;
    private final double confidence;
    private final List<String> indicators;

    public DecryptorCandidate(ClassFile classFile, MethodEntry method,
                              DecryptorType type, double confidence) {
        this.classFile = classFile;
        this.method = method;
        this.type = type;
        this.confidence = confidence;
        this.indicators = new ArrayList<>();
    }

    public void addIndicator(String indicator) {
        indicators.add(indicator);
    }

    public String getClassName() {
        return classFile.getClassName();
    }

    public String getMethodName() {
        return method.getName();
    }

    public String getDescriptor() {
        return method.getDesc();
    }

    public String getSignature() {
        return getClassName().replace('/', '.') + "." + getMethodName() + getDescriptor();
    }

    public String getSimpleSignature() {
        String className = getClassName();
        int lastSlash = className.lastIndexOf('/');
        String simpleName = lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
        return simpleName + "." + getMethodName() + "()";
    }

    public boolean isStatic() {
        return (method.getAccess() & 0x0008) != 0;
    }

    @Getter
    public enum DecryptorType {
        STRING_TO_STRING("String → String", "(Ljava/lang/String;)Ljava/lang/String;"),
        STRING_INT_TO_STRING("String, int → String", "(Ljava/lang/String;I)Ljava/lang/String;"),
        INT_TO_STRING("int → String (index-based)", "(I)Ljava/lang/String;"),
        BYTES_TO_STRING("byte[] → String", "([B)Ljava/lang/String;"),
        STRING_TO_BYTES("String → byte[]", "(Ljava/lang/String;)[B"),
        CHAR_ARRAY_TO_STRING("char[] → String", "([C)Ljava/lang/String;"),
        UNKNOWN("Unknown pattern", null);

        private final String description;
        private final String expectedDescriptor;

        DecryptorType(String description, String expectedDescriptor) {
            this.description = description;
            this.expectedDescriptor = expectedDescriptor;
        }

        public static DecryptorType fromDescriptor(String descriptor) {
            for (DecryptorType type : values()) {
                if (type.expectedDescriptor != null && type.expectedDescriptor.equals(descriptor)) {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }

    @Override
    public String toString() {
        return String.format("%s [%s] (%.0f%% confidence)",
            getSimpleSignature(), type.getDescription(), confidence * 100);
    }
}
