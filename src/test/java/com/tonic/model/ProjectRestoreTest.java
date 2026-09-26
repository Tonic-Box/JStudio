package com.tonic.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tonic.analysis.ClassFactory;
import com.tonic.parser.ClassFile;
import com.tonic.parser.ClassPool;
import com.tonic.util.AccessBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("replacing a project's classes from history is all or nothing")
class ProjectRestoreTest
{

    private static ProjectModel projectWith(String... names) throws Exception
    {
        ClassPool pool = new ClassPool(true);
        ProjectModel project = new ProjectModel();
        project.setClassPool(pool);
        for (String name : names)
        {
            project.addClass(ClassFactory.createClass(pool, name, new AccessBuilder().setPublic().build()));
        }
        return project;
    }

    private static byte[] bytesOf(String name) throws Exception
    {
        ClassFile cf = ClassFactory.createClass(new ClassPool(true), name, new AccessBuilder().setPublic().build());
        return cf.write();
    }

    @Test
    @DisplayName("a class with no bytes leaves every existing class in place")
    void missingBytesChangeNothing() throws Exception
    {
        ProjectModel project = projectWith("a/One", "a/Two");
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put("a/One", bytesOf("a/One"));
        classes.put("a/Two", null);

        assertThrows(IllegalStateException.class, () -> project.replaceUserClasses(classes, Map.of()));

        assertEquals(2, project.getUserClasses().size());
        assertNotNull(project.getClass("a/Two"));
    }

    @Test
    @DisplayName("bytes that do not parse leave every existing class in place")
    void unparsableBytesChangeNothing() throws Exception
    {
        ProjectModel project = projectWith("a/One", "a/Two");
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put("a/One", bytesOf("a/One"));
        classes.put("a/Two", new byte[]{1, 2, 3});

        assertThrows(IllegalStateException.class, () -> project.replaceUserClasses(classes, Map.of()));

        assertEquals(2, project.getUserClasses().size());
    }

    @Test
    @DisplayName("a complete snapshot replaces the classes")
    void completeSnapshotApplies() throws Exception
    {
        ProjectModel project = projectWith("a/One", "a/Two");

        project.replaceUserClasses(Map.of("a/Three", bytesOf("a/Three")), Map.of());

        assertEquals(1, project.getUserClasses().size());
        assertNotNull(project.getClass("a/Three"));
    }
}
