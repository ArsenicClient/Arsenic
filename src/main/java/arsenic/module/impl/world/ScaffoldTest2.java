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
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.function.Supplier;


@ModuleInfo(name = "scaffoldTest2", category = ModuleCategory.WORLD)
public class ScaffoldTest2 extends Module {
    public final BooleanProperty allowTower = new BooleanProperty("Allow Tower", false);
    private BlockPos lastOverBlock;

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
    };

    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        //tower
        event.setSpeed(180f);
        event.setDoMovementFix(false);
        if(allowTower.getValue() && mc.thePlayer.motionX == 0 && mc.thePlayer.motionZ == 0 && !PlayerUtils.playerOverAir()) {
            event.setPitch(90);
            return;
        }

        if(lastOverBlock == null)
            return;

        final HashMap<Double, Supplier<float[]>> map = new HashMap<>();

        for(EnumFacing enumFacing : EnumFacing.values()) {
            if(enumFacing == EnumFacing.UP || enumFacing == EnumFacing.DOWN)
                continue;
            helper2(map, enumFacing);
        }

        double lowestVal = map.keySet().stream().min(Comparator.naturalOrder()).get();
        float[] rotations = map.get(lowestVal).get();

        if(Keyboard.isKeyDown(Keyboard.KEY_B)) {
            rotations = RotationUtils.getPlayerRotationsToBlock(lastOverBlock, EnumFacing.NORTH);
            mc.thePlayer.rotationPitch = rotations[1];
            mc.thePlayer.rotationYaw = rotations[0];
        }
        event.setPitch(rotations[1]);
        event.setYaw(rotations[0]);
    };

    private void helper2(HashMap<Double, Supplier<float[]>> map, EnumFacing f) {
        map.put(helper(f), () -> RotationUtils.getPlayerRotationsToBlock(lastOverBlock, f));
    }

    private double helper(EnumFacing f) {
        Vec3 vec = RotationUtils.getVec3FromBlockPosAndEnumFacing(lastOverBlock, f);
        vec = new Vec3(vec.xCoord, mc.thePlayer.posY, vec.zCoord);
        return vec.distanceTo(mc.thePlayer.getPositionVector());
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


}
