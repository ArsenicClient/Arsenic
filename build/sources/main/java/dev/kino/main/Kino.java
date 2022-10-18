package dev.kino.main;

import dev.kino.event.EventManager;
import dev.kino.module.ModuleManager;
import dev.kino.utils.font.Fonts;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(name = "Kino Client", modid = "kino", clientSideOnly = true)
public class Kino {

    private final String clientName =   "Kino";
    private final long clientVersion =  221015L;

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
    private static Kino instance;

    public static Kino getInstance() {
        return instance;
    }

    public static Kino getKino() {
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
