package com.tonic.ui.vm.testgen;

import com.tonic.ui.vm.model.ExecutionResult;
import com.tonic.ui.vm.model.HeapReference;
import com.tonic.ui.vm.model.MethodCall;
import com.tonic.ui.vm.testgen.objectspec.ObjectFactory.ConstructorCall;
import com.tonic.ui.vm.testgen.objectspec.ObjectFactory.ExpressionObject;
import com.tonic.ui.vm.testgen.objectspec.ObjectFactory.FactoryCall;
import com.tonic.util.DescriptorParser;
import java.lang.reflect.Array;
import java.util.List;
import lombok.Getter;

/** Generates JUnit 4 or 5 test source that replays recorded calls, typing every literal from the method descriptor, and asserts each return value or exception. */
public class TestCaseGenerator
{

    /** A supported JUnit version, with its display name and the imports its tests need. */
    @Getter
    public enum JUnitVersion
    {
        JUNIT4("JUnit 4", "org.junit.Test", "org.junit.Assert"),
        JUNIT5("JUnit 5 (Jupiter)", "org.junit.jupiter.api.Test", "org.junit.jupiter.api.Assertions");

        private final String displayName;
        private final String testAnnotationImport;
        private final String assertionsImport;

        JUnitVersion(String displayName, String testAnnotationImport, String assertionsImport)
        {
            this.displayName = displayName;
            this.testAnnotationImport = testAnnotationImport;
            this.assertionsImport = assertionsImport;
        }

    }

    /** A generated test class: its source, file name, package and class name. */
    @Getter
    public static class GeneratedTest
    {
        private final String code;
        private final String suggestedFileName;
        private final String packageName;
        private final String className;

        /**
         * Creates a generated test.
         *
         * @param code the test class source
         * @param suggestedFileName the file name to save it as
         * @param packageName the package, empty for the default package
         * @param className the test class's simple name
         */
        public GeneratedTest(String code, String suggestedFileName, String packageName, String className)
        {
            this.code = code;
            this.suggestedFileName = suggestedFileName;
            this.packageName = packageName;
            this.className = className;
        }

    }

    /** One call to replay: the method, its receiver and arguments, and the outcome to assert. */
    @Getter
    public static final class TestCase
    {
        private final String ownerClass;
        private final String methodName;
        private final String descriptor;
        private final boolean staticMethod;
        private final Object receiver;
        private final Object[] args;
        private final boolean throwing;
        private final String exceptionClass;
        private final Object returnValue;

        private TestCase(String ownerClass, String methodName, String descriptor, boolean staticMethod, Object receiver, Object[] args, boolean throwing, String exceptionClass, Object returnValue)
        {
            this.ownerClass = ownerClass;
            this.methodName = methodName;
            this.descriptor = descriptor;
            this.staticMethod = staticMethod;
            this.receiver = receiver;
            this.args = args != null ? args.clone() : new Object[0];
            this.throwing = throwing;
            this.exceptionClass = exceptionClass;
            this.returnValue = returnValue;
        }

        /**
         * Builds a case from a call recorded in a trace.
         *
         * @param call the recorded call
         * @return the case, expecting any Throwable when the call threw
         * @throws IllegalArgumentException if the call is an instance call, whose receiver state a trace does not record
         */
        public static TestCase fromCall(MethodCall call)
        {
            if (!call.isStaticMethod())
            {
                throw new IllegalArgumentException("A trace does not record the receiver of an instance call, so " + call.getMethodName() + " cannot be replayed");
            }
            return new TestCase(call.getOwnerClass(), call.getMethodName(), call.getDescriptor(), true, null, call.getArguments(), call.isExceptional(), "java/lang/Throwable", call.getReturnValue());
        }

        /**
         * Builds a case from a VM run.
         *
         * @param result the run's result
         * @param ownerClass the class's internal name, with slashes
         * @param methodName the method's name
         * @param descriptor the method's descriptor
         * @param staticMethod whether the method is static
         * @param receiver for an instance method the constructor, factory or expression spec the receiver was built from; ignored for a static method
         * @param args the arguments the method was called with
         * @return the case, expecting the thrown VM exception's class when the method threw
         * @throws IllegalArgumentException if the run did not finish by returning or throwing inside the VM, such as a failed argument conversion or a hit instruction limit
         */
        public static TestCase fromResult(ExecutionResult result, String ownerClass, String methodName, String descriptor, boolean staticMethod, Object receiver, Object[] args)
        {
            if (result.isSuccess())
            {
                return new TestCase(ownerClass, methodName, descriptor, staticMethod, staticMethod ? null : receiver, args, false, null, result.getReturnValue());
            }
            String exceptionClass = vmExceptionClass(result.getException());
            if (exceptionClass == null)
            {
                String reason = result.getException() != null ? result.getException().getMessage() : "no result";
                throw new IllegalArgumentException("The run did not complete in the VM: " + reason);
            }
            return new TestCase(ownerClass, methodName, descriptor, staticMethod, staticMethod ? null : receiver, args, true, exceptionClass, null);
        }

