import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
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
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * When you fall towards the void, finds the nearest standable block that is not above your reach, solves the ender pearl
 * trajectory onto it, rotates there silently and throws a pearl from the hotbar. Useful for BuildFFA and similar modes.
 */
@ModuleInfo(name = "AutoPearlClutch", description = "Pearls to the nearest safe surface when you fall into the void",
        category = ModuleCategory.MOVEMENT, tier = ModuleTier.BLATANT)
public class AutoPearlClutch extends Module {

    public final DoubleProperty speed = new DoubleProperty("Rotation Speed", new DoubleValue(60, 180, 150, 1));
    public final EnumProperty<SilentRotationManager.MovementFix> fix =
            new EnumProperty<>("Movement Fix", SilentRotationManager.MovementFix.SILENT);
    public final DoubleProperty minFall = new DoubleProperty("Min Fall Distance", new DoubleValue(2, 20, 5, 0.5));
    public final DoubleProperty voidY = new DoubleProperty("Void Y", new DoubleValue(-64, 100, 0, 1));
    public final DoubleProperty radius = new DoubleProperty("Search Radius", new DoubleValue(6, 32, 20, 1));
    public final DoubleProperty maxUp = new DoubleProperty("Max Up", new DoubleValue(2, 24, 14, 1));
    public final DoubleProperty maxDown = new DoubleProperty("Max Down", new DoubleValue(0, 12, 6, 1));
    public final BooleanProperty avoidEdges = new BooleanProperty("Avoid Edges", true);
    public final BooleanProperty lastResort = new BooleanProperty("Last Resort", true);
    public final RangeProperty reaction = new RangeProperty("Reaction Ticks", new RangeValue(0, 8, 1, 2, 1));
    public final DoubleProperty cooldown = new DoubleProperty("Cooldown Ticks", new DoubleValue(20, 80, 45, 1));
    public final BooleanProperty render = new BooleanProperty("Render", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("AutoPearlClutch", 4, 140, 110, 16);
    private final Random random = new Random();

    private static final double LANDING_TOLERANCE = 2.5;
    private static final int MAX_CANDIDATES = 14;
    private static final int RESCAN_TICKS = 5;

    private static final class Cand {
        final BlockPos pos; final Vec3 center; final double score;
        Cand(BlockPos p, double s) { pos = p; center = new Vec3(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5); score = s; }
    }

    private static final class Sim {
        boolean hit; Vec3 landing; int ticks; Direction side; BlockPos block;
    }

    private static final class Aim {
        final BlockPos target; final Vec3 center; final float yaw, pitch; final int ticks; final boolean safe;
        Aim(BlockPos t, Vec3 c, float y, float p, int tk, boolean s) { target = t; center = c; yaw = y; pitch = p; ticks = tk; safe = s; }
    }

    private final List<Cand> candidates = new ArrayList<>();
    private Aim aim;
    private BlockPos locked, shownTarget;
    private Vec3 landing;
    private boolean active, ready;
    private int tickCounter, lastScan = -100, startTick, reactionTicks, cooldownLeft, pearlSlot = -1, thrown;
    private int savedSlot = -1, lastSetSlot = -1, restoreIn;

    @Override
    protected void onDisable() {
        restoreSlot();
        cooldownLeft = 0;
        reset();
    }

    @Override
    public boolean isSwappingHotbar() { return lastSetSlot != -1; }

    @RequiresPlayer @EventLink
    public final Listener<EventTick> onTick = event -> {
        tickCounter++;
        if (restoreIn > 0 && --restoreIn == 0) restoreSlot();
        if (cooldownLeft > 0) cooldownLeft--;

        if (!shouldClutch()) { reset(); return; }
        if (!active) {
            active = true;
            startTick = tickCounter;
            int min = (int) reaction.getValue().getMin(), max = (int) reaction.getValue().getMax();
            reactionTicks = min + random.nextInt(Math.max(0, max - min) + 1);
        }
        if (cooldownLeft > 0) { ready = false; aim = null; return; }

        pearlSlot = findPearlSlot();
        if (pearlSlot == -1) { ready = false; aim = null; return; }
        if (tickCounter - lastScan >= RESCAN_TICKS || candidates.isEmpty()) scan();

        if (ready && aim != null && tickCounter - startTick >= reactionTicks) throwPearl();
    };

    @RequiresPlayer @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (!active || cooldownLeft > 0 || pearlSlot == -1 || candidates.isEmpty() || mc.gui.screen() != null) {
            aim = null;
            return;
        }
        aim = solveBest();
        if (aim == null) return;
        locked = aim.target;
        shownTarget = aim.target;
        event.setYaw(aim.yaw);
        event.setPitch(aim.pitch);
        event.setSpeed((float) (speed.getValue().getInput() * (0.95 + random.nextDouble() * 0.1)));
        event.setPreventDuplicateLook(true);
        event.setBlockUserInput(true);
        event.setMovementFix(fix.getValue());
    };

