package com.tonic.plugin.loader;

import com.tonic.plugin.annotations.JStudioPlugin;
import com.tonic.plugin.api.Plugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Loads the JStudioPlugin-annotated Plugin classes out of a jar through a new class loader that the caller owns and must close once the plugins are disposed. */
public final class JarPluginScanner
{

    private JarPluginScanner()
    {
    }

    /** The class loader for a scanned jar plus every plugin instantiated from it. */
    public static final class ScanResult
    {
        public final URLClassLoader loader;
        public final List<Plugin> plugins;

        ScanResult(URLClassLoader loader, List<Plugin> plugins)
        {
            this.loader = loader;
            this.plugins = plugins;
        }
    }

    /**
     * Loads and instantiates every annotated plugin in a jar, skipping classes that fail to load; on success the loader stays open and passes to the caller, on failure it is closed first.
     *
     * @param jarFile the jar to scan
     * @param parent the parent of the new class loader, which delegates to it first
     * @return the loader and the plugins, which may be empty
     * @throws RuntimeException if the jar cannot be opened or read, or a plugin cannot be instantiated
     */
    public static ScanResult scan(File jarFile, ClassLoader parent)
    {
        URLClassLoader loader;
        try
        {
            loader = new URLClassLoader(new URL[]{jarFile.toURI().toURL()}, parent);
        }
        catch (MalformedURLException e)
        {
            throw new RuntimeException("Failed to load JAR plugin: " + e.getMessage(), e);
        }

        List<Plugin> plugins = new ArrayList<>();
        boolean ok = false;
        try (JarFile jar = new JarFile(jarFile))
        {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements())
            {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class"))
                {
                    String className = entry.getName()
                            .replace('/', '.')
                            .replace(".class", "");
                    try
                    {
                        Class<?> clazz = loader.loadClass(className);
                        if (Plugin.class.isAssignableFrom(clazz)
                                && clazz.isAnnotationPresent(JStudioPlugin.class))
                        {
                            plugins.add(instantiate(clazz));
                        }
                    }
                    catch (ClassNotFoundException | NoClassDefFoundError e)
                    {
                    }
                }
            }
            ok = true;
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to load JAR plugin: " + e.getMessage(), e);
        }
        finally
        {
            if (!ok)
            {
                closeQuietly(loader);
            }
        }
        return new ScanResult(loader, plugins);
    }

    /**
     * Instantiates a plugin through its no-argument constructor, even a non-public one.
     *
     * @param clazz the plugin class
     * @return the new plugin
     * @throws RuntimeException if the class has no no-argument constructor or the constructor throws
     */
    public static Plugin instantiate(Class<?> clazz)
    {
        try
        {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (Plugin) constructor.newInstance();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to instantiate plugin: " + e.getMessage(), e);
        }
    }

    /**
     * Closes a class loader, ignoring I/O errors.
     *
     * @param loader the loader to close
     */
    public static void closeQuietly(URLClassLoader loader)
    {
        try
        {
            loader.close();
        }
        catch (IOException ignored)
        {
        }
    }
}