        private static String vmExceptionClass(Throwable exception)
        {
            String message = exception != null ? exception.getMessage() : null;
            String prefix = "VM Exception: ";
            if (message == null || !message.startsWith(prefix))
            {
                return null;
            }
            String rest = message.substring(prefix.length()).trim();
            int end = 0;
            while (end < rest.length() && (Character.isJavaIdentifierPart(rest.charAt(end)) || rest.charAt(end) == '/' || rest.charAt(end) == '.' || rest.charAt(end) == '$'))
            {
                end++;
            }
            return end > 0 ? rest.substring(0, end).replace('.', '/') : "java/lang/Throwable";
        }
    }

    /**
     * Generates a test class with one test per case, all in the first case's package.
     *
     * @param cases the calls to replay, at least one, all on the same class
     * @param version the JUnit version to target
     * @param testClassName the test class's simple name
     * @param testMethodName the test method's name; with several cases each gets a numbered suffix
     * @return the generated test
     * @throws IllegalArgumentException if there are no cases or a receiver, argument or expected value has no source form
     */
    public GeneratedTest generate(List<TestCase> cases, JUnitVersion version, String testClassName, String testMethodName)
    {
        if (cases.isEmpty())
        {
            throw new IllegalArgumentException("No test cases");
        }
        String owner = cases.get(0).getOwnerClass();
        String packageName = packageOf(owner);

        StringBuilder sb = new StringBuilder();
        if (!packageName.isEmpty())
        {
            sb.append("package ").append(packageName).append(";\n\n");
        }
        sb.append("import ").append(version.getTestAnnotationImport()).append(";\n");
        sb.append("import static ").append(version.getAssertionsImport()).append(".*;\n\n");
        sb.append("public class ").append(testClassName).append(" {\n\n");

        for (int i = 0; i < cases.size(); i++)
        {
            String name = cases.size() == 1 ? testMethodName : testMethodName + "_" + (i + 1);
            appendTest(sb, cases.get(i), version, name, packageName);
            if (i + 1 < cases.size())
            {
                sb.append("\n");
            }
        }

        sb.append("}\n");
        return new GeneratedTest(sb.toString(), testClassName + ".java", packageName, testClassName);
    }

    private void appendTest(StringBuilder sb, TestCase testCase, JUnitVersion version, String name, String packageName)
    {
        String exceptionType = testCase.isThrowing() ? sourceType(testCase.getExceptionClass(), packageName) : null;
        if (testCase.isThrowing() && version == JUnitVersion.JUNIT4)
        {
            sb.append("    @Test(expected = ").append(exceptionType).append(".class)\n");
        }
        else
        {
            sb.append("    @Test\n");
        }
        sb.append("    ").append(version == JUnitVersion.JUNIT4 ? "public " : "").append("void ").append(name).append("() throws Exception {\n");

        List<String> paramTypes = DescriptorParser.parameterDescriptors(testCase.getDescriptor());
        Object[] args = testCase.getArgs();
        if (args.length != paramTypes.size())
        {
            throw new IllegalArgumentException("Expected " + paramTypes.size() + " arguments but got " + args.length);
        }
        StringBuilder argList = new StringBuilder();
        for (int i = 0; i < args.length; i++)
        {
            if (i > 0)
            {
                argList.append(", ");
            }
            argList.append(literal(args[i], paramTypes.get(i), packageName));
        }

        String target;
        if (testCase.isStaticMethod())
        {
            target = sourceType(testCase.getOwnerClass(), packageName);
        }
        else
        {
            String ownerType = sourceType(testCase.getOwnerClass(), packageName);
            sb.append("        ").append(ownerType).append(" target = ").append(literal(testCase.getReceiver(), "L" + testCase.getOwnerClass() + ";", packageName)).append(";\n");
            target = "target";
        }
        String call = target + "." + testCase.getMethodName() + "(" + argList + ")";

        String returnType = DescriptorParser.returnDescriptor(testCase.getDescriptor());
        if (testCase.isThrowing())
        {
            if (version == JUnitVersion.JUNIT5)
            {
                sb.append("        assertThrows(").append(exceptionType).append(".class, () -> ").append(call).append(");\n");
            }
            else
            {
                sb.append("        ").append(call).append(";\n");
            }
        }
        else if ("V".equals(returnType))
        {
            sb.append("        ").append(call).append(";\n");
        }
        else
        {
            sb.append("        ").append(assertion(testCase.getReturnValue(), returnType, call, packageName)).append(";\n");
        }
        sb.append("    }\n");
    }