    // only throw once the rotation actually being sent lands the pearl near the target
    @RequiresPlayer @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        ready = false;
        if (!active || cooldownLeft > 0 || aim == null) return;
        Vec3 eyes = predictedEyes();
        Sim s = simulate(eyes.x, eyes.y, eyes.z, event.getYaw(), event.getPitch());
        if (!landingOk(s)) return;
        if (s.landing.distanceTo(aim.center) > LANDING_TOLERANCE) return;
        if (!timeSafe(s.ticks) && !lastResort.getValue()) return;
        landing = s.landing;
        ready = true;
    };

    private boolean shouldClutch() {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null) return false;
        if (p.onGround() || p.isInWater() || p.isInLava() || p.onClimbable() || p.isPassenger() || p.isSpectator()
                || p.getAbilities().flying || p.getAbilities().instabuild) return false;
        if (p.getDeltaMovement().y > -0.1) return false;
        if (p.fallDistance < minFall.getValue().getInput()) return false;
        if (p.getY() < voidY.getValue().getInput()) return false;
        return voidBelow();
    }

    private boolean voidBelow() {
        LocalPlayer p = mc.player;
        double[][] offs = {{0, 0}, {0.3, 0.3}, {0.3, -0.3}, {-0.3, 0.3}, {-0.3, -0.3}};
        int top = (int) Math.floor(p.getY()) - 1;
        for (double[] o : offs) {
            for (int y = top; y >= 0; y--) {
                if (!mc.level.getBlockState(BlockPos.containing(p.getX() + o[0], y, p.getZ() + o[1])).isAir())
                    return false;
            }
        }
        return true;
    }

    private void scan() {
        lastScan = tickCounter;
        candidates.clear();
        LocalPlayer p = mc.player;
        Vec3 m = p.getDeltaMovement();
        double px = p.getX() + m.x, py = p.getY() + m.y, pz = p.getZ() + m.z;
        int r = (int) radius.getValue().getInput();
        int bx = (int) Math.floor(px), bz = (int) Math.floor(pz), by = (int) Math.floor(py);
        int yTop = by + (int) maxUp.getValue().getInput(), yBottom = Math.max(0, by - (int) maxDown.getValue().getInput());
        List<Cand> all = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dz * dz > r * r) continue;
            for (int y = yTop; y >= yBottom; y--) {
                BlockPos pos = new BlockPos(bx + dx, y, bz + dz);
                if (!standable(pos)) continue;
                double hd = Math.sqrt(dx * dx + dz * dz);
                double score = hd + 0.5 * Math.abs(y + 1 - py);
                if (avoidEdges.getValue() && !inset(pos)) score += 3.0;
                all.add(new Cand(pos, score));
                break; // topmost surface of this column only
            }
        }
        all.sort(Comparator.comparingDouble(c -> c.score));
        for (int i = 0; i < Math.min(MAX_CANDIDATES, all.size()); i++) candidates.add(all.get(i));
    }

    private boolean standable(BlockPos pos) {
        BlockState b = mc.level.getBlockState(pos);
        if (!b.isCollisionShapeFullBlock(mc.level, pos)) return false;
        return mc.level.getBlockState(pos.above()).isAir() && mc.level.getBlockState(pos.above(2)).isAir();
    }

    private boolean inset(BlockPos pos) {
        for (Direction f : Direction.Plane.HORIZONTAL)
            if (!mc.level.getBlockState(pos.relative(f)).isCollisionShapeFullBlock(mc.level, pos.relative(f))) return false;
        return true;
    }

    private Aim solveBest() {
        Vec3 eyes = predictedEyes();
        List<Cand> order = new ArrayList<>(candidates);
        if (locked != null) { // try the locked target first so we do not flick between candidates
            for (int i = 0; i < order.size(); i++) {
                if (order.get(i).pos.equals(locked)) { order.add(0, order.remove(i)); break; }
            }
        }
        Aim fastest = null;
        for (Cand c : order) {
            Aim a = solve(c, eyes);
            if (a == null) continue;
            if (a.safe) return a;
            if (fastest == null || a.ticks < fastest.ticks) fastest = a;
        }
        return lastResort.getValue() ? fastest : null;
    }

    private Aim solve(Cand c, Vec3 eyes) {
        double tx = c.center.x, ty = c.center.y, tz = c.center.z;
        double sx = eyes.x, sy = eyes.y - 0.1, sz = eyes.z;
        float yaw = yawTo(tx - sx, tz - sz);
        double yr = Math.toRadians(yaw);
        double stx = sx, stz = sz;
        Vec3 own = mc.player.getDeltaMovement();
        double ownAlong = own.x * -Math.sin(yr) + own.z * Math.cos(yr);   // the thrower's motion along the throw
        double dist = Math.hypot(tx - stx, tz - stz);
        double floorY = voidY.getValue().getInput() - 30;

        // 2D sweep over pitch for descending arcs that reach the target height
        List<float[]> sols = new ArrayList<>();
        for (float pitch = -89.5f; pitch <= 60f; pitch += 0.5f) {
            double pr = Math.toRadians(pitch);
            double vh = 1.5 * Math.cos(pr) + ownAlong, vy = -1.5 * Math.sin(pr) + own.y;
            double dh = 0, y = sy;
            for (int t = 0; t < 90; t++) {
                if (dh + vh >= dist) {
                    double yAt = y + vy * ((dist - dh) / vh);
                    if (vy < 0 && Math.abs(yAt - ty) < 0.35) sols.add(new float[]{pitch, t + 1});
                    break;
                }
                dh += vh; y += vy; vh *= 0.99; vy = vy * 0.99 - 0.03;
                if (y < floorY) break;
            }
        }
        sols.sort(Comparator.comparingDouble(s -> s[1]));

        for (int i = 0; i < Math.min(6, sols.size()); i++) {
            float pitch = sols.get(i)[0];
            Sim s = simulate(eyes.x, eyes.y, eyes.z, yaw, pitch);
            if (!landingOk(s) || s.landing.distanceTo(c.center) > LANDING_TOLERANCE) continue;
            float jy = yaw + (float) ((random.nextDouble() - 0.5) * 0.4);
            float jp = pitch + (float) ((random.nextDouble() - 0.5) * 0.4);
            Sim sj = simulate(eyes.x, eyes.y, eyes.z, jy, jp);
            if (landingOk(sj) && sj.landing.distanceTo(c.center) <= LANDING_TOLERANCE) { s = sj; yaw = jy; pitch = jp; }
            return new Aim(c.pos, c.center, yaw, pitch, s.ticks, timeSafe(s.ticks));
        }
        return null;
    }

    // ender pearl physics: 1.5 initial speed plus the thrower's motion, 0.99 drag, 0.03 gravity, block ray per tick
    private Sim simulate(double ex, double ey, double ez, float yaw, float pitch) {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double x = ex, y = ey - 0.1, z = ez;
        Vec3 own = mc.player.getDeltaMovement();
        double mx = -Math.sin(yr) * Math.cos(pr) * 1.5 + own.x, my = -Math.sin(pr) * 1.5 + own.y, mz = Math.cos(yr) * Math.cos(pr) * 1.5 + own.z;
        double floorY = voidY.getValue().getInput() - 30;
        Sim s = new Sim();
        for (int t = 0; t < 120; t++) {
            Vec3 a = new Vec3(x, y, z), b = new Vec3(x + mx, y + my, z + mz);
            BlockHitResult mop = mc.level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (mop.getType() == HitResult.Type.BLOCK) {
                s.hit = true;
                s.landing = a;
                s.ticks = t + 1;
                s.side = mop.getDirection();
                s.block = mop.getBlockPos();
                return s;
            }
            x += mx; y += my; z += mz;
            mx *= 0.99; my *= 0.99; mz *= 0.99; my -= 0.03;
            if (y < floorY) break;
        }
        return s;
    }

    private boolean landingOk(Sim s) {
        if (s == null || !s.hit || s.side != Direction.UP || !standable(s.block)) return false;
        if (s.landing.y - (s.block.getY() + 1) > 2.5) return false;
        BlockPos feet = BlockPos.containing(s.landing);
        return passable(feet) && passable(feet.above());
    }

    private boolean passable(BlockPos pos) {
        return mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty();
    }

    // whether the player is still above the void when the pearl lands
    private boolean timeSafe(int flightTicks) {
        LocalPlayer p = mc.player;
        double my = p.getDeltaMovement().y;
        double y = p.getY() + my, vy = (my - 0.08) * 0.98;
        for (int i = 0; i < flightTicks; i++) { y += vy; vy = (vy - 0.08) * 0.98; }
        return y > voidY.getValue().getInput() + 0.5;
    }

    private Vec3 predictedEyes() {
        LocalPlayer p = mc.player;
        Vec3 m = p.getDeltaMovement();
        return new Vec3(p.getX() + m.x, p.getY() + m.y + p.getEyeHeight(), p.getZ() + m.z);
    }

    private static float yawTo(double dx, double dz) { return (float) Math.toDegrees(Math.atan2(-dx, dz)); }

    private int findPearlSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() == Items.ENDER_PEARL) return i;
        }
        return -1;
    }

    // swap to the pearl, throw, and swap back two ticks later
    private void throwPearl() {
        if (mc.player.isUsingItem()) mc.gameMode.releaseUsingItem(mc.player);
        if (savedSlot == -1) savedSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(pearlSlot);
        lastSetSlot = pearlSlot;
        ItemStack held = mc.player.getMainHandItem();
        if (held.getItem() != Items.ENDER_PEARL) { restoreSlot(); return; }
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        thrown++;
        cooldownLeft = (int) cooldown.getValue().getInput();
        restoreIn = 2;
        ready = false;
    }

    private void restoreSlot() {
        if (savedSlot != -1 && mc.player != null && mc.player.getInventory().getSelectedSlot() == lastSetSlot)
            mc.player.getInventory().setSelectedSlot(savedSlot);
        savedSlot = -1;
        lastSetSlot = -1;
    }

    private void reset() {
        active = false;
        ready = false;
        aim = null;
        locked = null;
        landing = null;
        lastScan = -100;
        candidates.clear();
        if (cooldownLeft <= 0) shownTarget = null;
    }

    @RequiresPlayer @EventLink
    public final Listener<EventRenderWorldLast> onRenderWorld = event -> {
        if (!render.getValue() || shownTarget == null || mc.level == null) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        RenderUtils.renderBlock(shownTarget, ColorUtils.withAlpha(main, 90), false, true);
        RenderUtils.renderBlock(shownTarget, ColorUtils.withAlpha(main, 230), true, false);
        if (landing != null && aim != null) {
            BlockPos feet = BlockPos.containing(landing);
            RenderUtils.renderBlock(feet, ColorUtils.withAlpha(main, 150), true, false);
        }
    };

    @EventLink
    public final Listener<EventRender2D> onRenderHud = event -> {
        if (!hud.getValue()) return;
        String text;
        if (!active) text = "Pearl Clutch: idle";
        else if (pearlSlot == -1) text = "No pearl in hotbar!";
        else if (cooldownLeft > 0) text = "Pearl away";
        else if (aim == null) text = "No landing found";
        else text = ready ? "Throwing" : "Aiming";
        text += "  x" + thrown;
        panel.setSize(Math.max(80, mc.font.width(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        DrawUtils.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
