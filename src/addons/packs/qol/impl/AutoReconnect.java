import arsenic.module.ModuleTier;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;

/**
 * When you are disconnected from the last server you were on, connects back after a delay. Retries stop after the chosen
 * number of attempts in a row; the count resets once you have stayed in the world for 30 seconds. This is the one time
 * the addon acts with a GUI on screen: the disconnect screen.
 */
@ModuleInfo(name = "AutoReconnect", description = "Reconnects to the last server after a disconnect", category = ModuleCategory.PLAYER, tier = ModuleTier.QOL)
public class AutoReconnect extends Module {

    public final DoubleProperty delay = new DoubleProperty("Delay (s)", new DoubleValue(1, 60, 5, 1));
    public final DoubleProperty attempts = new DoubleProperty("Max Attempts", new DoubleValue(1, 20, 5, 1));

    private static final int STABLE_TICKS = 600;

    private ServerData lastServer;
    private final MSTimer waiting = new MSTimer();
    private boolean waitingToConnect;
    private int tries, stableTicks;

    @Override
    protected void onDisable() {
        waitingToConnect = false;
        tries = 0;
        stableTicks = 0;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.getCurrentServer() != null) lastServer = mc.getCurrentServer();

        if (mc.player != null && mc.level != null && mc.gui.screen() == null) {
            if (++stableTicks > STABLE_TICKS) tries = 0;
        } else {
            stableTicks = 0;
        }

        if (!(mc.gui.screen() instanceof DisconnectedScreen)) {
            waitingToConnect = false;
            return;
        }
        if (lastServer == null || tries >= (int) attempts.getValue().getInput()) return;

        if (!waitingToConnect) {
            waitingToConnect = true;
            waiting.reset();
        }
        if (waiting.hasTimeElapsed((long) (delay.getValue().getInput() * 1000), false)) {
            waitingToConnect = false;
            tries++;
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc,
                    net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(lastServer.ip), lastServer, false, null);
        }
    };
}
