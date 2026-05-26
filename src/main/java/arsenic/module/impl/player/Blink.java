package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.lag.LagManager;
import arsenic.utils.render.RenderUtils;
import net.minecraft.network.Packet;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.ArrayList;

@ModuleInfo(name = "Blink", category = ModuleCategory.PLAYER)
public class Blink extends Module {

    public final DoubleProperty doubleProperty = new DoubleProperty("Max Blink ticks", new DoubleValue(1, 20, 20, 1));
    private Vec3 startPos;
    private int ticksElapsed;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onPacket = event -> {
        ticksElapsed++;
        if(ticksElapsed > doubleProperty.getValue().getInput()) {
            onDisable();
            onEnable();
        }
    };

    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (startPos == null) return;
        RenderUtils.drawBoundingBox(startPos, new Color(255, 255, 255));
    };

    @RequiresPlayer
    @Override
    protected void onEnable() {
        startPos = mc.thePlayer.getPositionVector();
        LagManager.acquire(this.getClass());
    }

    @Override
    protected void onDisable() {
        startPos = null;
        ticksElapsed = 0;
        LagManager.release(this.getClass());
    }
}