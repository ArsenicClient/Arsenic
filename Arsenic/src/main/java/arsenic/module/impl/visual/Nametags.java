package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ColourProperty;
import arsenic.utils.font.FontRendererExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StringUtils;
import org.lwjgl.opengl.GL11;

import java.awt.*;

@ModuleInfo(name = "Nametags", category = ModuleCategory.WORLD, hidden = true)
public class Nametags extends Module {

    public final ColourProperty color = new ColourProperty("Color:", 0xFF2ECC71);
    public final BooleanProperty showHealth = new BooleanProperty("Show Health", true);
    public final BooleanProperty showDistance = new BooleanProperty("Show Distance", false);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderListener = event -> {
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
        if (fr == null) return;

        for (EntityPlayer player : Minecraft.getMinecraft().theWorld.playerEntities) {
            if (player == mc.thePlayer) continue;
            if (AntiBot.isBot(player)) continue;
            if (player.isDead) continue;

            double x = (player.lastTickPosX + (player.posX - player.lastTickPosX) * event.partialTicks)
                    - mc.getRenderManager().viewerPosX;
            double y = (player.lastTickPosY + (player.posY - player.lastTickPosY) * event.partialTicks)
                    - mc.getRenderManager().viewerPosY;
            double z = (player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.partialTicks)
                    - mc.getRenderManager().viewerPosZ;

            String name = StringUtils.stripControlCodes(player.getName());
            String healthText = showHealth.getValue()
                    ? String.format(" §7%.1f", player.getHealth())
                    : "";
            String distText = showDistance.getValue()
                    ? String.format(" §7[%.0f]", mc.thePlayer.getDistanceToEntity(player))
                    : "";
            String text = name + healthText + distText;

            float scale = 0.02666667F;
            int textWidth = (int) fr.getWidth(text);
            int textHeight = (int) fr.getHeight(text);
            float halfWidth = textWidth / 2f;

            float healthPercent = player.getHealth() / player.getMaxHealth();
            int healthColor = healthPercent > 0.5f ? 0xFF2ECC71
                    : healthPercent > 0.25f ? 0xFFFFFF00
                    : 0xFFFF0000;

            GlStateManager.pushMatrix();
            GL11.glTranslated(x, y + player.height + 0.6, z);
            GL11.glNormal3f(0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(-mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(-scale, -scale, scale);
            GlStateManager.disableDepth();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            Gui.drawRect((int) (-halfWidth - 2), -2, (int) (halfWidth + 2), textHeight + 2,
                    new Color(0, 0, 0, 100).getRGB());

            fr.drawString(text, (int) (-halfWidth), 0, 0xFFFFFFFF);

            if (showHealth.getValue()) {
                Gui.drawRect((int) (-halfWidth - 2), textHeight + 2,
                        (int) (-halfWidth - 2 + (textWidth + 4) * healthPercent), textHeight + 3,
                        healthColor);
            }

            GL11.glDisable(GL11.GL_BLEND);
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    };
}
