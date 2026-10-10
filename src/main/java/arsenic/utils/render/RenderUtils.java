package arsenic.utils.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import arsenic.injection.accessor.IMixinMinecraft;
import arsenic.injection.accessor.IMixinRenderManager;
import arsenic.utils.java.UtilityClass;
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
        /** A bobbing ring with a filled column down to the ground and a glowing band. */
        CLASSIC,
        /** The same bobbing band with no fill, brighter and wider. */
        OUTLINE,
        /** The classic ring, breathing in and out. */
        PULSE,
        /** A fixed band at head height over a faint disc. */
        HALO,
        /** Glowing dashes around the ground that turn slowly. */
        SPIN,
        /** A rune circle on the ground with a wall rising from its rim. Lightning is called down on every hit. */
        MAGIC_CIRCLE,
        /** A translucent column from the feet to the head with a ring climbing its length. */
        PILLAR,
        /** Two strands of motes spiralling up the body from the feet to the head. */
        HELIX,
        /** Rings that rise from the feet to the head and fade out, one after another. */
        RISING,
        /** A tilted ring circling the body at chest height with a mote riding it. */
        ORBIT,
        /** Ice spikes standing on the rim, rising and sinking as they turn. */
        ICE_WARD,
        /** Motes spiralling inward from the rim and up into a column over the centre. */
        GRAVITY_WELL,
        /** A ring that beats twice per cycle, and beats faster for a moment after a hit. */
        HEARTBEAT,
        /** A ring whose arc shows health left, coloured from red to green, with a column marking the arc's end. */
        HEALTH_ARC,
        /** Dark tendrils curling up from the ground around the body. */
        SHADOW_TENDRILS
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

        switch (style) {
            case OUTLINE:
                band(x, bob, z, rad, 0.06, colored, alpha, 0, PI2, 64);
                band(x, bob, z, rad, 0.2, colored, alpha * 0.35f, 0, PI2, 64);
                break;
            case PULSE:
                drawClassic(x, bob, z, rad * (1 + 0.2 * Math.sin(ticks * 3)), colored, alpha);
                break;
            case HALO:
                drawHalo(x, baseY + entity.height + 0.1, z, rad * 0.75, colored, alpha);
                break;
            case SPIN:
                drawSpin(x, baseY + 0.02, z, rad, colored, alpha);
                break;
            case MAGIC_CIRCLE:
                drawMagicCircle(entity, x, baseY, z, rad, colored, alpha);
                break;
            case PILLAR:
                drawPillar(x, baseY, z, rad, entity.height, colored, alpha);
                break;
            case HELIX:
                drawHelix(x, baseY, z, rad, entity.height, colored, alpha);
                break;
            case RISING:
                drawRising(x, baseY, z, rad, entity.height, colored, alpha);
                break;
            case ORBIT:
                drawOrbit(x, baseY, z, rad, entity.height, colored, alpha);
                break;
            case ICE_WARD:
                drawIceWard(x, baseY, z, rad, entity.height, colored, alpha);
                break;
            case GRAVITY_WELL:
                drawGravityWell(x, baseY, z, rad, entity.height, colored, alpha);
                break;
            case HEARTBEAT:
                drawHeartbeat(entity, x, baseY, z, rad, entity.height, colored, alpha);
                break;
            case HEALTH_ARC:
                drawHealthArc(entity, x, baseY, z, rad, entity.height, colored, alpha);
                break;
            case SHADOW_TENDRILS:
                drawShadowTendrils(x, baseY, z, rad, entity.height, colored, alpha);
                break;
            case CLASSIC:
            default:
                drawClassic(x, bob, z, rad, colored, alpha);
                break;
        }

        glPopMatrix();
        glPopAttrib();
    }

    private static void drawClassic(double x, double y, double z, double rad, int colored, float alpha) {
        glBegin(GL_TRIANGLE_STRIP);

        for (int seg = 0; seg <= 64; seg++) {
            final double i = seg * (Math.PI * 2) / 64.0;
            final double vecX = x + rad * Math.cos(i);
            final double vecZ = z + rad * Math.sin(i);

            color2(colored, 0);

            glVertex3d(vecX, y - Math.sin(ticks + 1) / 2.7f, vecZ);

            color2(colored, .52f * alpha);

            glVertex3d(vecX, y, vecZ);
        }

        glEnd();

        band(x, y, z, rad, 0.05, colored, alpha, 0, PI2, 64);
        band(x, y, z, rad, 0.16, colored, alpha * 0.35f, 0, PI2, 64);
    }

    private static void drawHalo(double x, double y, double z, double rad, int colored, float alpha) {
        // faint disc, fading out from the centre
        glBegin(GL_TRIANGLE_FAN);
        color2(colored, .25f * alpha);
        glVertex3d(x, y, z);
        color2(colored, 0);
        for (int seg = 0; seg <= 48; seg++) {
            double a = seg * Math.PI * 2 / 48;
            glVertex3d(x + rad * Math.cos(a), y, z + rad * Math.sin(a));
        }
        glEnd();

        band(x, y, z, rad, 0.06, colored, alpha, 0, PI2, 64);
        band(x, y, z, rad, 0.18, colored, alpha * 0.4f, 0, PI2, 64);
    }

    // A flat band between radius rad-half and rad+half, from angle `from` to `to` (radians). The core is opaque and
    // the outer edge fades, so the same call gives a glow when drawn wide and faint.
    private static void band(double x, double y, double z, double rad, double half, int colored, float alpha,
                             double from, double to, int steps) {
        glBegin(GL_TRIANGLE_STRIP);
        for (int i = 0; i <= steps; i++) {
            double a = from + (to - from) * i / steps;
            double c = Math.cos(a), s = Math.sin(a);
            color2(colored, alpha);
            glVertex3d(x + (rad - half) * c, y, z + (rad - half) * s);
            color2(colored, 0);
            glVertex3d(x + (rad + half) * c, y, z + (rad + half) * s);
        }
        glEnd();
    }

    // Twelve glowing dashes that turn slowly around the ground
    private static void drawSpin(double x, double y, double z, double rad, int colored, float alpha) {
        int dashes = 12;
        double spin = ticks * 1.5;
        for (int d = 0; d < dashes; d++) {
            double start = spin + d * Math.PI * 2 / dashes;
            double end = start + Math.PI * 2 / dashes * 0.55;
            band(x, y, z, rad, 0.06, colored, alpha, start, end, 6);
            band(x, y, z, rad, 0.18, colored, alpha * 0.35f, start, end, 6);
        }
    }

    private static final int BOLT_SEGMENTS = 12;
    private static final long BOLT_MS = 380;
    private static final int MAX_BOLTS = 8;
    private static final List<LightningBolt> bolts = new ArrayList<>();
    private static final Random boltRandom = new Random();
    private static int hurtTrackedId = -1;
    private static int lastHurtTime;
    private static long lastHitMs;

    private static final class LightningBolt {
        final long born = System.currentTimeMillis();
        // sideways wobble per joint, zero at the ends so the bolt starts above and lands on the target's centre
        final float[] jitterX = new float[BOLT_SEGMENTS + 1];
        final float[] jitterZ = new float[BOLT_SEGMENTS + 1];

        LightningBolt() {
            for (int i = 1; i < BOLT_SEGMENTS; i++) {
                jitterX[i] = (boltRandom.nextFloat() - 0.5f) * 0.4f;
                jitterZ[i] = (boltRandom.nextFloat() - 0.5f) * 0.4f;
            }
        }
    }

    // True once for each new hit on this entity. hurtTime jumps back up to its maximum on a hit, so a rise since the
    // last frame means a new one.
    private static boolean consumeHit(Entity entity) {
        if (entity.getEntityId() != hurtTrackedId) {
            hurtTrackedId = entity.getEntityId();
            lastHurtTime = 0;
        }
        boolean hit = entity.hurtTime > lastHurtTime;
        lastHurtTime = entity.hurtTime;
        return hit;
    }

    // A rune circle on the ground, its rim walled, and a lightning bolt from the sky each time the target takes a hit
    private static void drawMagicCircle(Entity entity, double x, double baseY, double z, double rad, int colored, float alpha) {
        if (consumeHit(entity)) {
            bolts.add(new LightningBolt());
            if (bolts.size() > MAX_BOLTS)
                bolts.remove(0);
        }

        double floor = baseY + 0.02;
        float pulse = 0.65f + 0.35f * (float) Math.sin(ticks * 4);
        disc(x, floor, z, rad, colored, alpha * 0.2f * pulse);
        band(x, floor, z, rad, 0.05, colored, alpha * pulse, 0, PI2, 64);
        band(x, floor, z, rad * 0.72, 0.03, colored, alpha * pulse, 0, PI2, 64);
        wall(x, floor, z, rad, floor + entity.height * 0.6, colored, alpha * 0.25f, 48);

        // a pentagram turning one way, and rune ticks turning the other
        double star = -ticks * 0.6 - Math.PI / 2;
        double runes = ticks * 0.25;
        glLineWidth(2f);
        color2(colored, alpha * pulse);
        glBegin(GL_LINES);
        for (int i = 0; i < 5; i++) {
            double a = star + i * Math.PI * 2 / 5;
            double b = star + ((i + 2) % 5) * Math.PI * 2 / 5;
            glVertex3d(x + rad * 0.72 * Math.cos(a), floor, z + rad * 0.72 * Math.sin(a));
            glVertex3d(x + rad * 0.72 * Math.cos(b), floor, z + rad * 0.72 * Math.sin(b));
        }
        for (int i = 0; i < 16; i++) {
            double a = runes + i * Math.PI * 2 / 16;
            glVertex3d(x + rad * 0.88 * Math.cos(a), floor, z + rad * 0.88 * Math.sin(a));
            glVertex3d(x + rad * 0.98 * Math.cos(a), floor, z + rad * 0.98 * Math.sin(a));
        }
        glEnd();
        glLineWidth(1f);

        double top = baseY + entity.height + 2.5;
        double bodyY = baseY + entity.height * 0.5;
        long now = System.currentTimeMillis();
        for (Iterator<LightningBolt> it = bolts.iterator(); it.hasNext(); ) {
            LightningBolt bolt = it.next();
            float age = (now - bolt.born) / (float) BOLT_MS;
            if (age >= 1f) {
                it.remove();
                continue;
            }
            float fade = 1f - age;
            drawBolt(bolt, x, top, z, bodyY, colored, alpha * fade);
            // the shock spreading out from the circle as the bolt lands
            band(x, floor, z, rad * (0.4 + age), 0.06, colored, alpha * fade, 0, PI2, 64);
        }
    }

    // A jagged path from the sky to the target, a wide faint glow under a thin white core
    private static void drawBolt(LightningBolt bolt, double x, double top, double z, double bottom, int colored, float alpha) {
        for (int pass = 0; pass < 2; pass++) {
            glLineWidth(pass == 0 ? 6f : 2f);
            glBegin(GL_LINE_STRIP);
            for (int i = 0; i <= BOLT_SEGMENTS; i++) {
                double t = i / (double) BOLT_SEGMENTS;
                double y = top + (bottom - top) * t;
                if (pass == 0)
                    color2(colored, alpha * 0.4f);
                else
                    glColor4f(1f, 1f, 1f, alpha);
                glVertex3d(x + bolt.jitterX[i], y, z + bolt.jitterZ[i]);
            }
            glEnd();
        }
        glLineWidth(1f);
    }

    // A translucent column with a ring that climbs it, a band at each end
    private static void drawPillar(double x, double y, double z, double rad, double height, int colored, float alpha) {
        double top = y + height;
        wall(x, y, z, rad * 0.8, top, colored, alpha * 0.45f, 48);
        band(x, y + 0.02, z, rad, 0.05, colored, alpha, 0, PI2, 64);
        band(x, top, z, rad * 0.8, 0.05, colored, alpha * 0.8f, 0, PI2, 64);
        double climb = y + ((ticks * 0.25) % 1) * height;
        band(x, climb, z, rad * 0.8, 0.05, colored, alpha, 0, PI2, 64);
        band(x, climb, z, rad * 0.8, 0.16, colored, alpha * 0.35f, 0, PI2, 64);
    }

    // Two strands winding up the body, with motes climbing both
    private static void drawHelix(double x, double y, double z, double rad, double height, int colored, float alpha) {
        double r = rad * 0.55;
        int samples = 40;
        glLineWidth(2f);
        for (int strand = 0; strand < 2; strand++) {
            double phase = strand * Math.PI;
            glBegin(GL_LINE_STRIP);
            for (int i = 0; i <= samples; i++) {
                double t = i / (double) samples;
                double a = phase + t * Math.PI * 4;
                color2(colored, alpha * 0.8f);
                glVertex3d(x + r * Math.cos(a), y + t * height, z + r * Math.sin(a));
            }
            glEnd();
        }
        glLineWidth(1f);
        for (int strand = 0; strand < 2; strand++) {
            for (int k = 0; k < 4; k++) {
                double t = (ticks * 0.12 + k / 4.0) % 1;
                double a = strand * Math.PI + t * Math.PI * 4;
                mote(x + r * Math.cos(a), y + t * height, z + r * Math.sin(a), 0.08, colored, alpha);
            }
        }
    }

    // Three rings that start at the feet, widen and rise to the head, fading as they go
    private static void drawRising(double x, double y, double z, double rad, double height, int colored, float alpha) {
        for (int k = 0; k < 3; k++) {
            double u = (ticks * 0.2 + k / 3.0) % 1;
            double r = rad * (0.6 + 0.5 * u);
            band(x, y + u * height, z, r, 0.05, colored, alpha * (float) (1 - u), 0, PI2, 64);
        }
        band(x, y + 0.02, z, rad * 0.6, 0.04, colored, alpha * 0.6f, 0, PI2, 64);
    }

    // A ring tilted across the body at chest height, turning around it with a mote riding its edge
    private static void drawOrbit(double x, double y, double z, double rad, double height, int colored, float alpha) {
        double cy = y + height * 0.55;
        double r = rad * 0.85;
        double tilt = 0.45;
        double spin = ticks * 0.8;
        for (int pass = 0; pass < 2; pass++) {
            glLineWidth(pass == 0 ? 4f : 1.5f);
            color2(colored, alpha * (pass == 0 ? 0.35f : 1f));
            glBegin(GL_LINE_LOOP);
            for (int i = 0; i < 64; i++) {
                double[] p = orbitPoint(x, cy, z, r, i * Math.PI * 2 / 64, tilt, spin);
                glVertex3d(p[0], p[1], p[2]);
            }
            glEnd();
        }
        glLineWidth(1f);
        double[] lead = orbitPoint(x, cy, z, r, ticks * 1.5, tilt, spin);
        mote(lead[0], lead[1], lead[2], 0.1, colored, alpha);
    }

    // A point on a circle of radius r around (x, cy, z), tilted about the x axis and then turned about the y axis
    private static double[] orbitPoint(double x, double cy, double z, double r, double a, double tilt, double spin) {
        double px = r * Math.cos(a);
        double pz = r * Math.sin(a);
        double py = pz * Math.sin(tilt);
        pz *= Math.cos(tilt);
        return new double[]{
                x + px * Math.cos(spin) - pz * Math.sin(spin),
                cy + py,
                z + px * Math.sin(spin) + pz * Math.cos(spin)
        };
    }

    // A flat filled disc fading out from its centre
    private static void disc(double x, double y, double z, double rad, int colored, float alpha) {
        glBegin(GL_TRIANGLE_FAN);
        color2(colored, alpha);
        glVertex3d(x, y, z);
        color2(colored, 0);
        for (int seg = 0; seg <= 48; seg++) {
            double a = seg * Math.PI * 2 / 48;
            glVertex3d(x + rad * Math.cos(a), y, z + rad * Math.sin(a));
        }
        glEnd();
    }

    // A vertical cylinder wall from y0 up to y1, solid at the bottom and fading out at the top
    private static void wall(double x, double y0, double z, double rad, double y1, int colored, float alpha, int steps) {
        glBegin(GL_TRIANGLE_STRIP);
        for (int i = 0; i <= steps; i++) {
            double a = i * Math.PI * 2 / steps;
            double c = Math.cos(a), s = Math.sin(a);
            color2(colored, alpha);
            glVertex3d(x + rad * c, y0, z + rad * s);
            color2(colored, 0);
            glVertex3d(x + rad * c, y1, z + rad * s);
        }
        glEnd();
    }

    // A small glowing diamond standing up, turned to face the viewer. Coordinates are relative to the viewer.
    private static void mote(double x, double y, double z, double size, int colored, float alpha) {
        double len = Math.hypot(x, z);
        double rx = len > 1e-6 ? -z / len * size : size;
        double rz = len > 1e-6 ? x / len * size : 0;
        glBegin(GL_TRIANGLE_FAN);
        color2(colored, alpha);
        glVertex3d(x, y, z);
        color2(colored, 0);
        glVertex3d(x + rx, y, z + rz);
        glVertex3d(x, y + size, z);
        glVertex3d(x - rx, y, z - rz);
        glVertex3d(x, y - size, z);
        glVertex3d(x + rx, y, z + rz);
        glEnd();
    }


    // Six ice spikes on the rim, each a pair of crossed fins that swell and sink out of step
    private static void drawIceWard(double x, double y, double z, double rad, double height, int colored, float alpha) {
        double floor = y + 0.02;
        band(x, floor, z, rad, 0.05, colored, alpha * 0.8f, 0, PI2, 64);
        for (int i = 0; i < 6; i++) {
            double a = ticks * 0.3 + i * Math.PI / 3;
            double h = height * (0.6 + 0.4 * Math.sin(ticks * 1.5 + i * 1.3));
            spike(x, floor, z, rad * 0.9, a, a, 0.08, h, colored, alpha);
            spike(x, floor, z, rad * 0.9, a, a + Math.PI / 2, 0.08, h, colored, alpha);
        }
    }

    // One fin standing on the rim at angle a, with its width running along the direction fin
    private static void spike(double x, double floor, double z, double dist, double a, double fin, double half, double h,
                              int colored, float alpha) {
        double bx = x + dist * Math.cos(a), bz = z + dist * Math.sin(a);
        double tx = -Math.sin(fin) * half, tz = Math.cos(fin) * half;
        glBegin(GL_TRIANGLES);
        color2(colored, alpha * 0.6f);
        glVertex3d(bx + tx, floor, bz + tz);
        color2(colored, alpha * 0.6f);
        glVertex3d(bx - tx, floor, bz - tz);
        color2(colored, 0);
        glVertex3d(bx, floor + h, bz);
        glEnd();
    }

    // Motes travel in three arms from the rim toward the centre, rising as they go, with a thin column over the middle
    private static void drawGravityWell(double x, double y, double z, double rad, double height, int colored, float alpha) {
        double floor = y + 0.02;
        disc(x, floor, z, rad, colored, alpha * 0.15f);
        band(x, floor, z, rad, 0.04, colored, alpha * 0.5f, 0, PI2, 64);
        wall(x, floor, z, rad * 0.08, floor + height * 0.7, colored, alpha * 0.3f, 12);
        int arms = 3, steps = 14;
        for (int arm = 0; arm < arms; arm++) {
            for (int k = 0; k < steps; k++) {
                double t = (ticks * 0.15 + k / (double) steps) % 1;
                double r = rad * (1 - t);
                double a = arm * Math.PI * 2 / arms + t * Math.PI * 3;
                mote(x + r * Math.cos(a), floor + t * height * 0.7, z + r * Math.sin(a), 0.07 * (1 - t * 0.5),
                        colored, alpha * (float) Math.sin(t * Math.PI));
            }
        }
    }

    // A ring that swells on a double beat. The beats come faster for a moment after each hit.
    private static void drawHeartbeat(Entity entity, double x, double y, double z, double rad, double height, int colored, float alpha) {
        double floor = y + 0.02;
        long now = System.currentTimeMillis();
        if (consumeHit(entity))
            lastHitMs = now;
        long period = now - lastHitMs < 1500 ? 380 : 900;
        double p = (now % period) / (double) period;
        double e = bump(p, 0.05, 0.05) + 0.6 * bump(p, 0.22, 0.06);
        band(x, floor, z, rad * (0.85 + 0.25 * e), 0.04 + 0.04 * e, colored, alpha * (0.45f + 0.55f * (float) e), 0, PI2, 64);
        band(x, floor, z, rad * 0.85, 0.03, colored, alpha * 0.4f, 0, PI2, 64);
        wall(x, floor, z, rad * 0.95, floor + height * (0.25 + 0.5 * e), colored, alpha * (0.1f + 0.3f * (float) e), 48);
    }

    // A triangle wave that peaks at centre and is zero beyond width from it
    private static double bump(double p, double centre, double width) {
        return Math.max(0, 1 - Math.abs(p - centre) / width);
    }

    // The part of the ring still showing is the health fraction, coloured from red when low to green when full
    private static void drawHealthArc(Entity entity, double x, double y, double z, double rad, double height, int colored, float alpha) {
        double floor = y + 0.02;
        float frac = 1f;
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            frac = MathHelper.clamp_float(living.getHealth() / living.getMaxHealth(), 0f, 1f);
        }
        band(x, floor, z, rad, 0.05, colored, alpha * 0.2f, 0, PI2, 64);
        if (frac <= 0f)
            return;
        int hp = (int) (255 * (1 - frac)) << 16 | (int) (255 * frac) << 8;
        double end = frac * PI2;
        band(x, floor, z, rad, 0.06, hp, alpha, 0, end, 64);
        band(x, floor, z, rad, 0.18, hp, alpha * 0.35f, 0, end, 64);
        double ex = x + rad * Math.cos(end), ez = z + rad * Math.sin(end);
        wall(ex, floor, ez, 0.05, floor + height * frac, hp, alpha * 0.8f, 8);
    }

    // Tendrils that rise from the ground and sway around the body, drawn in a darkened tint of the theme colour
    private static void drawShadowTendrils(double x, double y, double z, double rad, double height, int colored, float alpha) {
        double floor = y + 0.02;
        int dark = darken(colored, 0.25f);
        disc(x, floor, z, rad, dark, alpha * 0.5f);
        int tendrils = 6, samples = 16;
        glLineWidth(3f);
        for (int i = 0; i < tendrils; i++) {
            double base = i * Math.PI * 2 / tendrils + Math.sin(ticks * 0.5 + i) * 0.2;
            glBegin(GL_LINE_STRIP);
            for (int j = 0; j <= samples; j++) {
                double t = j / (double) samples;
                double a = base + Math.sin(t * 6 + ticks * 2 + i) * 0.4;
                double r = rad * (1 - 0.3 * t);
                color2(dark, alpha * 0.9f * (float) (1 - t));
                glVertex3d(x + r * Math.cos(a), floor + t * height * 1.1, z + r * Math.sin(a));
            }
            glEnd();
        }
        glLineWidth(1f);
    }

    private static int darken(int colored, float f) {
        return ((int) ((colored >> 16 & 255) * f) << 16) | ((int) ((colored >> 8 & 255) * f) << 8) | (int) ((colored & 255) * f);
    }

    public static final float PI2 = roundToFloat((Math.PI * 2D));

    public static float roundToFloat(double d) {
        return (float) ((double) Math.round(d * 1.0E8D) / 1.0E8D);
    }
    public static Double interpolate(double oldValue, double newValue, double interpolationValue){
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }
}
