package com.tonic.ui.editor.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Covers the pure regex declaration parsing extracted from the source view's navigation. */
class SourceDeclarationParserTest {

    @Test
    void classDeclaration() {
        assertEquals("Foo", SourceDeclarationParser.extractClassDeclaration("public class Foo {"));
        assertEquals("Bar", SourceDeclarationParser.extractClassDeclaration("interface Bar {"));
        assertEquals("E", SourceDeclarationParser.extractClassDeclaration("public enum E {"));
        assertNull(SourceDeclarationParser.extractClassDeclaration("int x = 5;"));
    }

    @Test
    void methodDeclaration() {
        assertEquals("foo", SourceDeclarationParser.extractMethodDeclaration("public void foo(int x) {"));
        assertEquals("compute", SourceDeclarationParser.extractMethodDeclaration("private String compute() {"));
        assertNull(SourceDeclarationParser.extractMethodDeclaration("if (x) {"));
        assertNull(SourceDeclarationParser.extractMethodDeclaration("int y = bar();"));
        assertNull(SourceDeclarationParser.extractMethodDeclaration("private int count;"));
    }

    @Test
    void fieldDeclaration() {
        assertEquals("count", SourceDeclarationParser.extractFieldDeclaration("private int count;"));
        assertEquals("name", SourceDeclarationParser.extractFieldDeclaration("public String name = \"x\";"));
        assertNull(SourceDeclarationParser.extractFieldDeclaration("void foo() {"));
    }

    @Test
    void methodParams() {
        assertEquals("int a, String b", SourceDeclarationParser.extractMethodParams("foo(int a, String b)"));
        assertEquals("", SourceDeclarationParser.extractMethodParams("bar()"));
    }

    @Test
    void identifierAtOffset() {
        assertEquals("hello", SourceDeclarationParser.extractIdentifierAt("hello world", 2));
        assertEquals("a.b.c", SourceDeclarationParser.extractIdentifierAt("a.b.c = 1", 2));
        assertNull(SourceDeclarationParser.extractIdentifierAt("text", -1));
        assertNull(SourceDeclarationParser.extractIdentifierAt("  ", 0));
    }
}