    private String assertion(Object expected, String type, String call, String packageName)
    {
        switch (type)
        {
            case "Z":
                return (Boolean.TRUE.equals(expected) ? "assertTrue(" : "assertFalse(") + call + ")";
            case "F":
                return "assertEquals(" + literal(expected, type, packageName) + ", " + call + ", 0.0f)";
            case "D":
                return "assertEquals(" + literal(expected, type, packageName) + ", " + call + ", 0.0)";
            case "B":
            case "S":
            case "C":
            case "I":
            case "J":
                return "assertEquals(" + literal(expected, type, packageName) + ", " + call + ")";
            default:
                break;
        }
        if (expected == null)
        {
            return "assertNull(" + call + ")";
        }
        if (expected instanceof HeapReference || !hasLiteral(expected, type))
        {
            return "assertNotNull(" + call + ")";
        }
        if (type.startsWith("["))
        {
            String component = type.substring(1);
            String delta = "F".equals(component) ? ", 0.0f" : "D".equals(component) ? ", 0.0" : "";
            return "assertArrayEquals(" + literal(expected, type, packageName) + ", " + call + delta + ")";
        }
        return "assertEquals(" + literal(expected, type, packageName) + ", " + call + ")";
    }

    private static boolean hasLiteral(Object value, String type)
    {
        if (value == null)
        {
            return true;
        }
        if (value instanceof HeapReference)
        {
            return false;
        }
        if (value.getClass().isArray())
        {
            String component = type.startsWith("[") ? type.substring(1) : "Ljava/lang/Object;";
            for (int i = 0; i < Array.getLength(value); i++)
            {
                if (!hasLiteral(Array.get(value, i), component))
                {
                    return false;
                }
            }
            return type.startsWith("[");
        }
        return value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Character;
    }

    /**
     * Formats a value as Java source of a declared type: narrowed and cast primitives, escaped strings, typed array creations, constructor and factory calls, expressions, and casted nulls.
     *
     * @param value the value
     * @param type the declared type descriptor
     * @param packageName the package the source is written in, whose classes need no qualification
     * @return the source expression
     * @throws IllegalArgumentException if the value has no source form, such as a VM object, a field-injected object or null for a primitive
     */
    public String literal(Object value, String type, String packageName)
    {
        if (type.length() == 1)
        {
            return primitiveLiteral(value, type.charAt(0));
        }
        if (value == null)
        {
            return "(" + sourceTypeOfDescriptor(type, packageName) + ") null";
        }
        if (value instanceof String)
        {
            return "\"" + escapeString((String) value) + "\"";
        }
        if (value instanceof ConstructorCall)
        {
            ConstructorCall call = (ConstructorCall) value;
            return "new " + sourceType(call.getTypeName(), packageName) + "(" + arguments(call.getArgs(), call.getDescriptor(), packageName) + ")";
        }
        if (value instanceof FactoryCall)
        {
            FactoryCall call = (FactoryCall) value;
            return sourceType(call.getTypeName(), packageName) + "." + call.getMethodName() + "(" + arguments(call.getArgs(), call.getDescriptor(), packageName) + ")";
        }
        if (value instanceof ExpressionObject)
        {
            return "(" + ((ExpressionObject) value).getExpression() + ")";
        }
        if (value.getClass().isArray() && type.startsWith("["))
        {
            String component = type.substring(1);
            StringBuilder sb = new StringBuilder("new ").append(sourceTypeOfDescriptor(type, packageName)).append("{");
            for (int i = 0; i < Array.getLength(value); i++)
            {
                if (i > 0)
                {
                    sb.append(", ");
                }
                sb.append(literal(Array.get(value, i), component, packageName));
            }
            return sb.append("}").toString();
        }
        throw new IllegalArgumentException(value + " has no source form as " + DescriptorParser.formatFieldDescriptor(type));
    }

    private String arguments(Object[] args, String descriptor, String packageName)
    {
        List<String> types = DescriptorParser.parameterDescriptors(descriptor);
        if (types.size() != args.length)
        {
            throw new IllegalArgumentException("Expected " + types.size() + " arguments for " + descriptor + " but got " + args.length);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < args.length; i++)
        {
            if (i > 0)
            {
                sb.append(", ");
            }
            sb.append(literal(args[i], types.get(i), packageName));
        }
        return sb.toString();
    }

