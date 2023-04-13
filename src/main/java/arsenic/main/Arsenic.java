package arsenic.main;

import java.util.Arrays;
import java.util.Collection;

import arsenic.event.ForgeEvents;
import arsenic.gui.click.ClickGuiScreen;
import arsenic.module.impl.visual.ClickGui;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import arsenic.command.CommandManager;
import arsenic.config.ConfigManager;
import arsenic.event.EventManager;
import arsenic.module.ModuleManager;
import arsenic.module.property.Property;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.Fonts;
import arsenic.utils.interfaces.IContainable;
import arsenic.utils.interfaces.IContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

//to do
// make velo have chance
// make custom font better
// make the module component enable on left click & open on right click

@Mod(name = "Arsenic Client", modid = "arsenic", clientSideOnly = true)
public class Arsenic implements IContainer<IContainable> {

    private final String clientName = "Arsenic";
    private final long clientVersion = 221020L;
    private final Logger logger = LogManager.getLogger(clientName);
    private final EventManager eventManager = new EventManager();
    private final ModuleManager moduleManager = new ModuleManager();
    private final Fonts fonts = new Fonts();
    private final ConfigManager configManager = new ConfigManager();
    private final CommandManager commandManager = new CommandManager();
    private final ClickGuiScreen clickGuiScreen = new ClickGuiScreen();
    private final SilentRotationManager silentRotationManager = new SilentRotationManager();

    private final Property<?> // placeholders rn
    customFontProperty = new BooleanProperty("Custom Font", false),
            blurIntensityProperty = new DoubleProperty("Blur Intensity", new DoubleValue(0D, 10D, 5D, 0.125D));

    @Mod.EventHandler
    public final void init(FMLInitializationEvent event) {

        logger.info("Loading {}, version {}...", clientName, getClientVersionString());

        MinecraftForge.EVENT_BUS.register(new ForgeEvents());
        logger.info("Hooked forge events");

        getEventManager().subscribe(silentRotationManager);
        logger.info("Subscribed silent rotation manager");

        logger.info("Loaded {} modules...", String.valueOf(moduleManager.initialize()));

        logger.info("Loaded {} configs...", String.valueOf(configManager.initialize()));

        logger.info("Loaded {} commands...", String.valueOf(commandManager.initialize()));

        fonts.initTextures();
        logger.info("Loaded fonts.");

        logger.info("Loaded {}.", clientName);

    }

    @Contract(pure = true)
    @Override
    public final @NotNull Collection<IContainable> getContents() {
        return Arrays.asList(customFontProperty, blurIntensityProperty);
    }

    @Override
    public String getName() { return clientName; }

    @Mod.Instance
    private static Arsenic instance;

    public static Arsenic getInstance() { return instance; }

    public static Arsenic getArsenic() { return instance; }

    public final String getClientName() { return clientName; }

    public final long getClientVersion() { return clientVersion; }

    @Contract(pure = true)
    public final @NotNull String getClientVersionString() { return String.valueOf(clientVersion); }

    public final Logger getLogger() { return logger; }

    public final EventManager getEventManager() { return eventManager; }

    public final ModuleManager getModuleManager() { return moduleManager; }

    public final Fonts getFonts() { return fonts; }

    public final BooleanProperty getCustomFontProperty() { return (BooleanProperty) customFontProperty; }

    public final ConfigManager getConfigManager() { return configManager; }

    public final CommandManager getCommandManager() { return commandManager; }

    public final ClickGuiScreen getClickGuiScreen() {
        return clickGuiScreen;
    }

}
