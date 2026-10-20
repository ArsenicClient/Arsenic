package arsenic.config;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.interfaces.IConfig;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.Collection;

/** One file in the Configs folder: the settings of every module. */
public class ModuleConfig implements IConfig<Module> {

    private final File config;

    public ModuleConfig(File config) {
        this.config = config;
    }

    @Override
    public File getDirectory() {
        return config;
    }

    @Override
    public Collection<Module> getContents() {
        return Arsenic.getArsenic().getModuleManager().getModules();
    }

    /** Modules the file does not mention, and settings it is missing, go back to their defaults instead of keeping the last config's. */
    @Override
    public void loadConfig() {
        JsonObject data = IConfig.read(config);
        Arsenic.getArsenic().getConfigManager().applyModules(data == null ? new JsonObject() : data);
    }

    public long lastModified() {
        return config.lastModified();
    }

    public boolean exists() {
        return config.isFile();
    }
}
