package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.utils.render.RenderUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(name = "Tracers", category = ModuleCategory.RENDER, hidden = true)
public class Tracers extends Module {

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderListener = event -> {
        Vec3 eyes = mc.gameRenderer.mainCamera().position();
        // start just in front of the camera so the lines converge on the crosshair
        Vec3 start = eyes.add(Vec3.directionFromRotation(mc.player.getXRot(), mc.player.getYRot()).scale(0.2));

        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (AntiBot.isBot(player)) continue;

            Vec3 pos = RenderUtils.interpolatedPosition(player).add(0, player.getBbHeight() / 2, 0);
            RenderUtils.drawLine(start, pos, 0xFF000000 | getBedWarsColor(player), 1.5f);
        }
    };

    /** Team colour from a dyed leather chestplate, the way Bed Wars kits show it. */
    private int getBedWarsColor(Player player) {
        ItemStack chestplate = player.getItemBySlot(EquipmentSlot.CHEST);
        DyedItemColor dyed = chestplate.get(DataComponents.DYED_COLOR);
        if (dyed != null)
            return dyed.rgb();
        return ThemeManager.getMainColor();
    }
}
