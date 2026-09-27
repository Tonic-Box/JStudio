package com.tonic.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DescriptorParserTest
{

    @ParameterizedTest
    @CsvSource({
            "B, byte",
            "C, char",
            "D, double",
            "F, float",
            "I, int",
            "J, long",
            "S, short",
            "Z, boolean",
            "V, void"
    })
    void testPrimitiveTypes(String descriptor, String expected)
    {
        assertEquals(expected, DescriptorParser.formatFieldDescriptor(descriptor));
    }

    @Test
    void testSimpleClassName()
    {
        assertEquals("String", DescriptorParser.formatFieldDescriptor("Ljava/lang/String;"));
    }

    @Test
    void testNestedClassName()
    {
        assertEquals("ArrayList", DescriptorParser.formatFieldDescriptor("Ljava/util/ArrayList;"));
    }

    @Test
    void testDefaultPackageClass()
    {
        assertEquals("MyClass", DescriptorParser.formatFieldDescriptor("LMyClass;"));
    }

    @Test
    void testPrimitiveArray()
    {
        assertEquals("int[]", DescriptorParser.formatFieldDescriptor("[I"));
    }

    @Test
    void testMultiDimensionalPrimitiveArray()
    {
        assertEquals("int[][]", DescriptorParser.formatFieldDescriptor("[[I"));
        assertEquals("byte[][][]", DescriptorParser.formatFieldDescriptor("[[[B"));
    }

    @Test
    void testObjectArray()
    {
        assertEquals("String[]", DescriptorParser.formatFieldDescriptor("[Ljava/lang/String;"));
    }

    @Test
    void testMultiDimensionalObjectArray()
    {
        assertEquals("Object[][]", DescriptorParser.formatFieldDescriptor("[[Ljava/lang/Object;"));
    }

    @Test
    void testNullDescriptor()
    {
        assertEquals("?", DescriptorParser.formatFieldDescriptor(null));
    }

    @Test
    void testEmptyDescriptor()
    {
        assertEquals("?", DescriptorParser.formatFieldDescriptor(""));
    }

    @Test
    void testMethodDescriptorNoParams()
    {
        assertEquals("", DescriptorParser.formatMethodParams("()V"));
    }

    @Test
    void testMethodDescriptorSinglePrimitive()
    {
        assertEquals("int", DescriptorParser.formatMethodParams("(I)V"));
    }

    @Test
    void testMethodDescriptorMultiplePrimitives()
    {
        assertEquals("int, long, double", DescriptorParser.formatMethodParams("(IJD)V"));
    }

    @Test
    void testMethodDescriptorWithObject()
    {
        assertEquals("String", DescriptorParser.formatMethodParams("(Ljava/lang/String;)V"));
    }

    @Test
    void testMethodDescriptorMixed()
    {
        assertEquals("int, String, boolean", DescriptorParser.formatMethodParams("(ILjava/lang/String;Z)V"));
    }

    @Test
    void testMethodDescriptorWithArrays()
    {
        assertEquals("String[], int", DescriptorParser.formatMethodParams("([Ljava/lang/String;I)V"));
    }

    @Test
    void testMethodDescriptorComplexArrays()
    {
        assertEquals("int[][], Object[]", DescriptorParser.formatMethodParams("([[I[Ljava/lang/Object;)V"));
    }

    @Test
    void testMainMethodSignature()
    {
        assertEquals("String[]", DescriptorParser.formatMethodParams("([Ljava/lang/String;)V"));
    }

    @Test
    void testExtractSimpleClassName()
    {
        assertEquals("String", DescriptorParser.extractSimpleName("java/lang/String"));
        assertEquals("ArrayList", DescriptorParser.extractSimpleName("java/util/ArrayList"));
        assertEquals("MyClass", DescriptorParser.extractSimpleName("MyClass"));
    }

    @Test
    void testSplitsDescriptors()
    {
        assertEquals(List.of("I", "[Ljava/lang/String;", "[[J", "La/B;"), DescriptorParser.parameterDescriptors("(I[Ljava/lang/String;[[JLa/B;)V"));
        assertEquals("V", DescriptorParser.returnDescriptor("()V"));
        assertEquals("[I", DescriptorParser.returnDescriptor("(I)[I"));
    }

    @Test
    void testUnterminatedClassNameTerminates()
    {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertTrue(DescriptorParser.parseParameterTypes("(Lfoo").isEmpty()));
        assertThrows(IllegalArgumentException.class, () -> DescriptorParser.parameterDescriptors("(Lfoo)V"));
        assertEquals("Lfoo", DescriptorParser.formatFieldDescriptor("Lfoo"));
    }
}
