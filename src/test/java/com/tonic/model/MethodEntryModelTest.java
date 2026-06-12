package com.tonic.model;

import com.tonic.parser.MethodEntry;
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
class MethodEntryModelTest {

    @Mock
    private MethodEntry methodEntry;

    @Mock
    private ClassEntryModel owner;

    @Test
    void testGetName() {
        when(methodEntry.getName()).thenReturn("testMethod");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertEquals("testMethod", model.getName());
    }

    @Test
    void testGetDescriptor() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("(ILjava/lang/String;)V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertEquals("(ILjava/lang/String;)V", model.getDescriptor());
    }

    @Test
    void testDisplaySignatureNoParams() {
        when(methodEntry.getName()).thenReturn("run");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertEquals("run()", model.getDisplaySignature());
    }

    @Test
    void testDisplaySignatureWithParams() {
        when(methodEntry.getName()).thenReturn("process");
        when(methodEntry.getDesc()).thenReturn("(ILjava/lang/String;Z)V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertEquals("process(int, String, boolean)", model.getDisplaySignature());
    }

    @Test
    void testDisplaySignatureMainMethod() {
        when(methodEntry.getName()).thenReturn("main");
        when(methodEntry.getDesc()).thenReturn("([Ljava/lang/String;)V");
        when(methodEntry.getAccess()).thenReturn(0x0009);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertEquals("main(String[])", model.getDisplaySignature());
    }

    @Test
    void testIsPublic() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertTrue(model.isPublic());
        assertFalse(model.isPrivate());
        assertFalse(model.isProtected());
    }

    @Test
    void testIsPrivate() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0002);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertTrue(model.isPrivate());
        assertFalse(model.isPublic());
        assertFalse(model.isProtected());
    }

    @Test
    void testIsProtected() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0004);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertTrue(model.isProtected());
        assertFalse(model.isPublic());
        assertFalse(model.isPrivate());
    }

    @Test
    void testIsStatic() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0008);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertTrue(model.isStatic());
    }

    @Test
    void testIsFinal() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0010);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertTrue(model.isFinal());
    }

    @Test
    void testIsSynchronized() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0020);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertTrue(model.isSynchronized());
    }

    @Test
    void testIsNative() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0100);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertTrue(model.isNative());
    }

    @Test
    void testIsAbstract() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0400);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertTrue(model.isAbstract());
    }

    @Test
    void testIsConstructor() {
        when(methodEntry.getName()).thenReturn("<init>");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertTrue(model.isConstructor());
    }

    @Test
    void testIsStaticInitializer() {
        when(methodEntry.getName()).thenReturn("<clinit>");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0008);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertTrue(model.isStaticInitializer());
    }

    @Test
    void testHasCodeWithCode() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001);
        when(methodEntry.getCodeAttribute()).thenReturn(mock(com.tonic.parser.attribute.CodeAttribute.class));

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertTrue(model.hasCode());
    }

    @Test
    void testHasCodeWithoutCode() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0400);
        when(methodEntry.getCodeAttribute()).thenReturn(null);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertFalse(model.hasCode());
    }

    @Test
    void testDefaultAnalysisState() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertEquals(MethodEntryModel.AnalysisState.NOT_ANALYZED, model.getAnalysisState());
    }

    @Test
    void testSetAnalysisState() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        model.setAnalysisState(MethodEntryModel.AnalysisState.IR_LIFTED);
        assertEquals(MethodEntryModel.AnalysisState.IR_LIFTED, model.getAnalysisState());
    }

    @Test
    void testInvalidateIRCache() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        model.setAnalysisState(MethodEntryModel.AnalysisState.IR_LIFTED);
        model.setIrCache("cached IR string");

        model.invalidateIRCache();

        assertEquals(MethodEntryModel.AnalysisState.NOT_ANALYZED, model.getAnalysisState());
        assertNull(model.getCachedIR());
        assertNull(model.getIrCache());
        assertEquals(0, model.getIrCacheTimestamp());
    }

    @Test
    void testSelectedState() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertFalse(model.isSelected());

        model.setSelected(true);
        assertTrue(model.isSelected());

        model.setSelected(false);
        assertFalse(model.isSelected());
    }

    @Test
    void testBookmarkedState() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertFalse(model.isBookmarked());

        model.setBookmarked(true);
        assertTrue(model.isBookmarked());
    }

    @Test
    void testUserNotes() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertNull(model.getUserNotes());

        model.setUserNotes("This method needs refactoring");
        assertEquals("This method needs refactoring", model.getUserNotes());
    }

    @Test
    void testToString() {
        when(methodEntry.getName()).thenReturn("calculate");
        when(methodEntry.getDesc()).thenReturn("(DD)D");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertEquals("calculate(double, double)", model.toString());
    }

    @Test
    void testGetOwner() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertSame(owner, model.getOwner());
    }

    @Test
    void testGetMethodEntry() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertSame(methodEntry, model.getMethodEntry());
    }

    @Test
    void testIconNotNull() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertNotNull(model.getIconKey());
    }

    @Test
    void testCombinedAccessFlags() {
        when(methodEntry.getName()).thenReturn("test");
        when(methodEntry.getDesc()).thenReturn("()V");
        when(methodEntry.getAccess()).thenReturn(0x0001 | 0x0008 | 0x0010);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertTrue(model.isPublic());
        assertTrue(model.isStatic());
        assertTrue(model.isFinal());
        assertFalse(model.isPrivate());
        assertFalse(model.isAbstract());
    }

    @Test
    void testComplexDescriptor() {
        when(methodEntry.getName()).thenReturn("complex");
        when(methodEntry.getDesc()).thenReturn("([[Ljava/lang/String;[I)Ljava/util/List;");
        when(methodEntry.getAccess()).thenReturn(0x0001);

        MethodEntryModel model = new MethodEntryModel(methodEntry, owner);
        assertEquals("complex(String[][], int[])", model.getDisplaySignature());
    }
}
