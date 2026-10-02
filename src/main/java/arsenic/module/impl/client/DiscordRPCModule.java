package arsenic.module.impl.client;

import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.utils.discord.DiscordRPCManager;

@ModuleInfo(name = "DiscordRPC", category = ModuleCategory.CLIENT, hidden = true)
public class DiscordRPCModule extends Module {

    private static final long APP_ID = 1508792693688369252L;
    private static DiscordRPCManager rpcManager;


    @Override
    protected void onEnable() {
        if (rpcManager == null) {
            rpcManager = new DiscordRPCManager(APP_ID);
        }
        rpcManager.setShowServer(true);
        rpcManager.setShowName(true);
        rpcManager.setShowHealth(false);
        rpcManager.setShowModuleCount(false);
        rpcManager.start();
    }

    @Override
    protected void onDisable() {
        if (rpcManager != null) {
            rpcManager.stop();
        }
    }
}
