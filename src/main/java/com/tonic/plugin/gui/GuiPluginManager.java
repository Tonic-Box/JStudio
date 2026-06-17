package com.tonic.plugin.gui;

import com.tonic.plugin.api.Plugin;
import com.tonic.plugin.api.PluginInfo;
import com.tonic.plugin.api.ui.Registration;
import com.tonic.plugin.api.ui.UiPlugin;
import com.tonic.plugin.context.LiveGuiPluginContext;
import com.tonic.plugin.loader.JarPluginScanner;
import com.tonic.ui.MainFrame;
import com.tonic.util.Settings;

import javax.swing.SwingUtilities;
import java.io.File;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads, activates, and manages GUI plugins from {@code ~/.jstudio/plugins/}. Jars are discovered and their
 * plugin classes instantiated off the EDT (constructors must be trivial); activation (init + start) and every
 * later mutation happen on the EDT. One {@link URLClassLoader} is created per jar (parent-first off the app
 * loader, so plugins see every app class), shared by all plugins in that jar and closed only on reload/shutdown.
 * <p>
 * Lifecycle: <b>disable</b> removes a plugin's contributions (reverse order) then calls {@code dispose()} but
 * keeps its classes loaded; <b>enable</b> re-activates it; <b>reload</b> tears down a jar, closes its loader, and
 * re-scans it from disk (picking up a new build). Failures in one plugin never affect the app or its siblings.
 */
public final class GuiPluginManager {

    private static final GuiPluginManager INSTANCE = new GuiPluginManager();

    private MainFrame frame;
    /** All discovered plugins (EDT-confined). */
    private final List<LoadedPlugin> loaded = new ArrayList<>();

    private GuiPluginManager() {
    }

    public static GuiPluginManager getInstance() {
        return INSTANCE;
    }

