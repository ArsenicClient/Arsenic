package arsenic.config;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.utils.interfaces.IConfig;
import arsenic.utils.interfaces.IContainable;

import java.io.File;
import java.util.Collection;

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
        return Arsenic.getArsenic().getModuleManager().getModulesSet();
    }

}
