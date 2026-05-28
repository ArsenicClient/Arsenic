package arsenic.module.impl.world;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMove;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;

import static arsenic.utils.rotations.RotationUtils.getRotations;


@ModuleInfo(name = "KvScaffold", category = ModuleCategory.PLAYER, dev = true)
public class ScaffoldTest extends Module {
    public final BooleanProperty allowTower = new BooleanProperty("Allow Tower", false);
    private BlockPos lastOverBlock;
    private float[] rotations;
    private Vec3 lastTargetPos;

    @EventLink
    public final Listener<EventMove> eventMoveListener = event -> {
        BlockPos blockPos = PlayerUtils.getBlockUnderPlayer();
        if(!mc.theWorld.isAirBlock(blockPos)) {
            lastOverBlock = blockPos;
            return;
        }

        ArrayList<BlockPos> bpArray = new ArrayList<>();
        for(int x = -1; x <= 2; x ++) {
            for(int z = -1; z <= 2; z ++) {
                blockPos = PlayerUtils.getBlockUnderPlayer().add(x, 0, z);
                if(!mc.theWorld.isAirBlock(blockPos))
                    bpArray.add(blockPos);
            }
        }

        if(bpArray.isEmpty())
            return;

        lastOverBlock = bpArray.stream().min(Comparator.comparingDouble((in) -> RotationUtils.getDistanceToBlockPos(in))).get();
    };

    @EventLink
    public final Listener<EventRenderWorldLast> eventRenderWorldLastListener = event -> {
        if(lastOverBlock == null)
            return;
        re(lastOverBlock, Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor(), true);
        BlockPos blockPos = PlayerUtils.getBlockUnderPlayer();
        if(blockPos.equals(lastOverBlock))
            return;
        re(blockPos, Arsenic.getArsenic().getThemeManager().getCurrentTheme().getDarkerColor(), true);
        if(lastTargetPos != null)
            drawDot(lastTargetPos, 0xFFFF0000, 10.0f); // Red dot, size 10
    };

    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        event.setSpeed(20f);

        // Tower mode check
        if(allowTower.getValue() && mc.thePlayer.motionX == 0 && mc.thePlayer.motionZ == 0) {
            event.setPitch(90);
            return;
        }

        // Check block under player
        BlockPos blockPos = PlayerUtils.getBlockUnderPlayer();

        if(!mc.theWorld.isAirBlock(blockPos)) {
            event.setYaw(rotations[0]);
            event.setPitch(rotations[1]);
            return;
        }

        // Check if lastOverBlock exists
        if(lastOverBlock == null) {
            return;
        }

        // Find best facing direction
        EnumFacing face = java.util.Arrays.stream(EnumFacing.values())
                .filter(enumFacing -> enumFacing != EnumFacing.UP && enumFacing != EnumFacing.DOWN)
                .min(Comparator.comparingDouble(enumFacing -> {
                    Vec3 faceCenter = getFaceCenter(lastOverBlock, enumFacing);
                    Vec3 playerPos = mc.thePlayer.getPositionEyes(1f);
                    return playerPos.distanceTo(faceCenter);
                }))
                .orElse(null);

        if(face == null) {
            return;
        }

        Vec3 targetPos = getFaceCenter(lastOverBlock, face);

        rotations = getRotations(mc.thePlayer.getPositionEyes(1f), targetPos);
        lastTargetPos = targetPos;
        event.setPitch(rotations[1]);
        event.setYaw(rotations[0]);
    };

    @Override
    protected void onEnable() {
        rotations = new float[]{-mc.thePlayer.rotationYaw, 82f};
    };

    private Vec3 getFaceCenter(BlockPos pos, EnumFacing face) {
        return new Vec3(
                pos.getX() + 0.5 + face.getFrontOffsetX() * 0.5,
                pos.getY() + 0.5 + face.getFrontOffsetY() * 0.5,
                pos.getZ() + 0.5 + face.getFrontOffsetZ() * 0.5
        );
    }


    //note skidded tb removed for testing purposes
    public static void re(BlockPos bp, int color, boolean shade) {
        if (bp != null) {
            double x = (double) bp.getX() - mc.getRenderManager().viewerPosX;
            double y = (double) bp.getY() - mc.getRenderManager().viewerPosY;
            double z = (double) bp.getZ() - mc.getRenderManager().viewerPosZ;
            GL11.glBlendFunc(770, 771);
            GL11.glEnable(3042);
            GL11.glLineWidth(2.0F);
            GL11.glDisable(3553);
            GL11.glDisable(2929);
            GL11.glDepthMask(false);
            float a = (float) ((color >> 24) & 255) / 255.0F;
            float r = (float) ((color >> 16) & 255) / 255.0F;
            float g = (float) ((color >> 8) & 255) / 255.0F;
            float b = (float) (color & 255) / 255.0F;
            GL11.glColor4d(r, g, b, a);
            RenderGlobal.drawSelectionBoundingBox(new AxisAlignedBB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D));
            if (shade)
                dbb(new AxisAlignedBB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D), r, g, b);

            GL11.glEnable(3553);
            GL11.glEnable(2929);
            GL11.glDepthMask(true);
            GL11.glDisable(3042);
        }
    }

    public static void dbb(AxisAlignedBB abb, float r, float g, float b) {
        float a = 0.25F;
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

    public static void drawDot(Vec3 pos, int color, float size) {
        double x = pos.xCoord - mc.getRenderManager().viewerPosX;
        double y = pos.yCoord - mc.getRenderManager().viewerPosY;
        double z = pos.zCoord - mc.getRenderManager().viewerPosZ;

        GL11.glPushMatrix();
        GL11.glTranslated(x, y, z);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);

        float a = (float) ((color >> 24) & 255) / 255.0F;
        float r = (float) ((color >> 16) & 255) / 255.0F;
        float g = (float) ((color >> 8) & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;

        GL11.glColor4f(r, g, b, a);
        GL11.glPointSize(size);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        worldRenderer.begin(GL11.GL_POINTS, DefaultVertexFormats.POSITION);
        worldRenderer.pos(0, 0, 0).endVertex();
        tessellator.draw();

        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }


}
