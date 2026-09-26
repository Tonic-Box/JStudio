package com.tonic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.parser.ClassFile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("an enum made by the New Class dialog loads and behaves as an enum")
class EnumCreationTest
{

    private static final class Loader extends ClassLoader
    {
        Class<?> define(String binaryName, byte[] bytes)
        {
            return defineClass(binaryName, bytes, 0, bytes.length);
        }
    }

    @Test
    @DisplayName("the class verifies, values() is empty and valueOf rejects unknown names")
    void generatedEnumVerifies() throws Exception
    {
        ClassCreationService.ClassCreationParams params = ClassCreationService.ClassCreationParams.builder("test/gen/Color").classType(ClassCreationService.ClassType.ENUM).build();
        ClassFile cf = ClassCreationService.getInstance().createClass(params);

        Class<?> type = new Loader().define("test.gen.Color", cf.write());

        assertTrue(type.isEnum());
        Object values = type.getMethod("values").invoke(null);
        assertEquals(0, ((Object[]) values).length);
        Method valueOf = type.getMethod("valueOf", String.class);
        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> valueOf.invoke(null, "RED"));
        assertTrue(thrown.getCause() instanceof IllegalArgumentException);
    }
}
