package arsenic.utils.render;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

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
        SPIN
    }

    public static void drawCircle(Entity entity, float partialTicks, double rad, int colored, float alpha) {
        drawRing(entity, partialTicks, rad, colored, alpha, RingStyle.CLASSIC);
    }

    public static void drawRing(Entity entity, float partialTicks, double rad, int colored, float alpha, RingStyle style) {
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


    public static final float PI2 = roundToFloat((Math.PI * 2D));

    public static float roundToFloat(double d) {
        return (float) ((double) Math.round(d * 1.0E8D) / 1.0E8D);
    }
    public static Double interpolate(double oldValue, double newValue, double interpolationValue){
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }
}
