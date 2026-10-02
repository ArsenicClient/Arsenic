package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.gui.themes.ThemeManager;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.java.JavaUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.awt.Color;

@ModuleInfo(name = "Esp", category = ModuleCategory.RENDER, hidden = true)
public class ESP extends Module {

    /**
     * How players are drawn. These were four independent toggles plus two folders of tuning - a
     * combinatorial mess where most combinations were either invisible or drew the same player
     * three times. They are one choice now, because they always were one choice.
     */
    public enum Mode {
        /** Shaded box plus outline. The default: readable at any distance, costs nothing. */
        Box,
        /** Coloured outline through walls - vanilla's glowing-entity outline, in the team colour. */
        Glow,
        /**
         * Flat coloured player models on 1.8. Modern Minecraft renders entities through its own
         * feature pipeline, so this now draws the same through-wall outline as Glow.
         */
        Chams
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Box);

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        for (Player entity : mc.level.players()) {
            if (!isTarget(entity))
                continue;
            AABB box = RenderUtils.interpolatedBox(entity);
            if (mode.getValue() == Mode.Box)
                RenderUtils.renderBox(box, 0xC0000000 | getBedWarsColor(entity), true, true);
            // Health always shows. It was off by default, which meant the module shipped without
            // the one piece of information that actually changes how you play a fight.
            drawHealthEsp(entity, box);
        }
    };

    private boolean isTarget(Entity entity) {
        return entity instanceof Player player && player != mc.player && !AntiBot.isBot(player);
    }

    /** Read by MixinMinecraft#shouldEntityAppearGlowing. */
    public boolean shouldGlow(Entity entity) {
        return isEnabled() && mode.getValue() != Mode.Box && isTarget(entity);
    }

    /** Read by MixinEntity#getTeamColor so the outline takes the BedWars team colour. */
    public int getGlowColour(Entity entity) {
        return entity instanceof Player player ? getBedWarsColor(player) & 0xFFFFFF : ThemeManager.getMainColor();
    }

    /** A health bar standing beside the player, always facing the camera. */
    private void drawHealthEsp(Player en, AABB box) {
        double r = JavaUtils.limit(en.getHealth() / en.getMaxHealth(), 0, 1);
        int hc = r < 0.3D ? Color.red.getRGB() : (r < 0.5D ? Color.orange.getRGB() : (r < 0.7D ? Color.yellow.getRGB() : Color.green.getRGB()));

        Vector3fc left = mc.gameRenderer.mainCamera().leftVector();
        double side = (box.maxX - box.minX) / 2 + 0.25;
        Vec3 centre = box.getCenter();
        Vec3 base = new Vec3(centre.x - left.x() * side, box.minY, centre.z - left.z() * side);
        Vec3 top = base.add(0, box.maxY - box.minY, 0);
        Vec3 filled = base.add(0, (box.maxY - box.minY) * r, 0);

        RenderUtils.drawLine(base, top, 0xFF000000, 4f);
        RenderUtils.drawLine(base, filled, hc, 2.5f);
    }

    /** Team colour from a dyed leather chestplate, the way BedWars kits show it. */
    public int getBedWarsColor(Player entityPlayer) {
        DyedItemColor dyed = entityPlayer.getItemBySlot(EquipmentSlot.CHEST).get(DataComponents.DYED_COLOR);
        if (dyed != null)
            return dyed.rgb();
        return ThemeManager.getMainColor();
    }
}
