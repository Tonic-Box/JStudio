package com.tonic.service.run;

import com.tonic.model.ClassEntryModel;
import com.tonic.model.ProjectModel;
import com.tonic.model.ResourceEntryModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Writes a project's current in-memory classes and resources to a jar; shared by Export as JAR and Run so both reflect edits. */
public final class ProjectJarExporter
{

    private static final String MANIFEST_PATH = "META-INF/MANIFEST.MF";

    private ProjectJarExporter()
    {
    }

    /**
     * Writes the project's user classes and resources to a jar, keeping its manifest or writing a minimal one.
     *
     * @param project the project to export
     * @param output the jar file to write
     * @throws IOException if the jar cannot be written
     */
    public static void export(ProjectModel project, File output) throws IOException
    {
        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(output)))
        {
            writeManifest(jar, project);
            for (ClassEntryModel entry : project.getUserClasses())
            {
                jar.putNextEntry(new JarEntry(entry.getClassName() + ".class"));
                jar.write(entry.getClassFile().write());
                jar.closeEntry();
            }
            for (ResourceEntryModel resource : project.getAllResources())
            {
                if (MANIFEST_PATH.equals(resource.getPath()))
                {
                    continue;
                }
                jar.putNextEntry(new JarEntry(resource.getPath()));
                jar.write(resource.getData());
                jar.closeEntry();
            }
        }
    }

    private static void writeManifest(JarOutputStream jar, ProjectModel project) throws IOException
    {
        jar.putNextEntry(new JarEntry(MANIFEST_PATH));
        ResourceEntryModel existing = project.getResource(MANIFEST_PATH);
        jar.write(existing != null ? existing.getData() : "Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        jar.closeEntry();
    }
}
