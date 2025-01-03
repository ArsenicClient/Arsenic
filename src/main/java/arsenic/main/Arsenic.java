package arsenic.main;

import arsenic.command.CommandManager;
import arsenic.config.ConfigManager;
import arsenic.config.LaunchID;
import arsenic.event.EventManager;
import arsenic.gui.click.ClickGuiScreen;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.ModuleManager;
import arsenic.utils.font.Fonts;
import arsenic.utils.minecraft.ServerInfo;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Mod(name = "Arsenic Client", modid = "arsenic", clientSideOnly = true, version = "1.0")
public class Arsenic {

    private final String clientName = "Arsenic";
    private final long clientVersion = 221020L;
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
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final LaunchID launchID = new LaunchID();

    @Mod.EventHandler
    public final void init(FMLInitializationEvent event) {
        logger.info("Loading {}, version {}...", clientName, getClientVersionString());

        getEventManager().subscribe(silentRotationManager);
        getEventManager().subscribe(serverInfo);

        logger.info("Subscribed silent rotation manager");

        logger.info("Loaded {} modules...", String.valueOf(moduleManager.initialize()));

        logger.info("Loaded {} themes...", String.valueOf(themeManager.initialize()));

        logger.info("Loaded {} configs...", String.valueOf(configManager.initialize()));

        logger.info("Loaded {} commands...", String.valueOf(commandManager.initialize()));

        fonts.initTextures();
        logger.info("Loaded fonts.");

        logger.info("Loaded {}.", clientName);

        executor.execute(() -> {
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                HttpPost post = new HttpPost("http://140.238.204.221:5001/log");
                post.setEntity(new StringEntity(launchID.getLaunchID()));
                System.out.println(client.execute(post));
                logger.info("Logged Launch with ID | " + launchID.getLaunchID());
            } catch (Exception e) {
                logger.info("Launch Logger Broke :( | " + launchID.getLaunchID());
                e.printStackTrace();
            }
            configManager.saveClientConfig();
        });
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
