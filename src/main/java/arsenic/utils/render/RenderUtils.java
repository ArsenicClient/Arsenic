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

import static net.minecraft.client.renderer.GlStateManager.color;
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
    public static int alpha(Color color, int newAlpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), newAlpha).getRGB();
    }
    public static void setAlphaLimit(float alphaLimit) {
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL_GREATER,  alphaLimit * 0.01f);
    }
    /**
     * True while the ClickGUI is being captured into the burn-transition FBO.
     * With plain (SRC_ALPHA, 1-SRC_ALPHA) blending the src factor also applies
     * to the alpha channel, so an empty FBO accumulates srcA^2 instead of srcA
     * - the capture reads too transparent and the burn composite lets the world
     * bleed through, then "snaps" opaque when the burn ends. While this flag is
     * set, GUI draws use separate alpha factors (ONE, 1-SRC_ALPHA) so FBO alpha
     * is true coverage and the composite reproduces on-screen opacity exactly.
     */
    public static boolean captureCoverage = false;

    /** Standard GUI transparency blend; coverage-correct during burn capture. */
    public static void applyGuiBlend() {
        if (captureCoverage) {
            // sync GlStateManager's cache, then force the real GL state with a
            // raw call - the cache is often stale here because of the raw
            // glBlendFunc calls sprinkled through the render helpers
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
            return Minecraft.getMinecraft().renderEngine.getDynamicTextureLocation("Arsenic", new DynamicTexture(bf));
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

        // Build a razor-thin BB on the correct face
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

    public static void drawCircle(Entity entity, float partialTicks, double rad, int colored, float alpha) {
        ticks += .004 * (System.currentTimeMillis() - lastFrame);

        lastFrame = System.currentTimeMillis();

        glPushMatrix();
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        GlStateManager.color(1, 1, 1, 1);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glShadeModel(GL_SMOOTH);
        GlStateManager.disableCull();
        final double x = interpolate(entity.lastTickPosX, entity.posX, ((IMixinMinecraft) mc).getTimer().renderPartialTicks) - mc.getRenderManager().viewerPosX;
        final double y = interpolate(entity.lastTickPosY, entity.posY, ((IMixinMinecraft) mc).getTimer().renderPartialTicks) - mc.getRenderManager().viewerPosY + Math.sin(ticks) + 1;
        final double z = interpolate(entity.lastTickPosZ, entity.posZ, ((IMixinMinecraft) mc).getTimer().renderPartialTicks) - mc.getRenderManager().viewerPosZ;

        glBegin(GL_TRIANGLE_STRIP);

        for (float i = 0; i < (Math.PI * 2); i += (Math.PI * 2) / 64.F) {

            final double vecX = x + rad * Math.cos(i);
            final double vecZ = z + rad * Math.sin(i);

            color2(colored, 0);

            glVertex3d(vecX, y - Math.sin(ticks + 1) / 2.7f, vecZ);

            color2(colored, .52f * alpha);


            glVertex3d(vecX, y, vecZ);
        }

        glEnd();


        glEnable(GL_LINE_SMOOTH);
        glHint(GL_LINE_SMOOTH_HINT, GL_NICEST);
        glLineWidth(1.5f);
        glBegin(GL_LINE_STRIP);
        GlStateManager.color(1, 1, 1, 1);
        color2(colored, .5f * alpha);
        for (int i = 0; i <= 180; i++) {
            glVertex3d(x - Math.sin(i * PI2 / 90) * rad, y, z + Math.cos(i * PI2 / 90) * rad);
        }
        glEnd();

        glShadeModel(GL_FLAT);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        GlStateManager.enableCull();
        glDisable(GL_LINE_SMOOTH);
        glEnable(GL_TEXTURE_2D);
        glPopMatrix();
        glColor4f(1f, 1f, 1f, 1f);
    }


    // ---------------------------------------------------------------
    //  Alternative target effects (KillAura "Effect" dropdown). All share the
    //  same GL state block as drawCircle and tint with the theme colour.
    // ---------------------------------------------------------------

    /** Interpolated render position of an entity, viewer-relative: {x, y, z}. */
    private static double[] entityRenderPos(Entity entity) {
        float pt = ((IMixinMinecraft) mc).getTimer().renderPartialTicks;
        return new double[]{
                interpolate(entity.lastTickPosX, entity.posX, pt) - mc.getRenderManager().viewerPosX,
                interpolate(entity.lastTickPosY, entity.posY, pt) - mc.getRenderManager().viewerPosY,
                interpolate(entity.lastTickPosZ, entity.posZ, pt) - mc.getRenderManager().viewerPosZ};
    }

    private static void beginWorldEffect() {
        glPushMatrix();
        glDisable(GL_TEXTURE_2D);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);
        glShadeModel(GL_SMOOTH);
        GlStateManager.disableCull();
        GlStateManager.color(1, 1, 1, 1);
        glEnable(GL_LINE_SMOOTH);
        glHint(GL_LINE_SMOOTH_HINT, GL_NICEST);
    }

    private static void endWorldEffect() {
        glShadeModel(GL_FLAT);
        glDepthMask(true);
        glEnable(GL_DEPTH_TEST);
        GlStateManager.enableCull();
        glDisable(GL_LINE_SMOOTH);
        glLineWidth(1f);
        glEnable(GL_TEXTURE_2D);
        glPopMatrix();
        glColor4f(1f, 1f, 1f, 1f);
    }

    /** Glowing orbs circling the target, each dragging a fading trail. */
    public static void drawTargetOrbit(Entity entity, float partialTicks, double rad, int color, float alpha) {
        beginWorldEffect();
        double[] p = entityRenderPos(entity);
        double x = p[0], z = p[2];
        double t = (System.currentTimeMillis() % 100000L) / 1000.0;
        float aN = alpha / 255f;
        int orbs = 3;
        int trail = 28;

        for (int o = 0; o < orbs; o++) {
            double phase = t * 3.0 + o * (PI2 / orbs);
            // fading trail sweeping back behind the orb; vertical bob follows
            // the same motion evaluated back in time so the trail lines up
            glLineWidth(5f);
            glBegin(GL_LINE_STRIP);
            for (int i = 0; i <= trail; i++) {
                double back = i * 0.045;
                double tt = t - back / 3.0;
                double ang = phase - back;
                double yo = p[1] + entity.height * 0.5 + Math.sin(tt * 2.0 + o * 2.1) * entity.height * 0.35;
                color2(color, (1f - i / (float) trail) * 0.8f * aN);
                glVertex3d(x + Math.cos(ang) * rad, yo, z + Math.sin(ang) * rad);
            }
            glEnd();

            // the orb itself: a soft round point with a faint halo behind it
            double yo = p[1] + entity.height * 0.5 + Math.sin(t * 2.0 + o * 2.1) * entity.height * 0.35;
            double ox = x + Math.cos(phase) * rad, oz = z + Math.sin(phase) * rad;
            glEnable(GL_POINT_SMOOTH);
            glPointSize(16f);
            glBegin(GL_POINTS);
            color2(color, 0.25f * aN);
            glVertex3d(ox, yo, oz);
            glEnd();
            glPointSize(8f);
            glBegin(GL_POINTS);
            color2(color, aN);
            glVertex3d(ox, yo, oz);
            glEnd();
            glDisable(GL_POINT_SMOOTH);
        }
        glPointSize(1f);
        endWorldEffect();
    }

    /** Shockwave rings expanding outward from the target's feet over a soft glow disc. */
    public static void drawTargetPulse(Entity entity, float partialTicks, double rad, int color, float alpha) {
        beginWorldEffect();
        double[] p = entityRenderPos(entity);
        double x = p[0], y = p[1] + 0.02, z = p[2];
        double t = (System.currentTimeMillis() % 100000L) / 1000.0;
        float aN = alpha / 255f;
        int rings = 3;
        double maxR = rad * 2.2;

        for (int k = 0; k < rings; k++) {
            double f = (t * 0.8 + k / (double) rings) % 1.0;    // 0..1 expansion
            double r = 0.15 + f * maxR;
            float fade = (float) ((1.0 - f) * (1.0 - f)) * 0.9f * aN;
            glLineWidth((float) (7.0 * (1.0 - f) + 2.5)); // thick at spawn, still visible at full reach
            glBegin(GL_LINE_LOOP);
            color2(color, fade);
            for (int i = 0; i < 72; i++) {
                double ang = i * PI2 / 72;
                glVertex3d(x + Math.cos(ang) * r, y, z + Math.sin(ang) * r);
            }
            glEnd();
        }

        // soft filled glow disc at the centre, fading to nothing at the edge
        glBegin(GL_TRIANGLE_FAN);
        color2(color, 0.35f * aN);
        glVertex3d(x, y, z);
        color2(color, 0f);
        for (int i = 0; i <= 48; i++) {
            double ang = i * PI2 / 48;
            glVertex3d(x + Math.cos(ang) * rad, y, z + Math.sin(ang) * rad);
        }
        glEnd();
        endWorldEffect();
    }

    public static final float PI2 = roundToFloat((Math.PI * 2D));

    public static float roundToFloat(double d) {
        return (float) ((double) Math.round(d * 1.0E8D) / 1.0E8D);
    }
    public static Double interpolate(double oldValue, double newValue, double interpolationValue){
        return (oldValue + (newValue - oldValue) * interpolationValue);
    }
}
