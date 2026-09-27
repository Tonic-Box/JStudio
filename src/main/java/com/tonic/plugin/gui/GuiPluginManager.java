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

/** The singleton that loads, activates and manages GUI plugins from ~/.jstudio/plugins, with one class loader per jar; discovery runs off the EDT and everything else on it. */
public final class GuiPluginManager
{

    private static final GuiPluginManager INSTANCE = new GuiPluginManager();

    private MainFrame frame;
    private final List<LoadedPlugin> loaded = new ArrayList<>();

    private GuiPluginManager()
    {
    }

    /** @return the shared manager */
    public static GuiPluginManager getInstance()
    {
        return INSTANCE;
    }

    /**
     * Returns the plugins directory, creating it when absent.
     *
     * @return the plugins folder under ~/.jstudio
     */
    public File pluginsDir()
    {
        File dir = new File(System.getProperty("user.home"), ".jstudio" + File.separator + "plugins");
        if (!dir.exists())
        {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Returns the tracked plugins, for the manager UI.
     *
     * @return a new list, including ones that failed to load
     */
    public List<LoadedPlugin> getPlugins()
    {
        return new ArrayList<>(loaded);
    }

    /**
     * Scans the plugin jars on a background thread, then registers and activates the plugins on the EDT; later calls do nothing and nothing is thrown to the caller.
     *
     * @param mainFrame the main window the plugins contribute to
     */
    public void bootstrap(MainFrame mainFrame)
    {
        if (this.frame != null)
        {
            return;
        }
        this.frame = mainFrame;
        Thread loader = new Thread(() ->
        {
            List<ScannedJar> scanned = discover();
            SwingUtilities.invokeLater(() -> activateAll(scanned));
        }, "jstudio-plugin-loader");
        loader.setDaemon(true);
        loader.start();
    }

    /** Tears down every enabled plugin and closes every class loader; called on the EDT at exit. */
    public void shutdown()
    {
        Set<URLClassLoader> loaders = new HashSet<>();
        for (LoadedPlugin lp : loaded)
        {
            if (lp.state == LoadedPlugin.State.ENABLED)
            {
                teardown(lp);
            }
            if (lp.loader != null)
            {
                loaders.add(lp.loader);
            }
        }
        loaded.clear();
        for (URLClassLoader l : loaders)
        {
            JarPluginScanner.closeQuietly(l);
        }
    }

    /**
     * Removes a plugin from the persisted disabled set and activates it when it is a UI plugin; call on the EDT.
     *
     * @param lp the plugin to enable
     */
    public void enable(LoadedPlugin lp)
    {
        Set<String> disabled = Settings.getInstance().getDisabledPlugins();
        disabled.remove(lp.info.getId());
        Settings.getInstance().setDisabledPlugins(disabled);
        if (lp.isUi())
        {
            activate(lp);
        }
        else
        {
            lp.state = LoadedPlugin.State.NOT_UI;
        }
    }

    /**
     * Removes a plugin's contributions, disposes it and adds it to the persisted disabled set, keeping its classes loaded; call on the EDT.
     *
     * @param lp the plugin to disable
     */
    public void disable(LoadedPlugin lp)
    {
        if (lp.state == LoadedPlugin.State.ENABLED)
        {
            teardown(lp);
        }
        lp.state = LoadedPlugin.State.DISABLED;
        Set<String> disabled = Settings.getInstance().getDisabledPlugins();
        disabled.add(lp.info.getId());
        Settings.getInstance().setDisabledPlugins(disabled);
    }

    /**
     * Tears down every plugin from the same jar, closes its class loader and rescans the jar from disk; call on the EDT.
     *
     * @param lp a plugin from the jar to reload
     */
    public void reload(LoadedPlugin lp)
    {
        reloadJar(lp.jar);
    }

    /** Tears everything down, closes all class loaders and rescans the plugins directory, on the calling thread, which should be the EDT. */
    public void reloadAll()
    {
        Set<URLClassLoader> loaders = new HashSet<>();
        for (LoadedPlugin lp : loaded)
        {
            if (lp.state == LoadedPlugin.State.ENABLED)
            {
                teardown(lp);
            }
            if (lp.loader != null)
            {
                loaders.add(lp.loader);
            }
        }
        loaded.clear();
        for (URLClassLoader l : loaders)
        {
            JarPluginScanner.closeQuietly(l);
        }
        activateAll(discover());
    }

    private void reloadJar(File jar)
    {
        List<LoadedPlugin> fromJar = new ArrayList<>();
        for (LoadedPlugin lp : loaded)
        {
            if (lp.jar.equals(jar))
            {
                fromJar.add(lp);
            }
        }
        URLClassLoader loaderToClose = null;
        for (LoadedPlugin lp : fromJar)
        {
            if (lp.state == LoadedPlugin.State.ENABLED)
            {
                teardown(lp);
            }
            if (lp.loader != null)
            {
                loaderToClose = lp.loader;
            }
        }
        loaded.removeAll(fromJar);
        if (loaderToClose != null)
        {
            JarPluginScanner.closeQuietly(loaderToClose);
        }
        registerScanned(scanJar(jar));
    }

    private List<ScannedJar> discover()
    {
        List<ScannedJar> result = new ArrayList<>();
        File[] jars = pluginsDir().listFiles((d, n) -> n.toLowerCase().endsWith(".jar"));
        if (jars != null)
        {
            for (File jar : jars)
            {
                result.add(scanJar(jar));
            }
        }
        return result;
    }

    private ScannedJar scanJar(File jar)
    {
        ScannedJar scanned = new ScannedJar(jar);
        try
        {
            JarPluginScanner.ScanResult result = JarPluginScanner.scan(jar, getClass().getClassLoader());
            scanned.loader = result.loader;
            scanned.plugins = result.plugins;
        }
        catch (Throwable t)
        {
            scanned.error = t;
        }
        return scanned;
    }

    private void activateAll(List<ScannedJar> scanned)
    {
        for (ScannedJar jar : scanned)
        {
            registerScanned(jar);
        }
    }

    private void registerScanned(ScannedJar scanned)
    {
        if (scanned.error != null)
        {
            LoadedPlugin lp = new LoadedPlugin(scanned.jar, jarInfo(scanned.jar), null, null, LoadedPlugin.State.ERROR);
            lp.error = scanned.error;
            loaded.add(lp);
            return;
        }
        if (scanned.plugins == null || scanned.plugins.isEmpty())
        {
            if (scanned.loader != null)
            {
                JarPluginScanner.closeQuietly(scanned.loader);
            }
            return;
        }

        Set<String> disabled = Settings.getInstance().getDisabledPlugins();
        for (Plugin plugin : scanned.plugins)
        {
            try
            {
                PluginInfo info = plugin.getInfo();
                if (!(plugin instanceof UiPlugin))
                {
                    loaded.add(new LoadedPlugin(scanned.jar, info, scanned.loader, plugin, LoadedPlugin.State.NOT_UI));
                    continue;
                }
                LoadedPlugin lp = new LoadedPlugin(scanned.jar, info, scanned.loader, plugin, LoadedPlugin.State.DISABLED);
                loaded.add(lp);
                if (!disabled.contains(info.getId()))
                {
                    activate(lp);
                }
            }
            catch (Throwable t)
            {
                LoadedPlugin lp = new LoadedPlugin(scanned.jar, jarInfo(scanned.jar), scanned.loader, plugin, LoadedPlugin.State.ERROR);
                lp.error = t;
                loaded.add(lp);
            }
        }
    }

    private void activate(LoadedPlugin lp)
    {
        lp.contributions.clear();
        LiveGuiPluginContext context = new LiveGuiPluginContext(lp.info.getName(), lp.info.getId());
        context.setExportDir(new File(new File(pluginsDir(), lp.info.getId()), "export"));
        JStudioHostImpl host = new JStudioHostImpl(frame, lp.info, context, lp.contributions);
        lp.host = host;
        try
        {
            lp.plugin.init(context);
            ((UiPlugin) lp.plugin).start(host);
            lp.state = LoadedPlugin.State.ENABLED;
            lp.error = null;
        }
        catch (Throwable t)
        {
            lp.state = LoadedPlugin.State.ERROR;
            lp.error = t;
            teardown(lp);
        }
    }

    private void teardown(LoadedPlugin lp)
    {
        List<Registration> regs = lp.contributions;
        for (int i = regs.size() - 1; i >= 0; i--)
        {
            try
            {
                regs.get(i).remove();
            }
            catch (Throwable ignored)
            {
            }
        }
        regs.clear();
        if (lp.plugin != null)
        {
            try
            {
                lp.plugin.dispose();
            }
            catch (Throwable ignored)
            {
            }
        }
    }

    private static PluginInfo jarInfo(File jar)
    {
        return PluginInfo.builder().name(jar.getName()).version("").build();
    }

    private static final class ScannedJar
    {
        final File jar;
        URLClassLoader loader;
        List<Plugin> plugins;
        Throwable error;

        ScannedJar(File jar)
        {
            this.jar = jar;
        }
    }
}
