package arsenic.utils.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import arsenic.injection.accessor.IMixinMinecraft;
import arsenic.injection.accessor.IMixinRenderManager;
import arsenic.utils.java.UtilityClass;
import arsenic.utils.render.shader.ShaderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;

import static net.minecraft.client.renderer.GlStateManager.color;
import static org.lwjgl.opengl.GL11.*;

import arsenic.main.Arsenic;

import static org.lwjgl.opengl.GL11.glColor4f;

public class RenderUtils extends UtilityClass {

    public static void setColor(final int color) {
        final float a = ((color >> 24) & 0xFF) / 255.0f;
        final float r = ((color >> 16) & 0xFF) / 255.0f;
        final float g = ((color >> 8) & 0xFF) / 255.0f;
        final float b = (color & 0xFF) / 255.0f;
        glColor4f(r, g, b, a);
    }

    public static void resetColorText() {
        color(1f, 1f, 1f, 1f);
    }

    public static void resetColor() {
        glColor4f(1f, 1f, 1f, 1f);
    }
    public static void bindTexture(int texture) {
        glBindTexture(GL_TEXTURE_2D, texture);
    }

    /**
     * Binds a texture on a texture unit GlStateManager does not track (it only knows units 0 to 7), then leaves unit 0
     * active. Selecting such a unit through GlStateManager.setActiveTexture makes its next bindTexture throw, and its
     * state stays on that unit, so every later texture bind in the game fails too.
     */
    public static void bindTextureOnUnit(int unit, int texture) {
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, texture);
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        GlStateManager.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
    }
    public static int alpha(Color color, int newAlpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), newAlpha).getRGB();
    }
    public static void setAlphaLimit(float alphaLimit) {
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL_GREATER,  alphaLimit * 0.01f);
    }
    public static boolean captureCoverage = false;

    public static void applyGuiBlend() {
        if (captureCoverage) {
            GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            OpenGlHelper.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        } else {
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    public static void startBlend() {
        GlStateManager.enableBlend();
        if (captureCoverage) {
            applyGuiBlend();
        } else {
            GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        }
    }
    public static void endBlend() {
        GlStateManager.disableBlend();
    }
    public static ResourceLocation getResourcePath(String s) {
        InputStream inputStream = Arsenic.class.getResourceAsStream(s);
        BufferedImage bf;
        try {
            assert inputStream != null;
            bf = ImageIO.read(inputStream);
            return Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("Arsenic", new DynamicTexture(bf));
        } catch (IOException | IllegalArgumentException | NullPointerException e) {
            e.printStackTrace();
            return new ResourceLocation("null");
        }
    }

    public static Color interpolateColoursColor(Color a, Color b, float f) {
        float rf = 1 - f;
        int red = (int) (a.getRed() * rf + b.getRed() * f);
        int green = (int) (a.getGreen() * rf + b.getGreen() * f);
        int blue = (int) (a.getBlue() * rf + b.getBlue() * f);
        int alpha = (int) (a.getAlpha() * rf + b.getAlpha() * f);
        return new Color(red, green, blue, alpha);
    }

    public static int interpolateColours(Color a, Color b, float f) {
        return interpolateColoursColor(a, b, f).getRGB();
    }

    public static int interpolateColoursInt(int a, int b, float f) {
        return interpolateColoursColor(new Color(a), new Color(b), f).getRGB();
    }

    public static void renderBlock(BlockPos blockPos, int color, boolean outline, boolean shade) {
        renderBox(blockPos.getX(), blockPos.getY(), blockPos.getZ(), color, outline, shade);
    }

    public static void renderBox(int x, int y, int z, int color, boolean outline, boolean shade) {
        double xPos = x - mc.getRenderManager().viewerPosX;
        double yPos = y - mc.getRenderManager().viewerPosY;
        double zPos = z - mc.getRenderManager().viewerPosZ;
        GL11.glPushMatrix();
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glLineWidth(2.0f);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);

        float n8 = (color >> 24 & 0xFF) / 255.0f;
        float n9 = (color >> 16 & 0xFF) / 255.0f;
        float n10 = (color >> 8 & 0xFF) / 255.0f;
        float n11 = (color & 0xFF) / 255.0f;

        GL11.glColor4f(n9, n10, n11, n8);

        AxisAlignedBB axisAlignedBB = new AxisAlignedBB(xPos, yPos, zPos, xPos + 1.0, yPos + 1.0, zPos + 1.0);

        if (outline) {
            RenderGlobal.drawSelectionBoundingBox(axisAlignedBB);
        }

        if (shade) {
            drawBoundingBox(axisAlignedBB, n9, n10, n11);
        }

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(3042);
        GL11.glPopMatrix();
    }

    public static void renderBlockFace(BlockPos blockPos, EnumFacing facing, int color, boolean outline, boolean shade) {
        double xPos = blockPos.getX() - mc.getRenderManager().viewerPosX;
        double yPos = blockPos.getY() - mc.getRenderManager().viewerPosY;
        double zPos = blockPos.getZ() - mc.getRenderManager().viewerPosZ;

        GL11.glPushMatrix();
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glLineWidth(2.0f);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);

        float a = 1;
        float r = (color >> 16 & 0xFF) / 255.0f;
        float g = (color >> 8  & 0xFF) / 255.0f;
        float b = (color       & 0xFF) / 255.0f;

        GL11.glColor4f(r, g, b, a);

        AxisAlignedBB faceBB;
        switch (facing) {
            case UP:
                faceBB = new AxisAlignedBB(xPos,       yPos + 1.0, zPos,       xPos + 1.0, yPos + 1.0, zPos + 1.0); break;
            case DOWN:
                faceBB = new AxisAlignedBB(xPos,       yPos,       zPos,       xPos + 1.0, yPos,       zPos + 1.0); break;
            case NORTH:
                faceBB = new AxisAlignedBB(xPos,       yPos,       zPos,       xPos + 1.0, yPos + 1.0, zPos      ); break;
            case SOUTH:
                faceBB = new AxisAlignedBB(xPos,       yPos,       zPos + 1.0, xPos + 1.0, yPos + 1.0, zPos + 1.0); break;
            case WEST:
                faceBB = new AxisAlignedBB(xPos,       yPos,       zPos,       xPos,       yPos + 1.0, zPos + 1.0); break;
            case EAST:
                faceBB = new AxisAlignedBB(xPos + 1.0, yPos,       zPos,       xPos + 1.0, yPos + 1.0, zPos + 1.0); break;
            default: return;
        }

        if (outline) {
            RenderGlobal.drawSelectionBoundingBox(faceBB);
        }

        if (shade) {
            drawFaceQuad(faceBB, facing, r, g, b, a);
        }

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(3042);
        GL11.glPopMatrix();
    }

    private static void drawFaceQuad(AxisAlignedBB bb, EnumFacing facing, float r, float g, float b, float a) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        GL11.glColor4f(r, g, b, a);
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);

        switch (facing) {
            case UP:
                wr.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
                wr.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
                wr.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
                wr.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
                break;
            case DOWN:
                wr.pos(bb.minX, bb.minY, bb.minZ).endVertex();
                wr.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
                wr.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
                wr.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
                break;
            case NORTH:
                wr.pos(bb.minX, bb.minY, bb.minZ).endVertex();
                wr.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
                wr.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
                wr.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
                break;
            case SOUTH:
                wr.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
                wr.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
                wr.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
                wr.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
                break;
            case WEST:
                wr.pos(bb.minX, bb.minY, bb.minZ).endVertex();
                wr.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
                wr.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
                wr.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
                break;
            case EAST:
                wr.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
                wr.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
                wr.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
                wr.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
                break;
        }

        tess.draw();
    }

    public static void drawBoundingBox(AxisAlignedBB abb, float r, float g, float b) {
        drawBoundingBox(abb, r, g, b, 0.25f);
    }

    public static void drawBoundingBox(Vec3 pos, Color color) {
        IMixinRenderManager renderManager = (IMixinRenderManager) mc.getRenderManager();
        double x = pos.xCoord - renderManager.getRenderPosX();
        double y = pos.yCoord - renderManager.getRenderPosY();
        double z = pos.zCoord - renderManager.getRenderPosZ();

        AxisAlignedBB playerBB = mc.thePlayer.getEntityBoundingBox();
        double width = playerBB.maxX - playerBB.minX;
        double height = playerBB.maxY - playerBB.minY;

        AxisAlignedBB axisalignedbb1 = new AxisAlignedBB(
                x - width / 2, y, z - width / 2,
                x + width / 2, y + height, z + width / 2
        );

        GlStateManager.pushMatrix();
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDepthMask(false);
        GL11.glLineWidth(2.0F);
        RenderGlobal.drawOutlinedBoundingBox(axisalignedbb1, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDepthMask(true);
        GL11.glLineWidth(1.0F);
        GlStateManager.popMatrix();
    }

    public static void drawBoundingBox(AxisAlignedBB abb, float r, float g, float b, float a) {
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer vb = ts.getWorldRenderer();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        ts.draw();
    }
    public static void drawShadedBoundingBox(AxisAlignedBB abb, int r, int g, int b, int a) {
        Tessellator ts = Tessellator.getInstance();
        WorldRenderer vb = ts.getWorldRenderer();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        ts.draw();
        vb.begin(7, DefaultVertexFormats.POSITION_COLOR);
        vb.pos(abb.minX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.minX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.minZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.maxY, abb.maxZ).color(r, g, b, a).endVertex();
        vb.pos(abb.maxX, abb.minY, abb.maxZ).color(r, g, b, a).endVertex();
        ts.draw();
    }

    public static void drawLineToEntity(Entity e, int r, int g, int b, int a, double lw) {
        if (e != null) {
            double x = e.lastTickPosX + (e.posX - e.lastTickPosX) * ((IMixinMinecraft) mc).getTimer().renderPartialTicks - mc.getRenderManager().viewerPosX;
            double y = (double) e.getEyeHeight() + e.lastTickPosY + (e.posY - e.lastTickPosY) * ((IMixinMinecraft) mc).getTimer().renderPartialTicks - mc.getRenderManager().viewerPosY;
            double z = e.lastTickPosZ + (e.posZ - e.lastTickPosZ) * ((IMixinMinecraft) mc).getTimer().renderPartialTicks - mc.getRenderManager().viewerPosZ;
            GL11.glPushMatrix();
            GL11.glEnable(3042);
            GL11.glEnable(GL_LINE_SMOOTH);
            GL11.glDisable(2929);
            GL11.glDisable(GL_TEXTURE_2D);
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glLineWidth((float) lw);
            GL11.glColor4f(r, g, b, a);
            GL11.glBegin(2);
            GL11.glVertex3d(0.0D, (double) mc.thePlayer.getEyeHeight(), 0.0D);
            GL11.glVertex3d(x, y, z);
            GL11.glEnd();
            GL11.glDisable(GL_BLEND);
            GL11.glEnable(GL_TEXTURE_2D);
            GL11.glEnable(2929);
            GL11.glDisable(GL_LINE_SMOOTH);
            GL11.glDisable(GL_BLEND);
            GL11.glPopMatrix();
        }
    }

    public static void color2(int color, float alpha) {
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        GlStateManager.color(r, g, b, alpha);
    }

    public static double ticks = 0;
    public static long lastFrame = 0;

    public enum RingStyle {
        /** A bobbing ring with a shaded curtain down to the ground and a glowing band. */
        CLASSIC,
        /** A ring of crystal spikes floating above the head that stand taller on each hit. */
        CROWN,
        /** Ice lances fired from your chest into the target, with a burst of shards from each hit. */
        ICE_LANCE,
        /** A ring whose arc shows health left, coloured from red to green, with a column marking the arc's end. */
        HEALTH_ARC
    }

    public static void drawCircle(Entity entity, float partialTicks, double rad, int colored, float alpha) {
        drawRing(entity, partialTicks, rad, colored, alpha, RingStyle.CLASSIC);
    }

    public static void drawRing(Entity entity, float partialTicks, double rad, int colored, float alpha, RingStyle style) {
        // callers pass 0-255, but glColor clamps to 1, so every fade below would be lost without this
        alpha = alpha > 1f ? alpha / 255f : alpha;
        ticks += .004 * (System.currentTimeMillis() - lastFrame);

        lastFrame = System.currentTimeMillis();

        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_LINE_BIT
                | GL_CURRENT_BIT | GL_LIGHTING_BIT | GL_HINT_BIT);
        glPushMatrix();
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glShadeModel(GL_SMOOTH);
        final double pt = ((IMixinMinecraft) mc).getTimer().renderPartialTicks;
        final double x = interpolate(entity.lastTickPosX, entity.posX, pt) - mc.getRenderManager().viewerPosX;
        final double baseY = interpolate(entity.lastTickPosY, entity.posY, pt) - mc.getRenderManager().viewerPosY;
        final double z = interpolate(entity.lastTickPosZ, entity.posZ, pt) - mc.getRenderManager().viewerPosZ;
        final double bob = baseY + Math.sin(ticks) + 1;

        beginGlow();
        switch (style) {
            case CROWN:
                drawCrown(entity, x, baseY, z, rad, entity.height, colored, alpha);
                break;
            case ICE_LANCE:
                drawIceLance(entity, colored, alpha);
                break;
            case HEALTH_ARC:
                drawHealthArc(entity, x, baseY, z, rad, entity.height, colored, alpha);
                break;
            case CLASSIC:
            default:
                drawClassic(x, bob, z, rad, colored, alpha);
                break;
        }
        endGlow();

        glPopMatrix();
        glPopAttrib();
    }

    // The ring styles' geometry carries a profile in its texcoords, which ringglow.fsh turns into soft edges, glow and
    // travelling light. If the shader fails to build, the same geometry is drawn flat.
    private static ShaderUtil ringShader;
    private static int ringShaderState; // 0 untried, 1 ready, 2 failed

    private static void beginGlow() {
        if (ringShaderState == 0) {
            try {
                ringShader = new ShaderUtil("ringglow", "shaders/ringglow.vsh");
                ringShaderState = 1;
            } catch (Throwable t) {
                ringShaderState = 2;
            }
        }
        if (ringShaderState == 1) {
            ringShader.init();
            ringShader.setUniformf("uTime", (float) ticks);
        }
    }

    private static void endGlow() {
        if (ringShaderState == 1)
            ringShader.unload();
    }

    // Picks the profile for the geometry that follows. Call it outside glBegin/glEnd, as uniforms can't change inside.
    private static void glowMode(float mode) {
        if (ringShaderState == 1)
            ringShader.setUniformf("uMode", mode);
    }

    private static void drawClassic(double x, double y, double z, double rad, int colored, float alpha) {
        // curtain from the ring down to the ground, bright at the ring and fading out below it
        glowMode(1);
        glBegin(GL_TRIANGLE_STRIP);

        for (int seg = 0; seg <= 64; seg++) {
            final double i = seg * (Math.PI * 2) / 64.0;
            final double vecX = x + rad * Math.cos(i);
            final double vecZ = z + rad * Math.sin(i);

            glTexCoord2f(seg / 64f, 0f);
            color2(colored, 0);

            glVertex3d(vecX, y - Math.sin(ticks + 1) / 2.7f, vecZ);

            glTexCoord2f(seg / 64f, 1f);
            color2(colored, .52f * alpha);

            glVertex3d(vecX, y, vecZ);
        }

        glEnd();

        band(x, y, z, rad, 0.05, colored, alpha, 0, PI2, 64);
        band(x, y, z, rad, 0.16, colored, alpha * 0.35f, 0, PI2, 64);
    }

    // A flat band between radius rad-half and rad+half, from angle `from` to `to` (radians). Both edges carry the full
    // colour and the shader gives the band its soft edges and glow, so `half` is the band's visible thickness.
    private static void band(double x, double y, double z, double rad, double half, int colored, float alpha,
                             double from, double to, int steps) {
        glowMode(0);
        glBegin(GL_TRIANGLE_STRIP);
        for (int i = 0; i <= steps; i++) {
            double a = from + (to - from) * i / steps;
            double c = Math.cos(a), s = Math.sin(a);
            float u = (float) (i / (double) steps);
            glTexCoord2f(u, 0f);
            color2(colored, alpha);
            glVertex3d(x + (rad - half) * c, y, z + (rad - half) * s);
            glTexCoord2f(u, 1f);
            color2(colored, alpha);
            glVertex3d(x + (rad + half) * c, y, z + (rad + half) * s);
        }
        glEnd();
    }

    // A vertical cylinder wall from y0 up to y1, bright at the top and fading toward the bottom
    private static void wall(double x, double y0, double z, double rad, double y1, int colored, float alpha, int steps) {
        glowMode(1);
        glBegin(GL_TRIANGLE_STRIP);
        for (int i = 0; i <= steps; i++) {
            double a = i * Math.PI * 2 / steps;
            double c = Math.cos(a), s = Math.sin(a);
            float u = (float) (i / (double) steps);
            glTexCoord2f(u, 0f);
            color2(colored, alpha);
            glVertex3d(x + rad * c, y0, z + rad * s);
            glTexCoord2f(u, 1f);
            color2(colored, alpha);
            glVertex3d(x + rad * c, y1, z + rad * s);
        }
        glEnd();
    }

    // One fin standing on the rim at angle a, with its width running along the direction fin
    private static void spike(double x, double floor, double z, double dist, double a, double fin, double half, double h,
                              int colored, float alpha) {
        double bx = x + dist * Math.cos(a), bz = z + dist * Math.sin(a);
        double tx = -Math.sin(fin) * half, tz = Math.cos(fin) * half;
        glowMode(2);
        glBegin(GL_TRIANGLES);
        glTexCoord2f(0f, 0f);
        color2(colored, alpha * 0.8f);
        glVertex3d(bx + tx, floor, bz + tz);
        glTexCoord2f(1f, 0f);
        color2(colored, alpha * 0.8f);
        glVertex3d(bx - tx, floor, bz - tz);
        glTexCoord2f(0.5f, 1f);
        color2(colored, alpha);
        glVertex3d(bx, floor + h, bz);
        glEnd();
    }

    // Eight crystals in a ring above the head, each a wide dim fin behind a bright core fin, in both crossed directions.
    // A hit lengthens them.
    private static void drawCrown(Entity entity, double x, double y, double z, double rad, double height, int colored, float alpha) {
        long now = System.currentTimeMillis();
        if (consumeHit(entity))
            lastHitMs = now;
        float flash = hitFlash(now);
        double floor = y + height + 0.25;
        double r = rad * 0.9;
        double h = 0.32 + 0.14 * flash;
        for (int i = 0; i < 8; i++) {
            double a = ticks * 0.4 + i * Math.PI * 2 / 8;
            spike(x, floor, z, r, a, a, 0.2, h, colored, alpha * 0.35f);
            spike(x, floor, z, r, a, a + Math.PI / 2, 0.2, h, colored, alpha * 0.35f);
            spike(x, floor, z, r, a, a, 0.1, h, colored, alpha);
            spike(x, floor, z, r, a, a + Math.PI / 2, 0.1, h, colored, alpha);
        }
        band(x, floor, z, r, 0.12, colored, alpha * 0.6f, 0, PI2, 64);
    }

    // A point on an entity's body at the given fraction of its height, relative to the viewer
    private static double[] bodyPoint(Entity e, double yFrac) {
        double pt = ((IMixinMinecraft) mc).getTimer().renderPartialTicks;
        double x = interpolate(e.lastTickPosX, e.posX, pt) - mc.getRenderManager().viewerPosX;
        double y = interpolate(e.lastTickPosY, e.posY, pt) - mc.getRenderManager().viewerPosY + e.height * yFrac;
        double z = interpolate(e.lastTickPosZ, e.posZ, pt) - mc.getRenderManager().viewerPosZ;
        return new double[]{x, y, z};
    }

    private static double[] lerp3(double[] a, double[] b, double t) {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    // True once for each new hit on this entity. hurtTime jumps back up to its maximum on a hit, so a rise since the
    // last frame means a new one.
    private static int hurtTrackedId = -1;
    private static int lastHurtTime;
    private static long lastHitMs;

    private static boolean consumeHit(Entity entity) {
        if (entity.getEntityId() != hurtTrackedId) {
            hurtTrackedId = entity.getEntityId();
            lastHurtTime = 0;
        }
        boolean hit = entity.hurtTime > lastHurtTime;
        lastHurtTime = entity.hurtTime;
        return hit;
    }

    // 1 right after a hit, falling to 0 over 400ms
    private static float hitFlash(long now) {
        long since = now - lastHitMs;
        return since < 400 ? 1f - since / 400f : 0f;
    }

    // Ice lances fired from the player's chest into the target. Each lance is a tail that fades into a white-hot tip.
    // A hit bursts shards out of the impact point.
    private static void drawIceLance(Entity entity, int colored, float alpha) {
        long now = System.currentTimeMillis();
        if (consumeHit(entity))
            lastHitMs = now;
        double[] from = bodyPoint(mc.thePlayer, 0.6);
        double[] to = bodyPoint(entity, 0.5);
        glowMode(2);
        glBegin(GL_LINES);
        color2(colored, alpha * 0.15f);
        glVertex3d(from[0], from[1], from[2]);
        color2(colored, alpha * 0.15f);
        glVertex3d(to[0], to[1], to[2]);
        glEnd();
        int lances = 4;
        for (int k = 0; k < lances; k++) {
            double t = (ticks * 0.5 + k / (double) lances) % 1;
            lanceGlowed(lerp3(from, to, Math.max(0, t - 0.15)), lerp3(from, to, t), colored, alpha);
        }
        float flash = hitFlash(now);
        if (flash > 0) {
            double len = 0.1 + 0.6 * (1 - flash);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI * 2 / 8;
                double[] tip = {to[0] + Math.cos(a) * len, to[1] + Math.sin(a * 1.7) * len * 0.5, to[2] + Math.sin(a) * len};
                lanceGlowed(to, tip, colored, alpha * flash);
            }
        }
    }

    // A lance with a wide dim fin behind its core
    private static void lanceGlowed(double[] a, double[] b, int colored, float alpha) {
        lance(a, b, colored, alpha * 0.35f, 0.28);
        lance(a, b, colored, alpha, 0.12);
    }

    // One lance from a (tail) to b (tip), drawn as two fins crossed around its axis
    private static void lance(double[] a, double[] b, int colored, float alpha, double width) {
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6)
            return;
        dx /= len;
        dy /= len;
        dz /= len;
        // a horizontal side vector, falling back to x when the lance points straight up or down
        double px = -dz, pz = dx, pl = Math.sqrt(px * px + pz * pz);
        if (pl < 1e-6) {
            px = 1;
            pz = 0;
            pl = 1;
        }
        double[] p = {px / pl * width, 0, pz / pl * width};
        double[] q = {dy * p[2], dz * p[0] - dx * p[2], -dy * p[0]};
        glowMode(2);
        glBegin(GL_TRIANGLES);
        lanceFin(a, p, b, colored, alpha);
        lanceFin(a, q, b, colored, alpha);
        glEnd();
    }

    private static void lanceFin(double[] a, double[] s, double[] b, int colored, float alpha) {
        glTexCoord2f(0f, 0f);
        color2(colored, 0);
        glVertex3d(a[0] + s[0], a[1] + s[1], a[2] + s[2]);
        glTexCoord2f(1f, 0f);
        color2(colored, 0);
        glVertex3d(a[0] - s[0], a[1] - s[1], a[2] - s[2]);
        glTexCoord2f(0.5f, 1f);
        color2(colored, alpha);
        glVertex3d(b[0], b[1], b[2]);
    }

    // The part of the ring still showing is the health fraction, coloured from red when low to green when full.
    // A column at the end of the arc rises to the same fraction of the body's height.
    private static void drawHealthArc(Entity entity, double x, double y, double z, double rad, double height, int colored, float alpha) {
        double floor = y + 0.02;
        float frac = 1f;
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            frac = MathHelper.clamp_float(living.getHealth() / living.getMaxHealth(), 0f, 1f);
        }
        band(x, floor, z, rad, 0.12, colored, alpha * 0.18f, 0, PI2, 64);
        if (frac <= 0f)
            return;
        int hp = (int) (255 * (1 - frac)) << 16 | (int) (255 * frac) << 8;
        double end = frac * PI2;
        band(x, floor, z, rad, 0.12, hp, alpha, 0, end, 64);
        band(x, floor, z, rad, 0.3, hp, alpha * 0.35f, 0, end, 64);
        double ex = x + rad * Math.cos(end), ez = z + rad * Math.sin(end);
        wall(ex, floor, ez, 0.12, floor + height * frac, hp, alpha * 0.9f, 12);
    }

    public static final float PI2 = roundToFloat((Math.PI * 2D));

    public static float roundToFloat(double d) {
        return (float) ((double) Math.round(d * 1.0E8D) / 1.0E8D);
    }
    public static Double interpolate(double oldValue, double newValue, double interpolationValue){
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }
}
