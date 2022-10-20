package arsenic.main;

import arsenic.event.EventManager;
import arsenic.module.ModuleManager;
import arsenic.utils.font.Fonts;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(name = "Arsenic Client", modid = "arsenic", clientSideOnly = true)
public class Arsenic {

    private final String clientName =   "Arsenic";
    private final long clientVersion =  221020L;

    private final Logger logger = LogManager.getLogger(clientName);
    private final EventManager eventManager = new EventManager();
    private final ModuleManager moduleManager = new ModuleManager();
    private final Fonts fonts = new Fonts();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {

        logger.info("Loading {}, version {}...", clientName, getClientVersionString());

        logger.info("Loaded {} modules.", String.valueOf(moduleManager.initialize()));

        fonts.initTextures();
        logger.info("Loaded fonts.");

        logger.info("Loaded {}.", clientName);

    }

    @Mod.Instance
    private static Arsenic instance;

    public static Arsenic getInstance() {
        return instance;
    }

    public static Arsenic getArsenic() {
        return instance;
    }

    public String getClientName() {
        return clientName;
    }

    public long getClientVersion() {
        return clientVersion;
    }

    public String getClientVersionString() {
        return String.valueOf(clientVersion);
    }

    public Logger getLogger() {
        return logger;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public Fonts getFonts() {
        return fonts;
    }

}
