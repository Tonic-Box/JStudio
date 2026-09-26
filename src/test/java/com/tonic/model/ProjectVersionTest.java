package com.tonic.model;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.tonic.analysis.ClassFactory;
import com.tonic.parser.ClassPool;
import com.tonic.util.AccessBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("bytecode versions never repeat, so the VM never reuses one project's snapshot for another")
class ProjectVersionTest
{

    private static ProjectModel loaded(String className) throws Exception
    {
        ClassPool pool = new ClassPool(true);
        ProjectModel project = new ProjectModel();
        project.setClassPool(pool);
        project.addClass(ClassFactory.createClass(pool, className, new AccessBuilder().setPublic().build()));
        return project;
    }

    @Test
    @DisplayName("two projects built the same way have different versions")
    void separateProjectsDiffer() throws Exception
    {
        assertNotEquals(loaded("a/One").getBytecodeVersion(), loaded("a/One").getBytecodeVersion());
    }

    @Test
    @DisplayName("clearing a project changes its version")
    void clearChangesVersion() throws Exception
    {
        ProjectModel project = loaded("a/One");
        long before = project.getBytecodeVersion();

        project.clear();

        assertNotEquals(before, project.getBytecodeVersion());
    }
}
