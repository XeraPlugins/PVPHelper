package me.ian;

import lombok.Getter;
import me.ian.command.CommandRegistrar;
import me.ian.file.Config;
import me.ian.tab.CreeperTroll;
import me.ian.tab.TabListUpdater;
import me.ian.time.TaskRegistrar;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author SevJ6
 */
public class PVPHelper extends JavaPlugin {

    public static final long START_TIME = System.currentTimeMillis();
    public static final ScheduledExecutorService EXECUTOR_SERVICE = Executors.newScheduledThreadPool(3);
    public static PVPHelper INSTANCE;
    private Config config;

    @Getter
    private CommandRegistrar commandRegistrar;

    // Return the custom Toml configuration
    public Config getRunningConfig() {
        return config;
    }

    @Override
    public void onEnable() {
        INSTANCE = this;
        loadMixins();
        config = new Config("config.toml");
        commandRegistrar = new CommandRegistrar();
        commandRegistrar.registerAllCommands();
        TaskRegistrar.register(TabListUpdater.class);
        TaskRegistrar.register(CreeperTroll.class);
    }

    @Override
    public void onDisable() {

    }

    private void loadMixins() {
        File mixinJar = new File(".", "mixins-temp.jar");
        if (mixinJar.exists()) mixinJar.delete();
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("mixins.dat");
            if (is == null) throw new RuntimeException("The plugin jar is missing the mixins");
            Files.copy(is, mixinJar.toPath());
            URLClassLoader ccl = new URLClassLoader(new URL[]{mixinJar.toURI().toURL()});
            Class<?> mixinMainClass = Class.forName(String.format("%s.mixin.MixinMain", getClass().getPackage().getName()), true, ccl);
            Object instance = mixinMainClass.newInstance();
            Method mainM = instance.getClass().getDeclaredMethod("init", JavaPlugin.class);
            mainM.invoke(instance, this);
        } catch (Throwable t) {
            getLogger().severe(String.format("Failed to load mixins due to %s. Please see the stacktrace below for more info", t.getClass().getName()));
            t.printStackTrace();
        } finally {
            if (mixinJar.exists()) mixinJar.delete();
        }
    }
}