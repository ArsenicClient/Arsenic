import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.injection.accessor.IMixinEntity;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.minecraft.ContainerUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockWeb;
import net.minecraft.block.material.Material;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * When a hit throws you off the side of a block, puts a cobweb in the way so the knockback stops instead of carrying
 * you into the void. Reacts to the velocity packet, predicts where you will be one tick later, and clicks the side of
 * the block you were standing on with a silent rotation (so it works while the camera keeps looking at the enemy).
 */
@ModuleInfo(name = "CobClutch", description = "Places a cobweb to stop a knockback off a block edge",
        category = ModuleCategory.MOVEMENT, tier = ModuleTier.BLATANT)
public class CobClutch extends Module {

    public final DoubleProperty range = new DoubleProperty("Place Range", new DoubleValue(2, 4.5, 4.5, 0.1));
    public final DoubleProperty speed = new DoubleProperty("Rotation Speed", new DoubleValue(30, 180, 150, 1));
    public final EnumProperty<SilentRotationManager.MovementFix> fix =
            new EnumProperty<>("Movement Fix", SilentRotationManager.MovementFix.SILENT);
    public final DoubleProperty minKnock = new DoubleProperty("Min Knock", new DoubleValue(0.15, 1.2, 0.35, 0.05));
    public final DoubleProperty lead = new DoubleProperty("Lead Ticks", new DoubleValue(1, 4, 1, 1));
    public final RangeProperty reaction = new RangeProperty("Reaction Ticks", new RangeValue(0, 4, 0, 1, 1));
    public final BooleanProperty voidOnly = new BooleanProperty("Void Only", true);
    public final DoubleProperty delay = new DoubleProperty("Place Delay", new DoubleValue(50, 500, 100, 5));
    public final DoubleProperty maxWebs = new DoubleProperty("Max Per Hit", new DoubleValue(1, 3, 2, 1));
    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("CobClutch", 4, 160, 120, 16);
    private final Random random = new Random();
    private final MSTimer placeTimer = MSTimer.expired();
    private final Map<Long, Integer> badSupports = new HashMap<>();

    private static final class Aim {
        final BlockPos support; final EnumFacing face; final float yaw, pitch; final double clearanceRad;
        Aim(BlockPos s, EnumFacing f, float y, float p, double c) { support = s; face = f; yaw = y; pitch = p; clearanceRad = c; }
    }

    private static final class Plan {
        final BlockPos target; final Aim aim;
        Plan(BlockPos t, Aim a) { target = t; aim = a; }
    }

    private Plan plan;
    private BlockPos shownTarget, verifyPos;
    private long verifyKey;
    private int tickCounter, knockTick = -100, reactionTicks, websThisHit, missTicks, sneakTicks, verifyTicks, placed;
    private double knockVX, knockVZ, lastSpeed;
    // written on the netty thread by the packet listener, consumed in the tick
    private volatile boolean knockPacket;
    private volatile double packetVX, packetVZ;
    private int savedSlot = -1, lastSetSlot = -1;
    private boolean weSneak;

    private static final int KNOCK_WINDOW = 8, RESOLVE_AFTER_MISSES = 3, BLACKLIST_TICKS = 60;

    @Override
    protected void onDisable() {
        releaseSneak();
        restoreSlot();
        plan = null;
        shownTarget = null;
    }

    @Override
    public boolean isSwappingHotbar() { return lastSetSlot != -1; }

    // the server tells us the knockback in the velocity packet; that is the earliest possible signal.
    // netty thread: only copy the numbers out, the tick does the work
    @EventLink
    public final Listener<EventPacket.Incoming.Post> onPacket = event -> {
        Packet<?> packet = event.getPacket();
        if (!(packet instanceof S12PacketEntityVelocity)) return;
        S12PacketEntityVelocity velocity = (S12PacketEntityVelocity) packet;
        if (mc.thePlayer == null || velocity.getEntityID() != mc.thePlayer.getEntityId()) return;
        packetVX = velocity.getMotionX() / 8000.0;
        packetVZ = velocity.getMotionZ() / 8000.0;
        knockPacket = true;
    };

