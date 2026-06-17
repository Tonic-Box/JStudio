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

/**
 * Loads {@code @JStudioPlugin}-annotated {@link Plugin}s out of a jar. Shared by the CLI {@code PluginLoader}
 * (which takes the first) and the GUI plugin runtime (which takes all and activates the UI ones). Each scan
 * creates one {@link URLClassLoader} (parent-first delegation off the supplied parent) that the caller owns and
 * must {@link #closeQuietly(URLClassLoader) close} once the plugins are disposed.
 */
public final class JarPluginScanner {

    private JarPluginScanner() {
    }

    /** The class loader for a scanned jar plus every plugin instantiated from it. */
    public static final class ScanResult {
        public final URLClassLoader loader;
        public final List<Plugin> plugins;

        ScanResult(URLClassLoader loader, List<Plugin> plugins) {
            this.loader = loader;
            this.plugins = plugins;
        }
    }

    /**
     * Loads every annotated plugin in {@code jarFile}. On success the returned loader stays open (the plugins load
     * classes lazily through it) and ownership passes to the caller; on any failure the loader is closed before the
     * exception propagates.
     */
    public static ScanResult scan(File jarFile, ClassLoader parent) {
        URLClassLoader loader;
        try {
            loader = new URLClassLoader(new URL[]{jarFile.toURI().toURL()}, parent);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to load JAR plugin: " + e.getMessage(), e);
        }

        List<Plugin> plugins = new ArrayList<>();
        boolean ok = false;
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName()
                            .replace('/', '.')
                            .replace(".class", "");
                    try {
                        Class<?> clazz = loader.loadClass(className);
                        if (Plugin.class.isAssignableFrom(clazz)
                                && clazz.isAnnotationPresent(JStudioPlugin.class)) {
                            plugins.add(instantiate(clazz));
                        }
                    } catch (ClassNotFoundException | NoClassDefFoundError e) {
                        // Skip classes that can't be resolved (optional deps, etc.).
                    }
                }
            }
            ok = true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load JAR plugin: " + e.getMessage(), e);
        } finally {
            if (!ok) {
                closeQuietly(loader);
            }
        }
        return new ScanResult(loader, plugins);
    }

    /** Instantiates a plugin via its no-arg constructor (made accessible). */
    public static Plugin instantiate(Class<?> clazz) {
        try {
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (Plugin) constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate plugin: " + e.getMessage(), e);
        }
    }

    public static void closeQuietly(URLClassLoader loader) {
        try {
            loader.close();
        } catch (IOException ignored) {
        }
    }
}
