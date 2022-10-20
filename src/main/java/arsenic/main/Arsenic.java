package arsenic.main;

import arsenic.event.EventManager;
import arsenic.module.ModuleManager;
import arsenic.module.property.Property;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.DoubleProperty;
import arsenic.utils.font.Fonts;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.Collection;

@Mod(name = "Arsenic Client", modid = "arsenic", clientSideOnly = true)
public class Arsenic implements IContainer {

    private final String clientName =   "Arsenic";
    private final long clientVersion =  221020L;

    private final Logger logger = LogManager.getLogger(clientName);
    private final EventManager eventManager = new EventManager();
    private final ModuleManager moduleManager = new ModuleManager();
    private final Fonts fonts = new Fonts();

    private final Property<?> // placeholders rn
            customFontProperty = new BooleanProperty("Custom Font", false),
            blurIntensityProperty = new DoubleProperty("Blur Intensity", 5D, 0D, 10D, 0.25D);

    @Mod.EventHandler
    public final void init(FMLInitializationEvent event) {

        logger.info("Loading {}, version {}...", clientName, getClientVersionString());

        logger.info("Loaded {} modules.", String.valueOf(moduleManager.initialize()));

        fonts.initTextures();
        logger.info("Loaded fonts.");

        logger.info("Loaded {}.", clientName);

    }

    @Override
    public final Collection<IContainable> getContents() {
        return Arrays.asList(customFontProperty, blurIntensityProperty);
    }

    @Mod.Instance
    private static Arsenic instance;

    public static Arsenic getInstance() {
        return instance;
    }

    public static Arsenic getArsenic() {
        return instance;
    }

    public final String getClientName() {
        return clientName;
    }

    public final long getClientVersion() {
        return clientVersion;
    }

    public final String getClientVersionString() {
        return String.valueOf(clientVersion);
    }

    public final Logger getLogger() {
        return logger;
    }

    public final EventManager getEventManager() {
        return eventManager;
    }

    public final ModuleManager getModuleManager() {
        return moduleManager;
    }

    public final Fonts getFonts() {
        return fonts;
    }

    public final BooleanProperty getCustomFontProperty() {
        return (BooleanProperty) customFontProperty;
    }

}
