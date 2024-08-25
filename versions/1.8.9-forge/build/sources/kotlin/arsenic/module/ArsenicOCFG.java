package arsenic.module;

import arsenic.module.property.impl.StringProperty;
import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.core.ConfigUtils;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigHeader;

import java.lang.reflect.Field;

public class ArsenicOCFG extends Config {

    public boolean dummyVar;

    private final Module module;

    public ArsenicOCFG(Mod modData, String configFile, boolean enabled, Module module) {
        super(modData, configFile, enabled);
        this.module = module;
    }

    //tries to add a headerproperty but fails
    @Override
    public void initialize() {
        module.getProperties().forEach(property -> {
            System.out.println( "Hello from" + property.getClass());
            BasicOption configOption = property.getOption();
            if(configOption != null) {
                optionNames.put(property.getName().replaceAll(" ", ""), configOption);
                ConfigUtils.getSubCategory(mod.defaultPage, "General", "").options.add(configOption);
            }
        });
        super.initialize();
    }
}
