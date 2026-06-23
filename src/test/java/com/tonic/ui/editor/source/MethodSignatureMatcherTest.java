package com.tonic.ui.editor.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the pure descriptor/source signature matching extracted from the source view's navigation. */
class MethodSignatureMatcherTest {

    @Test
    void countsSourceParams() {
        assertEquals(0, MethodSignatureMatcher.countParams(""));
        assertEquals(1, MethodSignatureMatcher.countParams("int a"));
        assertEquals(2, MethodSignatureMatcher.countParams("int a, String b"));
        assertEquals(2, MethodSignatureMatcher.countParams("Map<K, V> m, int x"));
    }

    @Test
    void countsDescriptorParams() {
        assertEquals(0, MethodSignatureMatcher.countDescriptorParams("()V"));
        assertEquals(1, MethodSignatureMatcher.countDescriptorParams("(I)V"));
        assertEquals(2, MethodSignatureMatcher.countDescriptorParams("(ILjava/lang/String;)V"));
        assertEquals(1, MethodSignatureMatcher.countDescriptorParams("([I)V"));
    }

    @Test
    void paramsMatch() {
        assertTrue(MethodSignatureMatcher.paramsMatch("int a", "(I)V"));
        assertTrue(MethodSignatureMatcher.paramsMatch("String s", "(Ljava/lang/String;)V"));
        assertFalse(MethodSignatureMatcher.paramsMatch("int a", "(J)V"));
    }

    @Test
    void returnTypes() {
        assertEquals("void", MethodSignatureMatcher.extractReturnTypeFromDesc("(I)V"));
        assertEquals("String", MethodSignatureMatcher.extractReturnTypeFromDesc("(I)Ljava/lang/String;"));
        assertEquals("int", MethodSignatureMatcher.extractReturnTypeFromDesc("()I"));
        assertEquals("void", MethodSignatureMatcher.extractReturnTypeFromSource("public void foo(int x) {"));
        assertEquals("String", MethodSignatureMatcher.extractReturnTypeFromSource("private String compute() {"));
        assertTrue(MethodSignatureMatcher.returnTypeMatches("void", "void"));
        assertTrue(MethodSignatureMatcher.returnTypeMatches(null, "int"));
    }
}
