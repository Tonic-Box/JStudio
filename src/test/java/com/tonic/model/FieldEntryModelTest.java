package com.tonic.model;

import com.tonic.parser.FieldEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FieldEntryModelTest {

    @Mock
    private FieldEntry fieldEntry;

    @Mock
    private ClassEntryModel owner;

    @Test
    void testGetName() {
        when(fieldEntry.getName()).thenReturn("myField");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("myField", model.getName());
    }

    @Test
    void testGetDescriptor() {
        when(fieldEntry.getName()).thenReturn("test");
        when(fieldEntry.getDesc()).thenReturn("Ljava/lang/String;");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("Ljava/lang/String;", model.getDescriptor());
    }

    @Test
    void testDisplayTypePrimitiveByte() {
        when(fieldEntry.getName()).thenReturn("b");
        when(fieldEntry.getDesc()).thenReturn("B");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("byte", model.getDisplayType());
    }

    @Test
    void testDisplayTypePrimitiveChar() {
        when(fieldEntry.getName()).thenReturn("c");
        when(fieldEntry.getDesc()).thenReturn("C");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("char", model.getDisplayType());
    }

    @Test
    void testDisplayTypePrimitiveInt() {
        when(fieldEntry.getName()).thenReturn("i");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("int", model.getDisplayType());
    }

    @Test
    void testDisplayTypePrimitiveLong() {
        when(fieldEntry.getName()).thenReturn("l");
        when(fieldEntry.getDesc()).thenReturn("J");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("long", model.getDisplayType());
    }

    @Test
    void testDisplayTypePrimitiveDouble() {
        when(fieldEntry.getName()).thenReturn("d");
        when(fieldEntry.getDesc()).thenReturn("D");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("double", model.getDisplayType());
    }

    @Test
    void testDisplayTypePrimitiveFloat() {
        when(fieldEntry.getName()).thenReturn("f");
        when(fieldEntry.getDesc()).thenReturn("F");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("float", model.getDisplayType());
    }

    @Test
    void testDisplayTypePrimitiveShort() {
        when(fieldEntry.getName()).thenReturn("s");
        when(fieldEntry.getDesc()).thenReturn("S");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("short", model.getDisplayType());
    }

    @Test
    void testDisplayTypePrimitiveBoolean() {
        when(fieldEntry.getName()).thenReturn("flag");
        when(fieldEntry.getDesc()).thenReturn("Z");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("boolean", model.getDisplayType());
    }

    @Test
    void testDisplayTypeSimpleClass() {
        when(fieldEntry.getName()).thenReturn("str");
        when(fieldEntry.getDesc()).thenReturn("Ljava/lang/String;");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("String", model.getDisplayType());
    }

    @Test
    void testDisplayTypeNestedClass() {
        when(fieldEntry.getName()).thenReturn("list");
        when(fieldEntry.getDesc()).thenReturn("Ljava/util/ArrayList;");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("ArrayList", model.getDisplayType());
    }

    @Test
    void testDisplayTypePrimitiveArray() {
        when(fieldEntry.getName()).thenReturn("arr");
        when(fieldEntry.getDesc()).thenReturn("[I");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("int[]", model.getDisplayType());
    }

    @Test
    void testDisplayTypeMultiDimensionalArray() {
        when(fieldEntry.getName()).thenReturn("matrix");
        when(fieldEntry.getDesc()).thenReturn("[[D");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("double[][]", model.getDisplayType());
    }

    @Test
    void testDisplayTypeObjectArray() {
        when(fieldEntry.getName()).thenReturn("strings");
        when(fieldEntry.getDesc()).thenReturn("[Ljava/lang/String;");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("String[]", model.getDisplayType());
    }

    @Test
    void testDisplayTypeMultiDimensionalObjectArray() {
        when(fieldEntry.getName()).thenReturn("objects");
        when(fieldEntry.getDesc()).thenReturn("[[Ljava/lang/Object;");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("Object[][]", model.getDisplayType());
    }

    @Test
    void testDisplayTypeNullDescriptor() {
        when(fieldEntry.getName()).thenReturn("unknown");
        when(fieldEntry.getDesc()).thenReturn(null);
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("?", model.getDisplayType());
    }

    @Test
    void testDisplayTypeEmptyDescriptor() {
        when(fieldEntry.getName()).thenReturn("unknown");
        when(fieldEntry.getDesc()).thenReturn("");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("?", model.getDisplayType());
    }

    @Test
    void testIsPublic() {
        when(fieldEntry.getName()).thenReturn("field");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertTrue(model.isPublic());
        assertFalse(model.isPrivate());
        assertFalse(model.isProtected());
    }

    @Test
    void testIsPrivate() {
        when(fieldEntry.getName()).thenReturn("field");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0002);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertTrue(model.isPrivate());
        assertFalse(model.isPublic());
    }

    @Test
    void testIsProtected() {
        when(fieldEntry.getName()).thenReturn("field");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0004);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertTrue(model.isProtected());
        assertFalse(model.isPublic());
    }

    @Test
    void testIsStatic() {
        when(fieldEntry.getName()).thenReturn("field");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0008);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertTrue(model.isStatic());
    }

    @Test
    void testIsFinal() {
        when(fieldEntry.getName()).thenReturn("field");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0010);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertTrue(model.isFinal());
    }

    @Test
    void testIsVolatile() {
        when(fieldEntry.getName()).thenReturn("field");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0040);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertTrue(model.isVolatile());
    }

    @Test
    void testIsTransient() {
        when(fieldEntry.getName()).thenReturn("field");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0080);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertTrue(model.isTransient());
    }

    @Test
    void testCombinedAccessFlags() {
        when(fieldEntry.getName()).thenReturn("CONSTANT");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0001 | 0x0008 | 0x0010);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertTrue(model.isPublic());
        assertTrue(model.isStatic());
        assertTrue(model.isFinal());
    }

    @Test
    void testSelectedState() {
        when(fieldEntry.getName()).thenReturn("field");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertFalse(model.isSelected());

        model.setSelected(true);
        assertTrue(model.isSelected());

        model.setSelected(false);
        assertFalse(model.isSelected());
    }

    @Test
    void testUserNotes() {
        when(fieldEntry.getName()).thenReturn("field");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertNull(model.getUserNotes());

        model.setUserNotes("This field stores the count");
        assertEquals("This field stores the count", model.getUserNotes());
    }

    @Test
    void testToString() {
        when(fieldEntry.getName()).thenReturn("counter");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("int counter", model.toString());
    }

    @Test
    void testToStringWithObject() {
        when(fieldEntry.getName()).thenReturn("name");
        when(fieldEntry.getDesc()).thenReturn("Ljava/lang/String;");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("String name", model.toString());
    }

    @Test
    void testGetOwner() {
        when(fieldEntry.getName()).thenReturn("field");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertSame(owner, model.getOwner());
    }

    @Test
    void testGetFieldEntry() {
        when(fieldEntry.getName()).thenReturn("field");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertSame(fieldEntry, model.getFieldEntry());
    }

    @Test
    void testIconNotNull() {
        when(fieldEntry.getName()).thenReturn("field");
        when(fieldEntry.getDesc()).thenReturn("I");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertNotNull(model.getIconKey());
    }

    @Test
    void testDefaultPackageClass() {
        when(fieldEntry.getName()).thenReturn("obj");
        when(fieldEntry.getDesc()).thenReturn("LMyClass;");
        when(fieldEntry.getAccess()).thenReturn(0x0001);

        FieldEntryModel model = new FieldEntryModel(fieldEntry, owner);
        assertEquals("MyClass", model.getDisplayType());
    }
}