    private String primitiveLiteral(Object value, char tag)
    {
        if (tag == 'Z')
        {
            if (value instanceof Boolean)
            {
                return String.valueOf(value);
            }
            return String.valueOf(number(value, tag).intValue() != 0);
        }
        if (tag == 'C')
        {
            char c = value instanceof Character ? (Character) value : (char) number(value, tag).intValue();
            return "'" + escapeChar(c) + "'";
        }
        Number n = value instanceof Character ? Integer.valueOf((Character) value) : number(value, tag);
        switch (tag)
        {
            case 'B':
                return "(byte) " + n.byteValue();
            case 'S':
                return "(short) " + n.shortValue();
            case 'J':
                return n.longValue() + "L";
            case 'F':
                return floatLiteral(n.floatValue());
            case 'D':
                return doubleLiteral(n.doubleValue());
            default:
                return String.valueOf(n.intValue());
        }
    }

    private static Number number(Object value, char tag)
    {
        if (value instanceof Number)
        {
            return (Number) value;
        }
        if (value instanceof Boolean)
        {
            return (Boolean) value ? 1 : 0;
        }
        throw new IllegalArgumentException((value == null ? "null" : value.getClass().getSimpleName() + " " + value) + " is not a " + DescriptorParser.formatFieldDescriptor(String.valueOf(tag)));
    }

    private static String floatLiteral(float f)
    {
        if (Float.isNaN(f))
        {
            return "Float.NaN";
        }
        if (Float.isInfinite(f))
        {
            return f > 0 ? "Float.POSITIVE_INFINITY" : "Float.NEGATIVE_INFINITY";
        }
        return f + "f";
    }

    private static String doubleLiteral(double d)
    {
        if (Double.isNaN(d))
        {
            return "Double.NaN";
        }
        if (Double.isInfinite(d))
        {
            return d > 0 ? "Double.POSITIVE_INFINITY" : "Double.NEGATIVE_INFINITY";
        }
        return d + "d";
    }

    private static String sourceTypeOfDescriptor(String descriptor, String packageName)
    {
        int dims = 0;
        while (descriptor.charAt(dims) == '[')
        {
            dims++;
        }
        String element = descriptor.substring(dims);
        String name = element.startsWith("L") ? sourceType(element.substring(1, element.length() - 1), packageName) : DescriptorParser.formatFieldDescriptor(element);
        return name + "[]".repeat(dims);
    }

    private static String sourceType(String internalName, String packageName)
    {
        String dotted = internalName.replace('/', '.').replace('$', '.');
        String ownPackage = packageOf(internalName);
        if (ownPackage.equals(packageName) || ownPackage.equals("java.lang"))
        {
            return dotted.substring(ownPackage.isEmpty() ? 0 : ownPackage.length() + 1);
        }
        return dotted;
    }

    private static String packageOf(String internalName)
    {
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash < 0 ? "" : internalName.substring(0, lastSlash).replace('/', '.');
    }

    private static String escapeChar(char c)
    {
        if (c == '\'')
        {
            return "\\'";
        }
        if (c == '"')
        {
            return "\"";
        }
        return escape(c);
    }

    private static String escapeString(String s)
    {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray())
        {
            sb.append(c == '"' ? "\\\"" : escape(c));
        }
        return sb.toString();
    }

    private static String escape(char c)
    {
        switch (c)
        {
            case '\\':
                return "\\\\";
            case '\n':
                return "\\n";
            case '\r':
                return "\\r";
            case '\t':
                return "\\t";
            case '\b':
                return "\\b";
            case '\f':
                return "\\f";
            default:
                if (c < 32 || c > 126)
                {
                    return String.format("\\u%04x", (int) c);
                }
                return String.valueOf(c);
        }
    }

    /**
     * Suggests a test class name.
     *
     * @param targetClassName the class under test, with dots or slashes
     * @return the simple name followed by Test
     */
    public String suggestTestClassName(String targetClassName)
    {
        String name = targetClassName.replace('.', '/');
        return name.substring(name.lastIndexOf('/') + 1) + "Test";
    }

    /**
     * Suggests a test method name.
     *
     * @param methodName the method under test, or null
     * @return test followed by the capitalized name, or testMethod when the name is null or empty
     */
    public String suggestTestMethodName(String methodName)
    {
        if (methodName == null || methodName.isEmpty())
        {
            return "testMethod";
        }
        return "test" + Character.toUpperCase(methodName.charAt(0)) + methodName.substring(1);
    }
}
