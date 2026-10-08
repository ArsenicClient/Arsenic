import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.block.BlockBed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Finds your bed on its own and guards it.
 *
 * After you join (or respawn) the addon takes your position as home and looks for bed blocks within the search radius
 * of it, keeping the one nearest home plus its other half. Those blocks are outlined in the theme colour. If one of them
 * is later broken (its chunk still loaded and no bed there any more), it alerts in chat, plays a sound and flashes the
 * HUD. It gives up searching after 30 seconds and does not search again after the bed has been found.
 */
@ModuleInfo(name = "BedAlarm", description = "Finds your bed automatically, highlights it and alerts when it is broken", category = ModuleCategory.PLAYER)
public class BedAlarm extends Module {

    public final DoubleProperty searchRadius = new DoubleProperty("Search Radius", new DoubleValue(3, 12, 8, 1));
    public final BooleanProperty highlight = new BooleanProperty("Highlight", true);
    public final BooleanProperty chat = new BooleanProperty("Chat Alert", true);
    public final BooleanProperty sound = new BooleanProperty("Sound", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private static final int SEARCH_TICKS = 600;

    private final HudElement panel = hudElement("BedAlarm", 4, 100, 150, 16);
    private final MSTimer alarmTimer = MSTimer.expired();
    private final List<BlockPos> beds = new ArrayList<>();
    private EntityPlayer owner;
    private double homeX, homeY, homeZ;
    private int searchTicks, checkTicks;
    private boolean found, gaveUp;

    @Override
    protected void onEnable() {
        reset();
    }

    private void reset() {
        beds.clear();
        owner = null;
        searchTicks = 0;
        checkTicks = 0;
        found = false;
        gaveUp = false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.thePlayer != owner) {
            // new player entity: a fresh join or a respawn, so the home position is new
            reset();
            owner = mc.thePlayer;
            homeX = owner.posX;
            homeY = owner.posY;
            homeZ = owner.posZ;
        }

        if (!found) {
            if (gaveUp) return;
            if (++searchTicks > SEARCH_TICKS) {
                gaveUp = true;
                return;
            }
            if (searchTicks % 10 == 0) findBed();
            return;
        }

        if (++checkTicks < 10) return;
        checkTicks = 0;
        Iterator<BlockPos> it = beds.iterator();
        boolean lost = false;
        while (it.hasNext()) {
            BlockPos p = it.next();
            if (!mc.theWorld.isBlockLoaded(p) || mc.theWorld.getBlockState(p).getBlock() instanceof BlockBed) continue;
            it.remove();
            lost = true;
        }
        if (lost) {
            alarmTimer.reset();
            if (chat.getValue()) PlayerUtils.addWaterMarkedMessageToChat("Your bed is under attack!");
            if (sound.getValue()) mc.thePlayer.playSound("random.anvil_land", 1f, 0.8f);
        }
    };

    private void findBed() {
        BlockPos home = new BlockPos(homeX, homeY, homeZ);
        int r = (int) searchRadius.getValue().getInput();
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            BlockPos p = home.add(x, y, z);
            if (!mc.theWorld.isBlockLoaded(p) || !(mc.theWorld.getBlockState(p).getBlock() instanceof BlockBed)) continue;
            double d = p.distanceSq(homeX, homeY, homeZ);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest == null) return;

        // both halves of the bed are bed blocks next to each other
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            BlockPos p = nearest.add(x, y, z);
            if (mc.theWorld.isBlockLoaded(p) && mc.theWorld.getBlockState(p).getBlock() instanceof BlockBed) beds.add(p);
        }
        found = true;
        if (chat.getValue()) PlayerUtils.addWaterMarkedMessageToChat("BedAlarm found your bed and is guarding it.");
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!highlight.getValue() || beds.isEmpty()) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        for (BlockPos p : beds) {
            RenderUtils.renderBlock(p, ColorUtils.withAlpha(main, 80), false, true);
            RenderUtils.renderBlock(p, ColorUtils.withAlpha(main, 230), true, false);
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (!hud.getValue()) return;
        boolean alarming = alarmTimer.getTime() < 5000;
        String text;
        if (alarming) text = "Bed under attack!";
        else if (found && !beds.isEmpty()) text = "Bed OK (" + beds.size() + " blocks)";
        else if (found) text = "Bed lost";
        else if (gaveUp) text = "No bed found";
        else text = "Searching for bed...";
        panel.setSize(Math.max(150, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, alarming ? 0xC0AA1010 : 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
