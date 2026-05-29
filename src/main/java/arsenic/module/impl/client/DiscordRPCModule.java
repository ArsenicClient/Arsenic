package arsenic.module.impl.client;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.discord.DiscordRPCManager;

@ModuleInfo(name = "DiscordRPC", category = ModuleCategory.SETTINGS, hidden = true)
public class DiscordRPCModule extends Module {

    private static final long APP_ID = 1508792693688369252L;
    private static DiscordRPCManager rpcManager;

    public final BooleanProperty showServer = new BooleanProperty("Show Server", true);
    public final BooleanProperty showName = new BooleanProperty("Show Name", true);
    public final BooleanProperty showHealth = new BooleanProperty("Show Health", false);
    public final BooleanProperty showModuleCount = new BooleanProperty("Show Module Count", false);

    @Override
    protected void onEnable() {
        if (rpcManager == null) {
            rpcManager = new DiscordRPCManager(APP_ID);
        }
        rpcManager.setShowServer(showServer.getValue());
        rpcManager.setShowName(showName.getValue());
        rpcManager.setShowHealth(showHealth.getValue());
        rpcManager.setShowModuleCount(showModuleCount.getValue());
        rpcManager.start();
    }

    @Override
    protected void onDisable() {
        if (rpcManager != null) {
            rpcManager.stop();
        }
    }
}
