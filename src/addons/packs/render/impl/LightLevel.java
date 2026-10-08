import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.BlockPos;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the light level on the ground around you, so you can see where hostile mobs can spawn. Spots at level 7 or below
 * (where hostile mobs can spawn in the dark) are red, the rest are white. Only open-air spots with a solid block under
 * them are shown. The numbers are refreshed every quarter second.
 */
@ModuleInfo(name = "LightLevel", description = "Shows the light level on the ground around you", category = ModuleCategory.RENDER)
public class LightLevel extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final DoubleProperty radius = new DoubleProperty("Radius", new DoubleValue(2, 12, 6, 1));

    private static final class Spot {
        final BlockPos pos;
        final int level;

        Spot(BlockPos pos, int level) {
            this.pos = pos;
            this.level = level;
        }
    }

    private final List<Spot> spots = new ArrayList<>();
    private int tickCounter;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (++tickCounter < 5) return;
        tickCounter = 0;
        spots.clear();
        int r = (int) radius.getValue().getInput();
        BlockPos origin = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            // find the ground: the first solid block below the player's feet within a few blocks
            for (int dy = 0; dy >= -4; dy--) {
                BlockPos ground = origin.add(x, dy, z);
                if (!mc.theWorld.isBlockLoaded(ground)) break;
                if (!mc.theWorld.getBlockState(ground).getBlock().getMaterial().isSolid()) continue;
                BlockPos spot = ground.up();
                if (mc.theWorld.getBlockState(spot).getBlock().getMaterial() != Material.air) break;
                spots.add(new Spot(spot, mc.theWorld.getLightFromNeighbors(spot)));
                break;
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || spots.isEmpty()) return;
        double vx = mc.getRenderManager().viewerPosX, vy = mc.getRenderManager().viewerPosY, vz = mc.getRenderManager().viewerPosZ;
        for (Spot s : spots) {
            drawText(String.valueOf(s.level), s.pos.getX() + 0.5 - vx, s.pos.getY() + 0.02 - vy, s.pos.getZ() + 0.5 - vz,
                    s.level <= 7 ? 0xFFFF5555 : 0xFFFFFFFF);
        }
    };

    /** Text lying flat on the ground, facing up, so it reads from above. */
    private static void drawText(String text, double x, double y, double z, int argb) {
        GlStateManager.pushMatrix();
        GL11.glTranslated(x, y, z);
        GlStateManager.rotate(90f, 1f, 0f, 0f);
        float s = 0.02f;
        GlStateManager.scale(s, s, s);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
        mc.fontRendererObj.drawString(text, -mc.fontRendererObj.getStringWidth(text) / 2, 0, argb);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }
}
