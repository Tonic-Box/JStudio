package com.tonic.ui.deobfuscation.detection;

import com.tonic.parser.ClassFile;
import com.tonic.parser.FieldEntry;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.attribute.CodeAttribute;
import com.tonic.ui.deobfuscation.DeobfuscationService;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClinitAnalyzer {

    private static final int PUTSTATIC = 0xB3;
    private static final int INVOKESTATIC = 0xB8;

    public ClinitAnalysisResult analyze(ClassFile classFile) {
        ClinitAnalysisResult result = new ClinitAnalysisResult(classFile);

        MethodEntry clinit = findClinit(classFile);
        if (clinit == null) {
            return result;
        }

        result.setHasClinit(true);

        CodeAttribute code = clinit.getCodeAttribute();
        if (code == null) {
            return result;
        }

        analyzeClinitBytecode(code, result);

        return result;
    }

    private MethodEntry findClinit(ClassFile classFile) {
        for (MethodEntry method : classFile.getMethods()) {
            if ("<clinit>".equals(method.getName()) && "()V".equals(method.getDesc())) {
                return method;
            }
        }
        return null;
    }

    private void analyzeClinitBytecode(CodeAttribute code, ClinitAnalysisResult result) {
        try {
            byte[] bytecode = code.getCode();
            if (bytecode == null || bytecode.length == 0) {
                return;
            }

            boolean hasInvokeStatic = false;
            boolean hasPutStatic = false;

            for (byte b : bytecode) {
                int opcode = Byte.toUnsignedInt(b);

                if (opcode == INVOKESTATIC) {
                    hasInvokeStatic = true;
                }
                if (opcode == PUTSTATIC) {
                    hasPutStatic = true;
                }
            }

            if (hasInvokeStatic && hasPutStatic) {
                result.setLikelyHasDecryptorCalls(true);
            }

        } catch (Exception e) {
            System.out.println("[ClinitAnalyzer] Error analyzing bytecode: " + e.getMessage());
        }
    }

    public Map<String, Object> executeAndCapture(ClassFile classFile) {
        DeobfuscationService service = DeobfuscationService.getInstance();
        return service.executeClinitAndCaptureFields(classFile);
    }

    public List<FieldStringMapping> captureStringFields(ClassFile classFile) {
        List<FieldStringMapping> mappings = new ArrayList<>();

        Map<String, Object> captured = executeAndCapture(classFile);
        String className = classFile.getClassName();

        for (FieldEntry field : classFile.getFields()) {
            String fieldName = field.getName();
            String fieldDesc = field.getDesc();

            if (!"Ljava/lang/String;".equals(fieldDesc)) {
                continue;
            }

            Object value = captured.get(fieldName);
            if (value instanceof String) {
                mappings.add(new FieldStringMapping(
                    className, fieldName, fieldDesc, (String) value
                ));
            }
        }

        return mappings;
    }

    public static class ClinitAnalysisResult {
        @Getter
        private final ClassFile classFile;
        private boolean hasClinit;
        @Getter
        private boolean likelyHasDecryptorCalls;
        @Getter
        private final Map<String, String> decryptedFields;
        @Getter
        private final List<String> decryptorCalls;

        public ClinitAnalysisResult(ClassFile classFile) {
            this.classFile = classFile;
            this.hasClinit = false;
            this.likelyHasDecryptorCalls = false;
            this.decryptedFields = new HashMap<>();
            this.decryptorCalls = new ArrayList<>();
        }

        public boolean hasClinit() {
            return hasClinit;
        }

        public void setHasClinit(boolean hasClinit) {
            this.hasClinit = hasClinit;
        }

        public void setLikelyHasDecryptorCalls(boolean value) {
            this.likelyHasDecryptorCalls = value;
        }

        public void addDecryptedField(String fieldName, String decryptorCall) {
            decryptedFields.put(fieldName, decryptorCall);
            if (!decryptorCalls.contains(decryptorCall)) {
                decryptorCalls.add(decryptorCall);
            }
        }

        public int getDecryptedFieldCount() {
            return decryptedFields.size();
        }

        public boolean hasDecryptedFields() {
            return !decryptedFields.isEmpty();
        }
    }

    @Getter
    public static class FieldStringMapping {
        private final String className;
        private final String fieldName;
        private final String descriptor;
        private final String value;

        public FieldStringMapping(String className, String fieldName,
                                   String descriptor, String value) {
            this.className = className;
            this.fieldName = fieldName;
            this.descriptor = descriptor;
            this.value = value;
        }

        public String getFullFieldName() {
            return className + "." + fieldName;
        }

        @Override
        public String toString() {
            return getFullFieldName() + " = \"" + value + "\"";
        }
    }
}