    @RequiresPlayer @EventLink
    public final Listener<EventTick> onTick = event -> {
        tickCounter++;
        EntityPlayerSP p = mc.thePlayer;
        if (p == null) return;

        if (knockPacket) {
            knockPacket = false;
            double vx = packetVX, vz = packetVZ;
            if (Math.hypot(vx, vz) >= minKnock.getValue().getInput()) {
                knockVX = vx;
                knockVZ = vz;
                knockTick = tickCounter;
                websThisHit = 0;
                plan = null;
                missTicks = 0;
            }
        }

        // the user scrolled: hands off the hotbar until it is ours again
        if (lastSetSlot != -1 && p.inventory.currentItem != lastSetSlot) {
            savedSlot = -1;
            lastSetSlot = -1;
        }
        // a knockback the server did not report as a velocity packet still shows up in the motion
        double hs = Math.hypot(p.motionX, p.motionZ);
        if (hs - lastSpeed > 0.25 && hs >= minKnock.getValue().getInput()) {
            knockVX = p.motionX;
            knockVZ = p.motionZ;
            knockTick = tickCounter;
            websThisHit = 0;
            plan = null;
            missTicks = 0;
        }
        lastSpeed = hs;

        if (verifyPos != null && ++verifyTicks >= 4) {
            if (isReplaceable(verifyPos)) badSupports.put(verifyKey, tickCounter + BLACKLIST_TICKS);
            else placed++;
            verifyPos = null;
        }

        if (!active()) {
            plan = null;
            if (tickCounter - knockTick > KNOCK_WINDOW) shownTarget = null;
            return;
        }
        if (websThisHit >= (int) maxWebs.getValue().getInput()) { plan = null; return; }
        if (plan == null && tickCounter - knockTick >= reactionTicks) pick();
        if (plan != null && needsSneak(plan.aim.support)) { holdSneak(); sneakTicks++; }
        else { sneakTicks = 0; releaseSneak(); }
    };

