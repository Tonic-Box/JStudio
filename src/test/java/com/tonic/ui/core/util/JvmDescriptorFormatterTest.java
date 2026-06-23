package com.tonic.ui.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Covers the shared JVM-descriptor formatting extracted from the navigator/debugger panels. */
class JvmDescriptorFormatterTest {

    @Test
    void simpleClassName() {
        assertEquals("String", JvmDescriptorFormatter.getSimpleClassName("java/lang/String"));
        assertEquals("String", JvmDescriptorFormatter.getSimpleClassName("String"));
        assertEquals("C$D", JvmDescriptorFormatter.getSimpleClassName("a/b/C$D"));
    }

    @Test
    void primitivesAndObjects() {
        assertEquals("()", JvmDescriptorFormatter.formatDescriptorParams("()V"));
        assertEquals("(int)", JvmDescriptorFormatter.formatDescriptorParams("(I)V"));
        assertEquals("(int, String)", JvmDescriptorFormatter.formatDescriptorParams("(ILjava/lang/String;)V"));
        assertEquals("(long, boolean, double)",
                JvmDescriptorFormatter.formatDescriptorParams("(JZD)V"));
    }

    @Test
    void arrays() {
        assertEquals("(int[])", JvmDescriptorFormatter.formatDescriptorParams("([I)V"));
        assertEquals("(Object[][])", JvmDescriptorFormatter.formatDescriptorParams("([[Ljava/lang/Object;)V"));
        assertEquals("(String, byte[])",
                JvmDescriptorFormatter.formatDescriptorParams("(Ljava/lang/String;[B)V"));
    }

    @Test
    void malformedOrEmptyFallsBackToParens() {
        assertEquals("()", JvmDescriptorFormatter.formatDescriptorParams(null));
        assertEquals("()", JvmDescriptorFormatter.formatDescriptorParams(""));
        assertEquals("()", JvmDescriptorFormatter.formatDescriptorParams("garbage"));
    }
}
