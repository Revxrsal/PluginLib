package pluginlib;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Represents an extension of {@link JavaPlugin} that allows you to download and relocate libraries
 * at runtime.
 *
 * For information on using, please check <a href="https://github.com/ReflxctionDev/PluginLib/blob/master/README.md">this guide</a>.
 */
public abstract class DependentJavaPlugin extends JavaPlugin {

    static {
        FileRelocator.load(DependentJavaPlugin.class);
        PluginLib.loadLibs();
    }

}