    @RequiresPlayer @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (mc.currentScreen != null || plan == null || !active()) return;
        Aim aim = plan.aim;
        float yaw = aim.yaw, pitch = aim.pitch;
        if (aim.clearanceRad < settledFloorRad()) {
            double[] d = dither(tickCounter);
            yaw += (float) (d[0] * RotationUtils.getGCD());
            pitch += (float) (d[1] * RotationUtils.getGCD());
        }
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setSpeed((float) (speed.getValue().getInput() * (0.95 + random.nextDouble() * 0.1)));
        event.setPreventDuplicateLook(true);
        event.setBlockUserInput(true);
        event.setMovementFix(fix.getValue());
    };

    // only click when the rotation that is actually being sent hits the intended face
    @RequiresPlayer @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (plan == null || mc.currentScreen != null) return;
        MovingObjectPosition mop = event.getRayTrace();
        Aim aim = plan.aim;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !mop.getBlockPos().equals(aim.support) || mop.sideHit != aim.face) {
            if (++missTicks >= RESOLVE_AFTER_MISSES) { missTicks = 0; plan = null; pick(); }
            return;
        }
        missTicks = 0;
        if (!placeTimer.finished((long) delay.getValue().getInput())) return;
        if (needsSneak(mop.getBlockPos()) && !(mc.thePlayer.isSneaking() && sneakTicks >= 2)) return;
        int slot = findWebSlot();
        if (slot == -1) { plan = null; return; }
        if (savedSlot == -1) savedSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = slot;
        lastSetSlot = slot;
        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || !isWeb(held)) { restoreSlot(); plan = null; return; }
        if (!((ItemBlock) held.getItem()).canPlaceBlockOnSide(mc.theWorld, mop.getBlockPos(), mop.sideHit, mc.thePlayer, held))
            return;
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held, mop.getBlockPos(), mop.sideHit, mop.hitVec)) {
            mc.thePlayer.swingItem();
            verifyPos = plan.target;
            verifyTicks = 0;
            verifyKey = key(mop.getBlockPos(), mop.sideHit);
            placeTimer.reset();
            websThisHit++;
            shownTarget = plan.target;
            plan = null;
            releaseSneak();
        }
    };

    private boolean active() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return false;
        if (tickCounter - knockTick > KNOCK_WINDOW) return false;
        EntityPlayerSP p = mc.thePlayer;
        if (p.isSpectator() || p.capabilities.isFlying || p.capabilities.isCreativeMode) return false;
        if (voidOnly.getValue() && !voidAhead()) return false;
        return true;
    }

    // the block we stand on is solid, so "below you is void" means the place the knockback is taking us
    private boolean voidAhead() {
        EntityPlayerSP p = mc.thePlayer;
        double vx = knockVX, vz = knockVZ;
        if (Math.hypot(vx, vz) < 1e-4) {
            vx = -Math.sin(Math.toRadians(p.rotationYaw));
            vz = Math.cos(Math.toRadians(p.rotationYaw));
        }
        double length = Math.hypot(vx, vz);
        vx /= length;
        vz /= length;
        int fromY = (int) Math.floor(p.posY);
        for (int t = 1; t <= 3; t++) {
            if (columnVoid(p.posX + vx * t, fromY, p.posZ + vz * t)) return true;
        }
        return false;
    }

    private boolean columnVoid(double x, int fromY, double z) {
        for (int y = fromY - 1; y >= 0; y--) {
            if (mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock().getMaterial() != Material.air) return false;
        }
        return true;
    }

    // web first where the knockback is taking us, then at our feet, then one more tick ahead
    private void pick() {
        plan = null;
        missTicks = 0;
        EntityPlayerSP p = mc.thePlayer;
        double vx = knockVX, vz = knockVZ;
        if (Math.hypot(vx, vz) < 1e-4) {
            vx = -Math.sin(Math.toRadians(p.rotationYaw));
            vz = Math.cos(Math.toRadians(p.rotationYaw));
        }
        double length = Math.hypot(vx, vz);
        vx /= length;
        vz /= length;
        int leadTicks = Math.max(1, (int) lead.getValue().getInput());
        List<BlockPos> spots = new ArrayList<>();
        for (int t = leadTicks; t >= 1; t--) add(spots, new BlockPos(p.posX + vx * t, p.posY, p.posZ + vz * t));
        add(spots, new BlockPos(p.posX, p.posY, p.posZ));
        for (BlockPos spot : spots) {
            if (!isReplaceable(spot) || tooFar(spot)) continue;
            Aim a = solve(spot);
            if (a == null) continue;
            plan = new Plan(spot, a);
            shownTarget = spot;
            return;
        }
    }

    private void add(List<BlockPos> list, BlockPos pos) {
        if (!list.contains(pos)) list.add(pos);
    }

    private boolean tooFar(BlockPos pos) {
        return mc.thePlayer.getPositionEyes(1f).distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))
                > range.getValue().getInput() + 0.5;
    }

    private Aim solve(BlockPos placePos) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1f);
        double reach = range.getValue().getInput();
        Aim best = null;
        for (EnumFacing face : EnumFacing.values()) {
            BlockPos support = placePos.offset(face.getOpposite());
            if (isReplaceable(support)) continue;
            Integer retryAt = badSupports.get(key(support, face));
            if (retryAt != null && retryAt > tickCounter) continue;
            Block block = mc.theWorld.getBlockState(support).getBlock();
            block.setBlockBoundsBasedOnState(mc.theWorld, support);
            AxisAlignedBB box = block.getSelectedBoundingBox(mc.theWorld, support);

            final int n = 7;
            float[][][] rots = new float[n][n][];
            boolean[][] hit = new boolean[n][n];
            for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
                double u = -0.125 + 1.25 * i / (n - 1), v = -0.125 + 1.25 * j / (n - 1);
                rots[i][j] = RotationUtils.rotationsTo(eyes, facePoint(box, face, u, v));
                hit[i][j] = rayHits(eyes, rots[i][j][0], rots[i][j][1], reach, support, face);
            }
            for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
                if (!hit[i][j]) continue;
                double clearance = Double.MAX_VALUE;
                for (int a = 0; a < n; a++) for (int b = 0; b < n; b++) {
                    if (hit[a][b]) continue;
                    double dy = RotationUtils.getYawDifference(rots[i][j][0], rots[a][b][0]);
                    double dp = rots[i][j][1] - rots[a][b][1];
                    clearance = Math.min(clearance, Math.toRadians(Math.sqrt(dy * dy + dp * dp)));
                }
                if (best != null && clearance <= best.clearanceRad) continue;
                float yaw = rots[i][j][0], pitch = rots[i][j][1];
                float jy = (float) ((random.nextDouble() - 0.5) * Math.toDegrees(clearance) * 0.5);
                float jp = (float) ((random.nextDouble() - 0.5) * Math.toDegrees(clearance) * 0.5);
                if (rayHits(eyes, yaw + jy, pitch + jp, reach, support, face)) { yaw += jy; pitch += jp; }
                best = new Aim(support, face, yaw, pitch, clearance);
            }
        }
        return best;
    }

    private boolean rayHits(Vec3 eyes, float yaw, float pitch, double reach, BlockPos support, EnumFacing face) {
        Vec3 look = ((IMixinEntity) mc.thePlayer).invokeGetVectorForRotation(pitch, yaw);
        Vec3 end = eyes.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(eyes, end, false, false, true);
        return mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mop.getBlockPos().equals(support) && mop.sideHit == face;
    }

    private static Vec3 facePoint(AxisAlignedBB b, EnumFacing f, double u, double v) {
        switch (f.getAxis()) {
            case Y: return new Vec3(b.minX + (b.maxX - b.minX) * u, f == EnumFacing.UP ? b.maxY : b.minY, b.minZ + (b.maxZ - b.minZ) * v);
            case X: return new Vec3(f == EnumFacing.EAST ? b.maxX : b.minX, b.minY + (b.maxY - b.minY) * u, b.minZ + (b.maxZ - b.minZ) * v);
            default: return new Vec3(b.minX + (b.maxX - b.minX) * u, b.minY + (b.maxY - b.minY) * v, f == EnumFacing.SOUTH ? b.maxZ : b.minZ);
        }
    }

    private static double settledFloorRad() { return Math.toRadians(RotationUtils.getGCD()) * 3.0 + 0.0015; }

    private static double[] dither(int tick) {
        double[][] d = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {2, 0}, {-2, 0}};
        return d[tick % d.length];
    }

    private static long key(BlockPos p, EnumFacing f) { return p.toLong() * 8 + f.getIndex(); }

    private boolean isReplaceable(BlockPos p) {
        return mc.theWorld.getBlockState(p).getBlock().getMaterial().isReplaceable();
    }

    private boolean isWeb(ItemStack stack) {
        return stack.getItem() instanceof ItemBlock && ((ItemBlock) stack.getItem()).getBlock() instanceof BlockWeb;
    }

    private int findWebSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (s != null && s.stackSize > 0 && isWeb(s)) return i;
        }
        return -1;
    }

    // the hotbar slot we took is only ours to restore while the user has not scrolled away from it
    private void restoreSlot() {
        if (savedSlot != -1 && mc.thePlayer != null && mc.thePlayer.inventory.currentItem == lastSetSlot)
            mc.thePlayer.inventory.currentItem = savedSlot;
        savedSlot = -1;
        lastSetSlot = -1;
    }

    // ---- real sneak for interactive support blocks ----
    private boolean needsSneak(BlockPos support) {
        Block b = mc.theWorld.getBlockState(support).getBlock();
        return ContainerUtils.isInteractable(b);
    }

    private void holdSneak() {
        KeyBinding kb = mc.gameSettings.keyBindSneak;
        KeyBinding.setKeyBindState(kb.getKeyCode(), true);
        weSneak = true;
    }

    private void releaseSneak() {
        if (!weSneak || mc.gameSettings == null) return;
        KeyBinding kb = mc.gameSettings.keyBindSneak;
        int code = kb.getKeyCode();
        boolean userHolds = code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
        KeyBinding.setKeyBindState(code, userHolds);
        weSneak = false;
    }

    @RequiresPlayer @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        BlockPos target = plan != null ? plan.target : shownTarget;
        if (!render.getValue() || target == null || mc.theWorld == null) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        RenderUtils.renderBlock(target, ColorUtils.withAlpha(main, 90), false, true);
        RenderUtils.renderBlock(target, ColorUtils.withAlpha(main, 230), true, false);
        if (plan != null) RenderUtils.renderBlockFace(plan.aim.support, plan.aim.face, ColorUtils.withAlpha(main, 200), true, true);
    };

    @EventLink
    public final Listener<EventRender2D> onRenderHud = event -> {
        if (!hud.getValue()) return;
        String text;
        if (active() && plan != null) text = "CobClutch: placing";
        else if (active() && findWebSlot() == -1) text = "CobClutch: no cobweb";
        else if (tickCounter - knockTick <= KNOCK_WINDOW) text = "CobClutch: no spot";
        else text = "CobClutch: idle";
        text += "  x" + placed;
        panel.setSize(Math.max(90, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