    /** The plugins directory, created if absent. */
    public File pluginsDir() {
        File dir = new File(System.getProperty("user.home"), ".jstudio" + File.separator + "plugins");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** A snapshot of the currently tracked plugins, for the manager UI. */
    public List<LoadedPlugin> getPlugins() {
        return new ArrayList<>(loaded);
    }

    /**
     * Called once from the main window. Discovers and instantiates plugins on a background thread, then activates
     * them on the EDT. Never throws into the caller.
     */
    public void bootstrap(MainFrame mainFrame) {
        if (this.frame != null) {
            return;
        }
        this.frame = mainFrame;
        Thread loader = new Thread(() -> {
            List<ScannedJar> scanned = discover();
            SwingUtilities.invokeLater(() -> activateAll(scanned));
        }, "jstudio-plugin-loader");
        loader.setDaemon(true);
        loader.start();
    }

    /** Tears down all plugins and closes all loaders. Called on application exit (EDT). */
    public void shutdown() {
        Set<URLClassLoader> loaders = new HashSet<>();
        for (LoadedPlugin lp : loaded) {
            if (lp.state == LoadedPlugin.State.ENABLED) {
                teardown(lp);
            }
            if (lp.loader != null) {
                loaders.add(lp.loader);
            }
        }
        loaded.clear();
        for (URLClassLoader l : loaders) {
            JarPluginScanner.closeQuietly(l);
        }
    }

    public void enable(LoadedPlugin lp) {
        Set<String> disabled = Settings.getInstance().getDisabledPlugins();
        disabled.remove(lp.info.getId());
        Settings.getInstance().setDisabledPlugins(disabled);
        if (lp.isUi()) {
            activate(lp);
        } else {
            lp.state = LoadedPlugin.State.NOT_UI;
        }
    }

    public void disable(LoadedPlugin lp) {
        if (lp.state == LoadedPlugin.State.ENABLED) {
            teardown(lp);
        }
        lp.state = LoadedPlugin.State.DISABLED;
        Set<String> disabled = Settings.getInstance().getDisabledPlugins();
        disabled.add(lp.info.getId());
        Settings.getInstance().setDisabledPlugins(disabled);
    }

    /** Reloads the jar a plugin came from, re-reading it from disk. */
    public void reload(LoadedPlugin lp) {
        reloadJar(lp.jar);
    }

    /** Tears everything down, closes all loaders, and re-discovers from disk. */
    public void reloadAll() {
        Set<URLClassLoader> loaders = new HashSet<>();
        for (LoadedPlugin lp : loaded) {
            if (lp.state == LoadedPlugin.State.ENABLED) {
                teardown(lp);
            }
            if (lp.loader != null) {
                loaders.add(lp.loader);
            }
        }
        loaded.clear();
        for (URLClassLoader l : loaders) {
            JarPluginScanner.closeQuietly(l);
        }
        activateAll(discover());
    }

    private void reloadJar(File jar) {
        List<LoadedPlugin> fromJar = new ArrayList<>();
        for (LoadedPlugin lp : loaded) {
            if (lp.jar.equals(jar)) {
                fromJar.add(lp);
            }
        }
        URLClassLoader loaderToClose = null;
        for (LoadedPlugin lp : fromJar) {
            if (lp.state == LoadedPlugin.State.ENABLED) {
                teardown(lp);
            }
            if (lp.loader != null) {
                loaderToClose = lp.loader;
            }
        }
        loaded.removeAll(fromJar);
        if (loaderToClose != null) {
            JarPluginScanner.closeQuietly(loaderToClose);
        }
        registerScanned(scanJar(jar));
    }

    private List<ScannedJar> discover() {
        List<ScannedJar> result = new ArrayList<>();
        File[] jars = pluginsDir().listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
        if (jars != null) {
            for (File jar : jars) {
                result.add(scanJar(jar));
            }
        }
        return result;
    }

    private ScannedJar scanJar(File jar) {
        ScannedJar scanned = new ScannedJar(jar);
        try {
            JarPluginScanner.ScanResult result = JarPluginScanner.scan(jar, getClass().getClassLoader());
            scanned.loader = result.loader;
            scanned.plugins = result.plugins;
        } catch (Throwable t) {
            scanned.error = t;
        }
        return scanned;
    }

    private void activateAll(List<ScannedJar> scanned) {
        for (ScannedJar jar : scanned) {
            registerScanned(jar);
        }
    }

    private void registerScanned(ScannedJar scanned) {
        if (scanned.error != null) {
            LoadedPlugin lp = new LoadedPlugin(scanned.jar, jarInfo(scanned.jar), null, null,
                    LoadedPlugin.State.ERROR);
            lp.error = scanned.error;
            loaded.add(lp);
            return;
        }
        if (scanned.plugins == null || scanned.plugins.isEmpty()) {
            // A jar with no @JStudioPlugin (e.g. a stray library): nothing to host, release its loader.
            if (scanned.loader != null) {
                JarPluginScanner.closeQuietly(scanned.loader);
            }
            return;
        }

        Set<String> disabled = Settings.getInstance().getDisabledPlugins();
        for (Plugin plugin : scanned.plugins) {
            try {
                PluginInfo info = plugin.getInfo();
                if (!(plugin instanceof UiPlugin)) {
                    loaded.add(new LoadedPlugin(scanned.jar, info, scanned.loader, plugin,
                            LoadedPlugin.State.NOT_UI));
                    continue;
                }
                LoadedPlugin lp = new LoadedPlugin(scanned.jar, info, scanned.loader, plugin,
                        LoadedPlugin.State.DISABLED);
                loaded.add(lp);
                if (!disabled.contains(info.getId())) {
                    activate(lp);
                }
            } catch (Throwable t) {
                LoadedPlugin lp = new LoadedPlugin(scanned.jar, jarInfo(scanned.jar), scanned.loader, plugin,
                        LoadedPlugin.State.ERROR);
                lp.error = t;
                loaded.add(lp);
            }
        }
    }

    /** Builds init + start for a UI plugin, isolating any failure as an ERROR state. */
    private void activate(LoadedPlugin lp) {
        lp.contributions.clear();
        LiveGuiPluginContext context = new LiveGuiPluginContext(lp.info.getName());
        context.setExportDir(new File(new File(pluginsDir(), lp.info.getId()), "export"));
        JStudioHostImpl host = new JStudioHostImpl(frame, lp.info, context, lp.contributions);
        lp.host = host;
        try {
            lp.plugin.init(context);
            ((UiPlugin) lp.plugin).start(host);
            lp.state = LoadedPlugin.State.ENABLED;
            lp.error = null;
        } catch (Throwable t) {
            lp.state = LoadedPlugin.State.ERROR;
            lp.error = t;
            teardown(lp);
        }
    }

    /** Removes contributions (reverse order) then disposes the plugin. Every step is isolated. */
    private void teardown(LoadedPlugin lp) {
        List<Registration> regs = lp.contributions;
        for (int i = regs.size() - 1; i >= 0; i--) {
            try {
                regs.get(i).remove();
            } catch (Throwable ignored) {
            }
        }
        regs.clear();
        if (lp.plugin != null) {
            try {
                lp.plugin.dispose();
            } catch (Throwable ignored) {
            }
        }
    }

    private static PluginInfo jarInfo(File jar) {
        return PluginInfo.builder().name(jar.getName()).version("").build();
    }

    /** A scanned jar: its loader + instantiated plugins, or the failure that stopped it. */
    private static final class ScannedJar {
        final File jar;
        URLClassLoader loader;
        List<Plugin> plugins;
        Throwable error;

        ScannedJar(File jar) {
            this.jar = jar;
        }
    }
}
