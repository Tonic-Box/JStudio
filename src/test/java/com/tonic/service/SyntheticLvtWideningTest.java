package com.tonic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tonic.analysis.ClassFactory;
import com.tonic.parser.ClassPool;
import com.tonic.parser.ConstPool;
import com.tonic.parser.attribute.table.LocalVariableTableEntry;
import com.tonic.util.AccessBuilder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("widening a local variable table never renames one variable after another")
class SyntheticLvtWideningTest
{

    private ConstPool cp;
    private int intType;

    @BeforeEach
    void setUp() throws Exception
    {
        cp = ClassFactory.createClass(new ClassPool(true), "a/Loops", new AccessBuilder().setPublic().build()).getConstPool();
        intType = cp.utf8Index("I");
    }

    @Test
    @DisplayName("two loop counters sharing a slot keep their own names and scopes")
    void differentNamesStaySeparate()
    {
        List<LocalVariableTableEntry> entries = List.of(new LocalVariableTableEntry(cp, 2, 10, cp.utf8Index("i"), intType, 1), new LocalVariableTableEntry(cp, 14, 10, cp.utf8Index("j"), intType, 1));

        List<LocalVariableTableEntry> widened = SyntheticLvtInjector.widen(entries, cp, 30);

        assertEquals(2, widened.size());
        assertEquals(2, widened.get(0).getStartPc());
        assertEquals(14, widened.get(1).getStartPc());
    }

    @Test
    @DisplayName("one variable split across scopes becomes a single method-wide entry")
    void sameVariableWidens()
    {
        int name = cp.utf8Index("count");
        List<LocalVariableTableEntry> entries = List.of(new LocalVariableTableEntry(cp, 2, 5, name, intType, 1), new LocalVariableTableEntry(cp, 9, 5, name, intType, 1));

        List<LocalVariableTableEntry> widened = SyntheticLvtInjector.widen(entries, cp, 30);

        assertEquals(1, widened.size());
        assertEquals(0, widened.get(0).getStartPc());
        assertEquals(30, widened.get(0).getLengthPc());
    }
}
