package arsenic.main;

import java.util.Arrays;
import java.util.Collection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import arsenic.event.EventManager;
import arsenic.module.ModuleManager;
import arsenic.module.property.Property;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleProperty.DoubleProperty;
import arsenic.module.property.impl.doubleProperty.DoubleValue;
import arsenic.utils.font.Fonts;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import arsenic.utils.rotations.RotationManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

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
            blurIntensityProperty = new DoubleProperty("Blur Intensity", new DoubleValue(0D, 10D, 5D, 0.125D));

    @Mod.EventHandler
    public final void init(FMLInitializationEvent event) {

        logger.info("Loading {}, version {}...", clientName, getClientVersionString());

        logger.info("Loaded {} modules.", String.valueOf(moduleManager.initialize()));

        fonts.initTextures();
        logger.info("Loaded fonts.");

        new RotationManager();
        logger.info("Loaded RotationManager.");

        logger.info("Loaded {}.", clientName);

    }

    @Contract(pure = true)
    @Override
    public final @NotNull Collection<IContainable> getContents() {
        return Arrays.asList(customFontProperty, blurIntensityProperty);
    }

    @Override
    public String getName() {
        return clientName;
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

    @Contract(pure = true)
    public final @NotNull String getClientVersionString() {
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
