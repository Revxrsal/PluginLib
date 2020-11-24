package pluginlib;

import org.bukkit.plugin.java.JavaPlugin;

public abstract class DependantJavaPlugin extends JavaPlugin {

    static {
        FileRelocator.load(DependantJavaPlugin.class);
        PluginLib.loadLibs();
    }

}
