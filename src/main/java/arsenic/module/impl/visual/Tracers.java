package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.ColourProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

import java.awt.*;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

@ModuleInfo(name = "Tracers", category = ModuleCategory.WORLD, hidden = true)
public class Tracers extends Module {

    public enum TracerOrigin {
        CENTER, TOP, BOTTOM
    }

    public final ColourProperty color = new ColourProperty("Color:", 0xFF2ECC71);
    public final BooleanProperty bedWars = new BooleanProperty("BedWars", false);
    public final EnumProperty<TracerOrigin> origin = new EnumProperty<>("Origin", TracerOrigin.CENTER);
    public final DoubleProperty lineWidth = new DoubleProperty("Line Width", new DoubleValue(0.5, 5.0, 2.0, 0.5));

    private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
    private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
    private final FloatBuffer screenCoords = BufferUtils.createFloatBuffer(3);

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> matrixCapture = event -> {
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> renderListener = event -> {
        ScaledResolution sr = event.getSr();
        float scaleFactor = sr.getScaleFactor();
        int viewH = viewport.get(3);

        for (EntityPlayer player : Minecraft.getMinecraft().theWorld.playerEntities) {
            if (player == mc.thePlayer) continue;
            if (AntiBot.isBot(player)) continue;

            double x = player.lastTickPosX + (player.posX - player.lastTickPosX) * event.getPartialTicks();
            double y = player.lastTickPosY + (player.posY - player.lastTickPosY) * event.getPartialTicks();
            double z = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * event.getPartialTicks();

            screenCoords.rewind();
            GLU.gluProject((float) x, (float) y, (float) z, modelView, projection, viewport, screenCoords);
            float sx = screenCoords.get(0) / scaleFactor;
            float sy = (viewH - screenCoords.get(1)) / scaleFactor;
            float depth = screenCoords.get(2);

            if (depth > 1) continue;

            Color c = new Color(bedWars.getValue() ? getBedWarsColor(player) : color.getValue());

            float originX, originY;
            switch (origin.getValue()) {
                case TOP:
                    originX = sr.getScaledWidth() / 2f;
                    originY = 0;
                    break;
                case BOTTOM:
                    originX = sr.getScaledWidth() / 2f;
                    originY = sr.getScaledHeight();
                    break;
                default:
                    originX = sr.getScaledWidth() / 2f;
                    originY = sr.getScaledHeight() / 2f;
                    break;
            }

            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_LINE_SMOOTH);
            GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
            GL11.glLineWidth((float) lineWidth.getValue().getInput());
            GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f);

            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(originX, originY);
            GL11.glVertex2f(sx, sy);
            GL11.glEnd();

            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_LINE_SMOOTH);
            GL11.glPopMatrix();
        }
    };

    private int getBedWarsColor(EntityPlayer player) {
        if (player.getCurrentArmor(2) != null) {
            net.minecraft.nbt.NBTTagCompound tag = player.getCurrentArmor(2).getTagCompound();
            if (tag != null) {
                net.minecraft.nbt.NBTTagCompound display = tag.getCompoundTag("display");
                if (display != null && display.hasKey("color", 3)) {
                    return display.getInteger("color");
                }
            }
        }
        return color.getValue();
    }
}
