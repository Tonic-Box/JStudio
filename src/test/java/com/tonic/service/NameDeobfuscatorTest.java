package com.tonic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tonic.builder.ClassBuilder;
import com.tonic.model.ProjectModel;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.parser.MethodEntry;
import com.tonic.parser.constpool.Item;
import com.tonic.parser.constpool.MethodRefItem;
import com.tonic.type.AccessFlags;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("name deobfuscation keeps overrides consistent and leaves library contracts alone")
class NameDeobfuscatorTest implements AccessFlags
{

    private ProjectModel project;

    @BeforeEach
    void setUp()
    {
        ClassPool pool = new ClassPool(true);
        project = new ProjectModel();
        project.setClassPool(pool);

        project.addClass(ClassBuilder.create("a/Base").access(ACC_PUBLIC).interfaces("java/lang/Runnable").addMethod(ACC_PUBLIC, "foo", "()I").code().iconst(1).ireturn().end().end().addMethod(ACC_PUBLIC, "run", "()V").code().vreturn().end().end().build());
        project.addClass(ClassBuilder.create("a/Sub").access(ACC_PUBLIC).superClass("a/Base").addMethod(ACC_PUBLIC, "foo", "()I").code().iconst(2).ireturn().end().end().build());
        project.addClass(ClassBuilder.create("a/User").access(ACC_PUBLIC).addMethod(ACC_PUBLIC | ACC_STATIC, "call", "(La/Sub;)I").code().aload(0).invokevirtual("a/Base", "foo", "()I").ireturn().end().end().build());
    }

    private static String nameOf(ClassFile cf, String descriptor, List<String> exclude)
    {
        for (MethodEntry method : cf.getMethods())
        {
            if (method.getDesc().equals(descriptor) && !exclude.contains(method.getName()))
            {
                return method.getName();
            }
        }
        return null;
    }

    private static List<String> calledNames(ClassFile cf)
    {
        List<String> names = new ArrayList<>();
        for (Item<?> item : cf.getConstPool().getItems())
        {
            if (item instanceof MethodRefItem)
            {
                names.add(((MethodRefItem) item).getName());
            }
        }
        return names;
    }

    @Test
    @DisplayName("an override pair gets one name and call sites follow it; Runnable.run is kept")
    void overridesShareOneName()
    {
        new NameDeobfuscator(project, false, true, false, true, line ->
        {
        }).apply();

        ClassPool pool = project.getClassPool();
        List<String> constructors = List.of("<init>", "<clinit>");
        String baseFoo = nameOf(pool.get("a/Base"), "()I", constructors);
        String subFoo = nameOf(pool.get("a/Sub"), "()I", constructors);

        assertNotEquals("foo", baseFoo);
        assertEquals(baseFoo, subFoo);
        assertEquals("run", nameOf(pool.get("a/Base"), "()V", constructors));
        assertTrue(calledNames(pool.get("a/User")).contains(baseFoo));
        assertFalse(calledNames(pool.get("a/User")).contains("foo"));
    }
}
