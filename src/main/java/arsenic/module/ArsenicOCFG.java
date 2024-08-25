package arsenic.module;

import arsenic.module.property.impl.StringProperty;
import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.CustomOption;
import cc.polyfrost.oneconfig.config.annotations.Header;
import cc.polyfrost.oneconfig.config.core.ConfigUtils;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.config.elements.OptionPage;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigHeader;

import java.lang.reflect.Field;

public class ArsenicOCFG extends Config {

    @Header(text = "Fucker")
    public boolean fucker;

    public boolean dummyVar;

    @ArsenicProperty
    public boolean dummyVar2;

    private final Module module;
    private boolean flag;

    public ArsenicOCFG(Mod modData, String configFile, boolean enabled, Module module) {
        super(modData, configFile, enabled);
        this.module = module;
    }


    //tries to add a headerproperty but fails
    @Override
    public void initialize() {
        //adding it to every module
        System.out.println("Initializing Arsenic OCFG " + module.getName());
        try {
            System.out.println("Hi from String property" + "Sex");
            System.out.println("sex");
            Field field = getClass().getField("dummyVar");
            BasicOption configOption = new ConfigHeader(field, dummyVar, "sex", "General", "", 0);
            optionNames.put("sex", configOption);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        //actually checking if there is a string property
        module.getProperties().forEach(property -> {
            if(property instanceof StringProperty) {
                try {
                    System.out.println("Hi from String property" + ((StringProperty) property).getName());
                    System.out.println(property.getName());
                    Field field = getClass().getField("dummyVar");
                    BasicOption configOption = new ConfigHeader(field, dummyVar, property.getName(), "General", "", 0);
                    optionNames.put(property.getName().replaceAll(" ", ""), configOption);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        super.initialize();
        System.out.println(optionNames.toString());
    }


    //adds anything in this class annotated with @ArsenicProperty
    @Override
    protected BasicOption getCustomOption(Field field, CustomOption annotation, OptionPage page, Mod mod, boolean migrate) {
        BasicOption option = null;
        switch (annotation.id()) {
            case "arsenicOption":
                ArsenicProperty myOption = ConfigUtils.findAnnotation(field, ArsenicProperty.class);
                option = new ConfigHeader(field, dummyVar, "Arse Property", myOption.category(), myOption.subcategory(), 0);
                ConfigUtils.getSubCategory(page, myOption.category(), myOption.subcategory()).options.add(option);
                break;
        }
        return option;
    }
}
