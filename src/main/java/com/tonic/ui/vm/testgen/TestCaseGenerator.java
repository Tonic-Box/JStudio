package com.tonic.ui.vm.testgen;

import com.tonic.ui.vm.model.ExecutionResult;
import com.tonic.ui.vm.model.MethodCall;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class TestCaseGenerator {

    @Getter
    public enum JUnitVersion {
        JUNIT4("JUnit 4", "org.junit.Test", "org.junit.Assert"),
        JUNIT5("JUnit 5 (Jupiter)", "org.junit.jupiter.api.Test", "org.junit.jupiter.api.Assertions");

        private final String displayName;
        private final String testAnnotationImport;
        private final String assertionsImport;

        JUnitVersion(String displayName, String testAnnotationImport, String assertionsImport) {
            this.displayName = displayName;
            this.testAnnotationImport = testAnnotationImport;
            this.assertionsImport = assertionsImport;
        }

    }

    @Getter
    public static class GeneratedTest {
        private final String code;
        private final String suggestedFileName;
        private final String packageName;
        private final String className;

        public GeneratedTest(String code, String suggestedFileName, String packageName, String className) {
            this.code = code;
            this.suggestedFileName = suggestedFileName;
            this.packageName = packageName;
            this.className = className;
        }

    }

    public GeneratedTest generate(MethodCall call, JUnitVersion version,
                                   String testClassName, String testMethodName) {
        StringBuilder sb = new StringBuilder();
        List<String> imports = new ArrayList<>();

        String targetClass = call.getOwnerClass().replace('/', '.');
        String packageName = extractPackage(targetClass);
        String simpleTargetClass = extractSimpleName(targetClass);

        imports.add(version.getTestAnnotationImport());
        if (version == JUnitVersion.JUNIT4) {
            imports.add("static " + version.getAssertionsImport() + ".*");
        } else {
            imports.add("static " + version.getAssertionsImport() + ".*");
        }

        if (!packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }

        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("\n");

        sb.append("public class ").append(testClassName).append(" {\n\n");

        generateTestMethod(sb, call, version, testMethodName, simpleTargetClass, call.isStaticMethod());

        sb.append("}\n");

        String suggestedFileName = testClassName + ".java";
        return new GeneratedTest(sb.toString(), suggestedFileName, packageName, testClassName);
    }

    public GeneratedTest generate(ExecutionResult result, String entryClass,
                                   String entryMethod, String descriptor,
                                   Object[] args, JUnitVersion version,
                                   String testClassName, String testMethodName) {
        StringBuilder sb = new StringBuilder();
        List<String> imports = new ArrayList<>();

        String targetClass = entryClass.replace('/', '.');
        String packageName = extractPackage(targetClass);
        String simpleTargetClass = extractSimpleName(targetClass);

        imports.add(version.getTestAnnotationImport());
        if (version == JUnitVersion.JUNIT4) {
            imports.add("static " + version.getAssertionsImport() + ".*");
        } else {
            imports.add("static " + version.getAssertionsImport() + ".*");
        }

        boolean hasException = result.getException() != null;
        if (hasException && version == JUnitVersion.JUNIT5) {
            imports.add("static org.junit.jupiter.api.Assertions.assertThrows");
        }

        if (!packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }

        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("\n");

        sb.append("public class ").append(testClassName).append(" {\n\n");

        generateTestMethodFromResult(sb, result, version, testMethodName,
                                      simpleTargetClass, entryMethod, descriptor, args);

        sb.append("}\n");

        String suggestedFileName = testClassName + ".java";
        return new GeneratedTest(sb.toString(), suggestedFileName, packageName, testClassName);
    }

    private void generateTestMethod(StringBuilder sb, MethodCall call, JUnitVersion version,
                                     String testMethodName, String targetClass, boolean isStatic) {
        boolean hasException = call.isExceptional();
        Object returnValue = call.getReturnValue();
        Object[] args = call.getArguments();

        if (hasException && version == JUnitVersion.JUNIT4) {
            sb.append("    @Test(expected = RuntimeException.class)\n");
        } else {
            sb.append("    @Test\n");
        }

        if (version == JUnitVersion.JUNIT4) {
            sb.append("    public void ").append(testMethodName).append("() {\n");
        } else {
            sb.append("    void ").append(testMethodName).append("() {\n");
        }

        String argsString = formatArguments(args);

        if (hasException && version == JUnitVersion.JUNIT5) {
            sb.append("        assertThrows(RuntimeException.class, () -> {\n");
            sb.append("            ").append(targetClass).append(".").append(call.getMethodName());
            sb.append("(").append(argsString).append(");\n");
            sb.append("        });\n");
        } else if (returnValue != null) {
            String returnType = inferReturnType(returnValue);
            sb.append("        ").append(returnType).append(" result = ");
            sb.append(targetClass).append(".").append(call.getMethodName());
            sb.append("(").append(argsString).append(");\n");
            sb.append("        assertEquals(").append(formatLiteral(returnValue)).append(", result);\n");
        } else {
            sb.append("        ").append(targetClass).append(".").append(call.getMethodName());
            sb.append("(").append(argsString).append(");\n");
            if (!hasException) {
                sb.append("        // Method completed without exception\n");
            }
        }

        sb.append("    }\n");
    }

    private void generateTestMethodFromResult(StringBuilder sb, ExecutionResult result,
                                               JUnitVersion version, String testMethodName,
                                               String targetClass, String methodName,
                                               String descriptor, Object[] args) {
        boolean hasException = result.getException() != null;
        Object returnValue = result.getReturnValue();

        String exceptionClass = "RuntimeException";
        if (hasException) {
            String excMessage = result.getException().getMessage();
            if (excMessage != null && excMessage.contains("VM Exception:")) {
                int colonIdx = excMessage.indexOf(':');
                if (colonIdx > 0 && excMessage.length() > colonIdx + 2) {
                    String excPart = excMessage.substring(colonIdx + 1).trim();
                    int spaceIdx = excPart.indexOf(' ');
                    if (spaceIdx > 0) {
                        exceptionClass = excPart.substring(0, spaceIdx);
                        if (exceptionClass.contains("/")) {
                            exceptionClass = extractSimpleName(exceptionClass.replace('/', '.'));
                        }
                    }
                }
            }
        }

        if (hasException && version == JUnitVersion.JUNIT4) {
            sb.append("    @Test(expected = ").append(exceptionClass).append(".class)\n");
        } else {
            sb.append("    @Test\n");
        }

        if (version == JUnitVersion.JUNIT4) {
            sb.append("    public void ").append(testMethodName).append("() {\n");
        } else {
            sb.append("    void ").append(testMethodName).append("() {\n");
        }

        String argsString = formatArguments(args);

        if (hasException && version == JUnitVersion.JUNIT5) {
            sb.append("        assertThrows(").append(exceptionClass).append(".class, () -> {\n");
            sb.append("            ").append(targetClass).append(".").append(methodName);
            sb.append("(").append(argsString).append(");\n");
            sb.append("        });\n");
        } else if (returnValue != null) {
            String returnType = inferReturnType(returnValue);
            sb.append("        ").append(returnType).append(" result = ");
            sb.append(targetClass).append(".").append(methodName);
            sb.append("(").append(argsString).append(");\n");
            sb.append("        assertEquals(").append(formatLiteral(returnValue)).append(", result);\n");
        } else {
            sb.append("        ").append(targetClass).append(".").append(methodName);
            sb.append("(").append(argsString).append(");\n");
            if (!hasException && "V".equals(result.getReturnType())) {
                sb.append("        // Void method completed successfully\n");
            }
        }

        sb.append("    }\n");
    }

    private String formatArguments(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatLiteral(args[i]));
        }
        return sb.toString();
    }

    public String formatLiteral(Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return String.valueOf(value);
        }

        if (value instanceof Long) {
            return value + "L";
        }

        if (value instanceof Float) {
            Float f = (Float) value;
            if (f.isNaN()) return "Float.NaN";
            if (f.isInfinite()) return f > 0 ? "Float.POSITIVE_INFINITY" : "Float.NEGATIVE_INFINITY";
            return value + "f";
        }

        if (value instanceof Double) {
            Double d = (Double) value;
            if (d.isNaN()) return "Double.NaN";
            if (d.isInfinite()) return d > 0 ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY";
            return value + "d";
        }

        if (value instanceof Boolean) {
            return String.valueOf(value);
        }

        if (value instanceof Character) {
            return "'" + escapeChar((Character) value) + "'";
        }

        if (value instanceof String) {
            return "\"" + escapeString((String) value) + "\"";
        }

        return "/* TODO: provide value for " + value.getClass().getSimpleName() + " */";
    }

    private String escapeChar(char c) {
        switch (c) {
            case '\\': return "\\\\";
            case '\'': return "\\'";
            case '\n': return "\\n";
            case '\r': return "\\r";
            case '\t': return "\\t";
            case '\b': return "\\b";
            case '\f': return "\\f";
            default:
                if (c < 32 || c > 126) {
                    return String.format("\\u%04x", (int) c);
                }
                return String.valueOf(c);
        }
    }

    private String escapeString(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private String inferReturnType(Object value) {
        if (value instanceof Integer) return "int";
        if (value instanceof Long) return "long";
        if (value instanceof Float) return "float";
        if (value instanceof Double) return "double";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof Character) return "char";
        if (value instanceof Byte) return "byte";
        if (value instanceof Short) return "short";
        if (value instanceof String) return "String";
        return "Object";
    }

    private String extractPackage(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        if (lastDot < 0) return "";
        return fqcn.substring(0, lastDot);
    }

    private String extractSimpleName(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        int lastSlash = fqcn.lastIndexOf('/');
        int last = Math.max(lastDot, lastSlash);
        if (last < 0) return fqcn;
        return fqcn.substring(last + 1);
    }

    public String suggestTestClassName(String targetClassName) {
        return extractSimpleName(targetClassName) + "Test";
    }

    public String suggestTestMethodName(String methodName) {
        if (methodName == null || methodName.isEmpty()) {
            return "testMethod";
        }
        String capitalized = Character.toUpperCase(methodName.charAt(0)) +
                             (methodName.length() > 1 ? methodName.substring(1) : "");
        return "test" + capitalized;
    }
}
