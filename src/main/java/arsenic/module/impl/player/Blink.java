package arsenic.module.impl.player;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPlayerJoinWorld;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.lag.LagManager;
import arsenic.utils.render.RenderUtils;
import net.minecraft.util.Vec3;

import java.awt.*;

@ModuleInfo(name = "Blink", category = ModuleCategory.PLAYER)
public class Blink extends Module {

    public final DoubleProperty doubleProperty = new DoubleProperty("Max Blink ticks", new DoubleValue(1, 20, 20, 1));
    private Vec3 startPos;
    private int ticksElapsed;
    private static final int JOIN_GRACE_TICKS = 200;

    private boolean worldReady() {
        return mc.thePlayer != null && mc.theWorld != null && mc.getNetHandler() != null;
    }

    private boolean canBlink() {
        return worldReady() && !mc.isSingleplayer() && mc.thePlayer.ticksExisted >= JOIN_GRACE_TICKS;
    }

    /** Drops held packets (they belong to a world that no longer exists) and turns the module off. */
    private void resetStale() {
        startPos = null;
        ticksElapsed = 0;
        LagManager.releaseAndDiscard(this.getClass());
        if (isEnabled())
            setEnabledSilently(false);
    }

    @EventLink
    public final Listener<EventPlayerJoinWorld> onJoinWorld = event -> {
        if (event.getEntity() == mc.thePlayer)
            resetStale();
    };

    @EventLink
    public final Listener<EventTick> onPacket = event -> {
        if (!worldReady()) {
            resetStale();
            return;
        }
        ticksElapsed++;
        if (ticksElapsed > doubleProperty.getValue().getInput()) {
            onDisable();
            onEnable();
        }
    };

    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (startPos == null) return;
        RenderUtils.drawBoundingBox(startPos, new Color(255, 255, 255));
    };

    @Override
    public String getHudInfo() {
        return ticksElapsed + "/" + (int) doubleProperty.getValue().getInput() + "t";
    }

    @Override
    protected void onEnable() {
        if (!canBlink()) {
            setEnabledSilently(false);
            return;
        }
        startPos = mc.thePlayer.getPositionVector();
        LagManager.acquire(this.getClass());
    }

    @Override
    protected void onDisable() {
        startPos = null;
        ticksElapsed = 0;
        if (worldReady())
            LagManager.release(this.getClass());
        else
            LagManager.releaseAndDiscard(this.getClass());
    }
}
