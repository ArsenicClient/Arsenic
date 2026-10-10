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
import net.minecraft.client.gui.GuiGameOver;

/**
 * Presses Respawn after a delay when the death screen appears. The delay is random between the two settings so it does
 * not look scripted. Respawn is the same request the Respawn button sends.
 */
@ModuleInfo(name = "AutoRespawn", description = "Presses Respawn after a delay when you die", category = ModuleCategory.PLAYER)
public class AutoRespawn extends Module {

    public final DoubleProperty minDelay = new DoubleProperty("Min Delay (s)", new DoubleValue(0, 10, 1, 0.1));
    public final DoubleProperty maxDelay = new DoubleProperty("Max Delay (s)", new DoubleValue(0, 10, 2, 0.1));

    private final MSTimer deathTimer = new MSTimer();
    private boolean onDeathScreen;
    private long wait;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        boolean dead = mc.currentScreen instanceof GuiGameOver;
        if (!dead) {
            onDeathScreen = false;
            return;
        }
        if (!onDeathScreen) {
            onDeathScreen = true;
            deathTimer.reset();
            double lo = Math.min(minDelay.getValue().getInput(), maxDelay.getValue().getInput());
            double hi = Math.max(minDelay.getValue().getInput(), maxDelay.getValue().getInput());
            wait = (long) ((lo + Math.random() * (hi - lo)) * 1000);
        }
        if (deathTimer.hasTimeElapsed(wait, false)) {
            onDeathScreen = false;
            mc.thePlayer.respawnPlayer();
        }
    };
}
