import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.block.BlockBed;
import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Guards your bed. When you enable it, it remembers the bed blocks within 3 blocks of you, so enable it while standing
 * next to your bed. If one of those blocks is broken, it alerts you in chat, plays a sound and flashes the HUD. Only
 * loaded blocks are checked, so a bed far away out of render distance is not reported as lost.
 */
@ModuleInfo(name = "BedAlarm", description = "Alerts when your bed is broken. Enable it next to your bed", category = ModuleCategory.PLAYER)
public class BedAlarm extends Module {

    public final BooleanProperty chat = new BooleanProperty("Chat Alert", true);
    public final BooleanProperty sound = new BooleanProperty("Sound", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("BedAlarm", 4, 100, 120, 16);
    private final MSTimer alarmTimer = MSTimer.expired();
    private final List<BlockPos> beds = new ArrayList<>();
    private int tickCounter;

    @Override
    protected void onEnable() {
        beds.clear();
        tickCounter = 0;
        if (mc.thePlayer == null || mc.theWorld == null) return;
        BlockPos origin = new BlockPos(mc.thePlayer);
        for (int x = -3; x <= 3; x++) for (int y = -3; y <= 3; y++) for (int z = -3; z <= 3; z++) {
            BlockPos p = origin.add(x, y, z);
            if (mc.theWorld.getBlockState(p).getBlock() instanceof BlockBed) beds.add(p);
        }
        if (beds.isEmpty()) PlayerUtils.addWaterMarkedMessageToChat("BedAlarm: no bed within 3 blocks. Stand next to your bed and enable it again.");
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (beds.isEmpty() || ++tickCounter < 10) return;
        tickCounter = 0;

        Iterator<BlockPos> it = beds.iterator();
        while (it.hasNext()) {
            BlockPos p = it.next();
            if (!mc.theWorld.isBlockLoaded(p) || mc.theWorld.getBlockState(p).getBlock() instanceof BlockBed) continue;
            it.remove();
            alarmTimer.reset();
            if (chat.getValue()) PlayerUtils.addWaterMarkedMessageToChat("Your bed is under attack!");
            if (sound.getValue()) mc.thePlayer.playSound("random.anvil_land", 1f, 0.8f);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue() || (beds.isEmpty() && alarmTimer.getTime() > 5000)) return;
        String text = beds.isEmpty() ? "Bed lost" : "Bed OK (" + beds.size() + " blocks)";
        boolean alarming = alarmTimer.getTime() < 5000;
        if (alarming) text = "Bed under attack!";
        panel.setSize(Math.max(120, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, alarming ? 0xC0AA1010 : 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
