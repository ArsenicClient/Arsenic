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
import arsenic.utils.java.ColorUtils;
import arsenic.utils.minecraft.BedwarsTracker;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.block.BlockBed;
import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Highlights your bed and alerts when it is broken.
 *
 * Which bed is yours comes from BedwarsTracker: the bed nearest to where you stand when the game starts or you
 * respawn, corrected by "You can't destroy your own bed!" if you ever hit it. The alert fires as soon as a half of
 * your bed disappears from a loaded chunk, or when the server says "BED DESTRUCTION > Your Bed was destroyed",
 * whichever comes first. Nothing is shown outside a BedWars game.
 */
@ModuleInfo(name = "BedAlarm", description = "Highlights your bed in BedWars and alerts when it is broken", category = ModuleCategory.PLAYER)
public class BedAlarm extends Module {

    public final BooleanProperty highlight = new BooleanProperty("Highlight", true);
    public final BooleanProperty chat = new BooleanProperty("Chat Alert", true);
    public final BooleanProperty sound = new BooleanProperty("Sound", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("BedAlarm", 4, 100, 150, 16);
    private final MSTimer alarmTimer = MSTimer.expired();
    private final List<BlockPos> beds = new ArrayList<>();
    private int game = -1;
    private boolean announced, alarmed;

    @Override
    protected void onEnable() {
        game = -1;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (game != BedwarsTracker.gameId()) {
            game = BedwarsTracker.gameId();
            beds.clear();
            announced = false;
            alarmed = false;
        }
        if (!BedwarsTracker.inGame()) {
            beds.clear();
            return;
        }

        List<BlockPos> known = BedwarsTracker.ownBed();
        if (!known.isEmpty() && !beds.containsAll(known)) {
            beds.clear();
            beds.addAll(known);
            if (!announced && chat.getValue())
                PlayerUtils.addWaterMarkedMessageToChat("BedAlarm found your bed and is guarding it.");
            announced = true;
        }

        boolean lost = false;
        for (BlockPos p : beds)
            if (mc.theWorld.isBlockLoaded(p) && !(mc.theWorld.getBlockState(p).getBlock() instanceof BlockBed))
                lost = true;
        if ((lost || BedwarsTracker.ownBedDestroyed()) && !alarmed) {
            alarmed = true;
            beds.clear();
            alarmTimer.reset();
            if (chat.getValue()) PlayerUtils.addWaterMarkedMessageToChat("Your bed was broken!");
            if (sound.getValue()) mc.thePlayer.playSound("random.anvil_land", 1f, 0.8f);
        }
    };

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
        if (!hud.getValue() || !BedwarsTracker.inGame()) return;
        boolean alarming = alarmTimer.getTime() < 5000;
        String text;
        if (alarming) text = "Bed broken!";
        else if (BedwarsTracker.ownBedDestroyed()) text = "Bed lost";
        else if (!beds.isEmpty()) text = "Bed OK";
        else text = "Looking for your bed...";
        panel.setSize(Math.max(150, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, alarming ? 0xC0AA1010 : 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
