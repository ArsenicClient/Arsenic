package arsenic.main;

import arsenic.command.CommandManager;
import arsenic.config.ConfigManager;
import arsenic.config.LaunchID;
import arsenic.event.EventManager;
import arsenic.event.impl.EventRender2D;
import arsenic.gui.ErrorOverlay;
import arsenic.gui.click.ClickGuiScreen;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.ModuleManager;
import arsenic.module.impl.client.CapeHandler;
import arsenic.notifications.NotificationManager;
import arsenic.utils.font.Fonts;
import arsenic.utils.lag.LagManager;
import arsenic.utils.minecraft.ServerInfo;
import arsenic.utils.render.RenderContext;
import arsenic.utils.rotations.SilentRotationManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Fabric client entrypoint. Fabric constructs this class itself (see {@code fabric.mod.json}), so
 * the singleton is captured in the instance initialiser - before any field below runs - which lets
 * managers that call {@link #getInstance()} while constructing see a non-null client.
 */
public class Arsenic implements ClientModInitializer {

    private static Arsenic instance;

    {
        instance = this;
    }

    private final String clientName = "Arsenic";
    private final long clientVersion = 260300L;
    private final Logger logger = LogManager.getLogger(clientName);
    private final EventManager eventManager = new EventManager();
    private final ModuleManager moduleManager = new ModuleManager();
    private final Fonts fonts = new Fonts();
    private final ConfigManager configManager = new ConfigManager();
    private final CommandManager commandManager = new CommandManager();
    private final ClickGuiScreen clickGuiScreen = new ClickGuiScreen();
    private final ThemeManager themeManager = new ThemeManager();
    private final SilentRotationManager silentRotationManager = new SilentRotationManager();
    private final ServerInfo serverInfo = new ServerInfo();
    private final NotificationManager notificationManager = new NotificationManager();
    private final LaunchID launchID = new LaunchID();
    private final ErrorOverlay errorOverlay = new ErrorOverlay();

    @Override
    public void onInitializeClient() {
        logger.info("Loading {}, version {}...", clientName, getClientVersionString());

        getEventManager().subscribe(silentRotationManager);
        getEventManager().subscribe(serverInfo);
        getEventManager().subscribe(notificationManager);
        getEventManager().subscribe(new LagManager());
        getEventManager().subscribe(errorOverlay);

        logger.info("Subscribed managers");

        logger.info("Loaded {} modules...", String.valueOf(moduleManager.initialize()));

        logger.info("Loaded {} themes...", String.valueOf(themeManager.initialize()));

        logger.info("Loaded {} configs...", String.valueOf(configManager.initialize()));

        logger.info("Loaded {} commands...", String.valueOf(commandManager.initialize()));

        // Drawn last so client overlays sit on top of every vanilla HUD element.
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("arsenic", "hud"), (graphics, deltaTracker) -> {
            try (RenderContext ignored = RenderContext.begin(graphics)) {
                eventManager.post(new EventRender2D(graphics, deltaTracker.getGameTimeDeltaPartialTick(false)));
            }
        });

        // The ClickGUI resolves fonts and textures while building, which needs resources loaded.
        // The component tree also needs the module list and a current theme, both set up above.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            clickGuiScreen.buildComponents();
            logger.info("Built ClickGUI.");

            CapeHandler.getInstance().init();
            logger.info("Loaded cape handler.");
        });

        logger.info("Loaded {}.", clientName);
    }

    public String getName() { return clientName; }

    public static Arsenic getInstance() { return instance; }

    public static Arsenic getArsenic() { return instance; }

    public final String getClientName() { return clientName; }

    public final long getClientVersion() { return clientVersion; }

    @Contract(pure = true)
    public final @NotNull String getClientVersionString() { return String.valueOf(clientVersion); }

    public final Logger getLogger() { return logger; }

    public final EventManager getEventManager() { return eventManager; }

    public final ModuleManager getModuleManager() { return moduleManager; }

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
}
