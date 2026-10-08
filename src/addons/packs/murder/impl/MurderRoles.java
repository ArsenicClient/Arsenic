import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import org.lwjgl.opengl.GL11;

/**
 * Murder Mystery hints: a player holding a sword is labelled as the suspected murderer, a player holding a bow as the
 * suspected detective, above their head through walls. These are only hints from the items in hand, not a confirmed
 * role. Filtered players are skipped.
 */
@ModuleInfo(name = "MurderRoles", description = "Labels players holding a sword (suspected murderer) or a bow (suspected detective)", category = ModuleCategory.RENDER)
public class MurderRoles extends Module {

    public final BooleanProperty render = new BooleanProperty("Render", true);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue()) return;
        double vx = mc.getRenderManager().viewerPosX, vy = mc.getRenderManager().viewerPosY, vz = mc.getRenderManager().viewerPosZ;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || AntiBot.isBot(p)) continue;
            ItemStack held = p.getHeldItem();
            if (held == null) continue;
            String label;
            int color;
            if (held.getItem() instanceof ItemSword) {
                label = "Suspected murderer";
                color = 0xFFFF5555;
            } else if (held.getItem() instanceof ItemBow) {
                label = "Suspected detective";
                color = 0xFF5599FF;
            } else {
                continue;
            }
            drawText(label, p.lastTickPosX + (p.posX - p.lastTickPosX) * event.partialTicks - vx,
                    p.posY + p.height + 0.5 - vy, p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * event.partialTicks - vz, color);
        }
    };

    private static void drawText(String text, double x, double y, double z, int argb) {
        GlStateManager.pushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glNormal3f(0f, 1f, 0f);
        GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0f, 1f, 0f);
        GlStateManager.rotate(mc.getRenderManager().playerViewX, 1f, 0f, 0f);
        float s = 0.025f;
        GlStateManager.scale(-s, -s, s);
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
