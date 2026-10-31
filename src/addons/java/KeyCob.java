import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
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
import arsenic.utils.java.ColorUtils;
import arsenic.utils.minecraft.ContainerUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockWeb;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The manual version of AutoCob: press the key and a cobweb is placed where you look, on the nearest player, or on the
 * nearest player first and your crosshair as the fallback. Meant to be bound to a key (Toggle places once and switches
 * itself off, Hold keeps placing while held).
 */
@ModuleInfo(name = "KeyCob", description = "Places a cobweb where you aim or on the nearest player",
        category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class KeyCob extends Module {

    public enum Target {
        AUTO("Auto"),
        PLAYERS("Players"),
        AIM("Aim");

        private final String name;
        Target(String name) { this.name = name; }
        @Override public String toString() { return name; }
    }

    public final EnumProperty<Target> target = new EnumProperty<>("Target", Target.AUTO);
    public final DoubleProperty range = new DoubleProperty("Place Range", new DoubleValue(2, 4.5, 4.5, 0.1));
    public final DoubleProperty targetRange = new DoubleProperty("Target Range", new DoubleValue(1, 6, 4.5, 0.5));
    public final BooleanProperty predict = new BooleanProperty("Predict Fall", true);
    public final DoubleProperty speed = new DoubleProperty("Rotation Speed", new DoubleValue(30, 180, 140, 1));
    public final EnumProperty<SilentRotationManager.MovementFix> fix =
            new EnumProperty<>("Movement Fix", SilentRotationManager.MovementFix.SILENT);
    public final DoubleProperty delay = new DoubleProperty("Place Delay", new DoubleValue(50, 1000, 200, 25));
    public final BooleanProperty autoDisable = new BooleanProperty("Auto Disable", true);
    public final BooleanProperty ignoreTeams = new BooleanProperty("Ignore Teams", true);
    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("KeyCob", 4, 200, 110, 16);
    private final Random random = new Random();
    private final MSTimer placeTimer = MSTimer.expired();
    private final Map<Long, Integer> badSupports = new HashMap<>();

    private static final class Aim {
        final BlockPos support; final EnumFacing face; final float yaw, pitch; final double clearanceRad;
        Aim(BlockPos s, EnumFacing f, float y, float p, double c) { support = s; face = f; yaw = y; pitch = p; clearanceRad = c; }
    }

    private static final class Plan {
        final BlockPos web; final Aim aim; final String who;
        Plan(BlockPos w, Aim a, String n) { web = w; aim = a; who = n; }
    }

    private Plan plan;
    private BlockPos shownTarget;
    private BlockPos verifyPos;
    private long verifyKey;
    private int tickCounter, enabledAt, missTicks, sneakTicks, verifyTicks, placed;
    private int savedSlot = -1, lastSetSlot = -1;
    private boolean weSneak;

    private static final int RESOLVE_AFTER_MISSES = 3, BLACKLIST_TICKS = 60, GIVE_UP_TICKS = 15;

    @Override
    protected void onEnable() {
        enabledAt = tickCounter;
        missTicks = 0;
    }

    @Override
    protected void onDisable() {
        releaseSneak();
        restoreSlot();
        plan = null;
        shownTarget = null;
    }

    @Override
    public boolean isSwappingHotbar() { return lastSetSlot != -1; }

    @RequiresPlayer @EventLink
    public final Listener<EventTick> onTick = event -> {
        tickCounter++;
        if (lastSetSlot != -1 && mc.thePlayer.inventory.currentItem != lastSetSlot) {
            savedSlot = -1;
            lastSetSlot = -1;
        }
        if (verifyPos != null && ++verifyTicks >= 4) {
            if (isReplaceable(verifyPos)) badSupports.put(verifyKey, tickCounter + BLACKLIST_TICKS);
            else placed++;
            verifyPos = null;
        }
        if (mc.currentScreen != null || mc.theWorld == null) { plan = null; releaseSneak(); return; }
        if (plan == null && tickCounter - enabledAt > GIVE_UP_TICKS && autoDisable.getValue()) {
            setEnabled(false);
            return;
        }
        if (plan == null) pick();
        if (plan != null && needsSneak(plan.aim.support)) { holdSneak(); sneakTicks++; }
        else { sneakTicks = 0; releaseSneak(); }
    };

    @RequiresPlayer @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (mc.currentScreen != null || plan == null) return;
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

    @RequiresPlayer @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (plan == null || mc.currentScreen != null) return;
        MovingObjectPosition mop = event.getRayTrace();
        Aim aim = plan.aim;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !mop.getBlockPos().equals(aim.support) || mop.sideHit != aim.face) {
            if (++missTicks >= RESOLVE_AFTER_MISSES) { missTicks = 0; plan = null; }
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
            verifyPos = plan.web;
            verifyTicks = 0;
            verifyKey = key(mop.getBlockPos(), mop.sideHit);
            placeTimer.reset();
            placed++;
            shownTarget = plan.web;
            plan = null;
            releaseSneak();
            if (autoDisable.getValue()) setEnabled(false);
        }
    };

    private void pick() {
        plan = null;
        missTicks = 0;
        Target mode = target.getValue();
        if (mode != Target.AIM) {
            BlockPos web = nearestPlayerSpot();
            if (web != null) {
                Aim aim = solve(web);
                if (aim != null) { plan = new Plan(web, aim, "player"); shownTarget = web; return; }
            }
            if (mode == Target.PLAYERS) return;
        }
        BlockPos spot = aimSpot();
        if (spot == null) return;
        Aim aim = solve(spot);
        if (aim != null) { plan = new Plan(spot, aim, "aim"); shownTarget = spot; }
    }

    // where the crosshair currently points: one block past the face it hits
    private BlockPos aimSpot() {
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        if (mop.sideHit == EnumFacing.DOWN) return null;
        BlockPos spot = mop.getBlockPos().offset(mop.sideHit);
        return isReplaceable(spot) ? spot : null;
    }

    private BlockPos nearestPlayerSpot() {
        double best = Double.MAX_VALUE;
        BlockPos spot = null;
        List<EntityPlayer> players = PlayerUtils.getPlayersWithin(targetRange.getValue().getInput());
        for (EntityPlayer player : players) {
            if (player == null || !player.isEntityAlive() || player.getHealth() <= 0 || player.isSpectator()) continue;
            if (ignoreTeams.getValue() && PlayerUtils.isEntityTeamSameAsPlayer(player)) continue;
            double distance = mc.thePlayer.getDistanceToEntity(player);
            if (distance >= best) continue;
            BlockPos web = webSpot(player);
            if (web == null) continue;
            best = distance;
            spot = web;
        }
        return spot;
    }

    private BlockPos webSpot(EntityPlayer player) {
        if (predict.getValue() && !player.onGround) {
            int from = (int) Math.floor(player.posY) - 1;
            int bottom = Math.max(1, from - 40);
            for (int y = from; y >= bottom; y--) {
                BlockPos support = new BlockPos(player.posX, y, player.posZ);
                if (!mc.theWorld.getBlockState(support).getBlock().getMaterial().isSolid()) continue;
                BlockPos web = support.up();
                return isReplaceable(web) ? web : null;
            }
            return null;
        }
        BlockPos feet = new BlockPos(player.posX, player.posY + 0.1, player.posZ);
        return isReplaceable(feet) ? feet : null;
    }

    private boolean tooFar(BlockPos pos) {
        return mc.thePlayer.getPositionEyes(1f).distanceTo(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5))
                > range.getValue().getInput() + 0.5;
    }

    private Aim solve(BlockPos placePos) {
        if (tooFar(placePos)) return null;
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

    private void restoreSlot() {
        if (savedSlot != -1 && mc.thePlayer != null && mc.thePlayer.inventory.currentItem == lastSetSlot)
            mc.thePlayer.inventory.currentItem = savedSlot;
        savedSlot = -1;
        lastSetSlot = -1;
    }

    private boolean needsSneak(BlockPos support) {
        return ContainerUtils.isInteractable(mc.theWorld.getBlockState(support).getBlock());
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
        if (!render.getValue()) return;
        BlockPos shown = plan != null ? plan.web : shownTarget;
        if (shown == null || mc.theWorld == null) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        RenderUtils.renderBlock(shown, ColorUtils.withAlpha(main, 90), false, true);
        RenderUtils.renderBlock(shown, ColorUtils.withAlpha(main, 230), true, false);
        if (plan != null) RenderUtils.renderBlockFace(plan.aim.support, plan.aim.face, ColorUtils.withAlpha(main, 200), true, true);
    };

    @EventLink
    public final Listener<EventRender2D> onRenderHud = event -> {
        if (!hud.getValue()) return;
        String text;
        if (plan != null) text = "KeyCob: " + plan.who;
        else text = "KeyCob: idle";
        text += "  x" + placed;
        panel.setSize(Math.max(80, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
