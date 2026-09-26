package com.tonic.ui.live;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.builder.ClassBuilder;
import com.tonic.parser.ClassFile;
import com.tonic.type.AccessFlags;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("live patching refuses edits that add or remove methods instead of reporting success")
class LivePatchMemberTest implements AccessFlags
{

    private static ClassFile classWithFoo(int returned)
    {
        return ClassBuilder.create("a/Target").access(ACC_PUBLIC)
                .addMethod(ACC_PUBLIC, "foo", "()I").code().iconst(returned).ireturn().end().end()
                .build();
    }

    @Test
    @DisplayName("a method added in the source is reported as a member change")
    void addedMethodIsDetected()
    {
        String baseline = "package a;\npublic class Target {\n    public int foo() { return 1; }\n}\n";
        String edited = "package a;\npublic class Target {\n    public int foo() { return helper(); }\n    public int helper() { return 2; }\n}\n";

        assertEquals(Set.of("helper()I"), MethodBodyDiff.addedOrRemovedMethods(baseline, edited, null, "a/Target"));
    }

    @Test
    @DisplayName("a body-only edit has no member changes")
    void bodyEditHasNoMemberChanges()
    {
        String baseline = "package a;\npublic class Target {\n    public int foo() { return 1; }\n}\n";
        String edited = "package a;\npublic class Target {\n    public int foo() { return 2; }\n}\n";

        assertTrue(MethodBodyDiff.addedOrRemovedMethods(baseline, edited, null, "a/Target").isEmpty());
    }

    @Test
    @DisplayName("grafting a key the running class lacks fails and names it")
    void missingKeyFails() throws Exception
    {
        byte[] running = classWithFoo(1).write();

        IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> LivePatch.graftOnto(running, classWithFoo(2), Set.of("foo()I", "helper()I")));

        assertTrue(thrown.getMessage().contains("helper()I"));
    }

    @Test
    @DisplayName("grafting existing methods succeeds")
    void existingKeyGrafts() throws Exception
    {
        byte[] running = classWithFoo(1).write();

        assertTrue(LivePatch.graftOnto(running, classWithFoo(2), Set.of("foo()I")).length > 0);
    }
}
