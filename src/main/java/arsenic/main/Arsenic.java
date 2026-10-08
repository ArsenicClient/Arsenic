package arsenic.main;

import arsenic.addon.AddonManager;
import arsenic.command.CommandManager;
import arsenic.config.ConfigManager;
import arsenic.config.FriendManager;
import arsenic.config.LaunchID;
import arsenic.event.EventManager;
import arsenic.gui.ErrorOverlay;
import arsenic.gui.click.ClickGuiScreen;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.ModuleManager;
import arsenic.module.impl.client.CapeHandler;
import arsenic.notifications.NotificationManager;
import arsenic.utils.font.Fonts;
import arsenic.utils.lag.LagManager;
import arsenic.utils.minecraft.ServerInfo;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@Mod(name = "Arsenic Client", modid = "arsenic", clientSideOnly = true, version = "2.0", useMetadata = true)
public class Arsenic {

    private final String clientName = "Arsenic";
    private final long clientVersion = 221020L;
    private final Logger logger = LogManager.getLogger(clientName);
    private final EventManager eventManager = new EventManager();
    private final ModuleManager moduleManager = new ModuleManager();
    private final AddonManager addonManager = new AddonManager();
    private final Fonts fonts = new Fonts();
    private final ConfigManager configManager = new ConfigManager();
    private final CommandManager commandManager = new CommandManager();
    private final ClickGuiScreen clickGuiScreen = new ClickGuiScreen();
    private final ThemeManager themeManager = new ThemeManager();
    private final SilentRotationManager silentRotationManager = new SilentRotationManager();
    private final ServerInfo serverInfo = new ServerInfo();
    private final NotificationManager notificationManager = new NotificationManager();
    private final LaunchID launchID = new LaunchID();
    private final FriendManager friendManager = new FriendManager();
    private final ErrorOverlay errorOverlay = new ErrorOverlay();

    @Mod.EventHandler
    public final void init(FMLInitializationEvent event) {
        initialize();
        arsenic.utils.render.capture.SilentView.register();
    }

    /** Starts the client; Forge calls it through {@link #init}, the injector directly. */
    public final void initialize() {
        logger.info("Loading {}, version {}...", clientName, getClientVersionString());

        getEventManager().subscribe(silentRotationManager);
        getEventManager().subscribe(serverInfo);
        getEventManager().subscribe(notificationManager);
        getEventManager().subscribe(new LagManager());
        getEventManager().subscribe(new arsenic.utils.lag.PingTracker());
        getEventManager().subscribe(new arsenic.utils.minecraft.BadPacketsManager());
        getEventManager().subscribe(new arsenic.utils.minecraft.BedwarsTracker());
        getEventManager().subscribe(errorOverlay);

        logger.info("Subscribed managers");

        logger.info("Loaded {} modules...", String.valueOf(moduleManager.initialize()));

        logger.info("Loaded {} themes...", String.valueOf(themeManager.initialize()));

        logger.info("Loaded {} configs...", String.valueOf(configManager.initialize()));

        clickGuiScreen.init();
        logger.info("Built ClickGUI.");

        logger.info("Loaded {} commands...", String.valueOf(commandManager.initialize()));

        fonts.initTextures();
        logger.info("Loaded fonts.");

        CapeHandler.getInstance().init();
        logger.info("Loaded cape handler.");

        logger.info("Loaded {}.", clientName);
    }

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

    public final AddonManager getAddonManager() { return addonManager; }

    public final ErrorOverlay getErrorOverlay() { return errorOverlay; }

    public final Fonts getFonts() { return fonts; }

    public final ConfigManager getConfigManager() { return configManager; }

    public final CommandManager getCommandManager() { return commandManager; }

    public final ClickGuiScreen getClickGuiScreen() {
        return clickGuiScreen;
    }

    public final SilentRotationManager getSilentRotationManager() {
        return silentRotationManager;
    }

    public final ThemeManager getThemeManager() { return themeManager; }

    public final ServerInfo getServerInfo() { return serverInfo; }

    public final LaunchID getLaunchID() { return launchID; }

    public final FriendManager getFriendManager() { return friendManager; }
}
