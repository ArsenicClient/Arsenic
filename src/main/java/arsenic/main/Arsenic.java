package arsenic.main;

import arsenic.addon.AddonManager;
import arsenic.command.CommandManager;
import arsenic.config.ConfigManager;
import arsenic.config.FriendManager;
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

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
    private final FriendManager friendManager = new FriendManager();
    private final ErrorOverlay errorOverlay = new ErrorOverlay();

    /** Starts the client. Called by the injector (arsenic.runtime.InjectedLaunch) once the game is running. */
    public final void initialize() {
        logger.info("Loading {}, version {}...", clientName, getClientVersionString());

        getEventManager().subscribe(silentRotationManager);
        getEventManager().subscribe(serverInfo);
        getEventManager().subscribe(notificationManager);
        getEventManager().subscribe(new LagManager());
        getEventManager().subscribe(new arsenic.utils.lag.PingTracker());
        getEventManager().subscribe(new arsenic.utils.lag.TimerBalance());
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

    // set by arsenic.runtime.InjectedLaunch
    private static Arsenic instance;

    public static Arsenic getInstance() { return instance; }

    public static Arsenic getArsenic() { return instance; }

    public final String getClientName() { return clientName; }

    public final long getClientVersion() { return clientVersion; }

    @Contract(pure = true)
    public final @NotNull String getClientVersionString() {
        if (versionString == null)
            versionString = readJarVersion();
        return versionString;
    }

    private static String versionString;

    /**
     * The release version ("v3.n") from the jar's manifest. The package lookup only works when the class loader
     * defined the package from the manifest, which the injector's loaders do not always do, so the manifests on the
     * class path are also searched for Arsenic's own (the one naming the injector as its main class).
     */
    private static String readJarVersion() {
        Package pkg = Arsenic.class.getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null)
            return pkg.getImplementationVersion();
        try {
            ClassLoader loader = Arsenic.class.getClassLoader();
            java.util.Enumeration<java.net.URL> manifests = loader != null
                    ? loader.getResources("META-INF/MANIFEST.MF")
                    : ClassLoader.getSystemResources("META-INF/MANIFEST.MF");
            while (manifests.hasMoreElements()) {
                try (java.io.InputStream in = manifests.nextElement().openStream()) {
                    java.util.jar.Attributes main = new java.util.jar.Manifest(in).getMainAttributes();
                    String version = main.getValue("Implementation-Version");
                    if (version != null && "arsenic.inject.Injector".equals(main.getValue("Main-Class")))
                        return version;
                }
            }
        } catch (Exception ignored) {
        }
        return "dev";
    }

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

    public final FriendManager getFriendManager() { return friendManager; }
}
