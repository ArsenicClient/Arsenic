package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.gui.themes.ThemeManager;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.render.RenderUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Traces where the nearest opponent is heading: their falling arc and landing spot while airborne, or where they are
 * sliding on the ground, with a 3 block ring showing the space they can reach you from.
 */
@ModuleInfo(name = "MacePrediction", description = "Predicts your opponent's falling and ground path and shows their reach", category = ModuleCategory.COMBAT, tier = ModuleTier.VISUAL)
public class MacePrediction extends Module {

    private static final double GRAVITY = 0.08;
    private static final double AIR_DRAG = 0.98;
    private static final double GROUND_DRAG = 0.999;
    private static final double REACH_RADIUS = 3.0;

    public enum Mode { Box, Outline, Both }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Outline);
    public final BooleanProperty airPath = new BooleanProperty("Predict Air Path", true);
    public final DoubleProperty airTicks = new DoubleProperty("Max Air Pred. Ticks", new DoubleValue(1, 500, 100, 1));
    public final BooleanProperty groundPath = new BooleanProperty("Predict Ground Path", true);
    public final DoubleProperty groundTicks = new DoubleProperty("Ground Pred. Ticks", new DoubleValue(1, 15, 6, 1));
    public final BooleanProperty rangeCircles = new BooleanProperty("Range Circles", true);
    public final DoubleProperty circleAlpha = new DoubleProperty("Circle Alpha", new DoubleValue(0, 255, 255, 1));

    public MacePrediction() {
        airTicks.setVisible(airPath::getValue);
        groundTicks.setVisible(groundPath::getValue);
        circleAlpha.setVisible(rangeCircles::getValue);
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> onRender = event -> {
        if (mc.level == null)
            return;
        Player target = nearestTarget();
        if (target == null)
            return;

        if (heightAboveGround(target) < 0.3) {
            if (!groundPath.getValue())
                return;
            Vec3 pos = target.position();
            if (rangeCircles.getValue())
                drawRangeCircle(pos);
            Vec3 motion = new Vec3(pos.x - target.xo, 0, pos.z - target.zo);
            simulateGround(target.level(), pos, motion);
        } else {
            if (!airPath.getValue())
                return;
            Vec3 landing = simulateAir(target);
            if (landing != null && rangeCircles.getValue())
                drawRangeCircle(landing);
        }
    };

    /** The closest living player who is not you, a friend or a bot. */
    private Player nearestTarget() {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || AntiBot.isBot(p)
                    || Arsenic.getArsenic().getFriendManager().isFriend(p))
                continue;
            double d = mc.player.distanceToSqr(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private double heightAboveGround(Player p) {
        Vec3 from = p.position();
        BlockHitResult hit = mc.level.clip(new ClipContext(from, from.add(0, -256, 0),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? p.getY() - hit.getLocation().y : 256.0;
    }

    /** Steps the target's fall tick by tick with vanilla gravity and drag, drawing the arc; returns where it lands. */
    private Vec3 simulateAir(Player target) {
        Level level = target.level();
        Vec3 pos = target.position();
        Vec3 vel = new Vec3(pos.x - target.xo, pos.y - target.yo, pos.z - target.zo);
        if (vel.lengthSqr() < 1.0E-6)
            vel = target.getDeltaMovement();
        Vec3 prev = pos;
        Vec3 lastDrawn = null;
        int path = ThemeManager.getMainColor();
        int ticks = (int) airTicks.getValue().getInput();

        for (int i = 0; i < ticks; i++) {
            prev = pos;
            pos = pos.add(vel);
            BlockPos below = BlockPos.containing(pos.x, pos.y - 0.1, pos.z);
            if (!level.getBlockState(below).isAir() || pos.y <= level.getMinY()) {
                drawMarker(prev, ThemeManager.getGradientColor());
                return new Vec3(prev.x, below.getY() + 1, prev.z);
            }
            if (lastDrawn == null || lastDrawn.distanceToSqr(pos) >= 0.1) {
                if (lastDrawn != null)
                    RenderUtils.drawLine(lastDrawn, pos, path, 1.5f);
                drawMarker(pos, path);
                lastDrawn = pos;
            }
            vel = vel.subtract(0, GRAVITY, 0).scale(AIR_DRAG);
        }
        return prev;
    }

    /** Projects the target's horizontal slide forwards while there is still ground under it. */
    private void simulateGround(Level level, Vec3 start, Vec3 motion) {
        Vec3 pos = start;
        Vec3 lastDrawn = null;
        int colour = ThemeManager.getDarkerColor();
        int ticks = (int) groundTicks.getValue().getInput();

        for (int i = 0; i < ticks; i++) {
            pos = pos.add(motion.x, 0, motion.z);
            if (level.getBlockState(BlockPos.containing(pos.x, pos.y - 0.1, pos.z)).isAir())
                break;
            motion = motion.multiply(GROUND_DRAG, 1, GROUND_DRAG);
            if (Math.abs(motion.x) < 0.005 && Math.abs(motion.z) < 0.005)
                break;
            if (lastDrawn == null || lastDrawn.distanceToSqr(pos) >= 0.1) {
                drawMarker(pos, colour);
                lastDrawn = pos;
            }
        }
    }

    private void drawMarker(Vec3 p, int colour) {
        AABB box = new AABB(p.x - 0.25, p.y, p.z - 0.25, p.x + 0.25, p.y + 2, p.z + 0.25);
        switch (mode.getValue()) {
            case Box -> RenderUtils.renderBox(box, RenderUtils.withAlpha(colour, 150), false, true);
            case Outline -> RenderUtils.renderBox(box, colour, true, false);
            case Both -> RenderUtils.renderBox(box, RenderUtils.withAlpha(colour, 150), true, true);
        }
    }

    /** The reach ring: a faint filled disc in the theme colour edged with a solid ring. */
    private void drawRangeCircle(Vec3 centre) {
        double a = circleAlpha.getValue().getInput() / 255.0;
        if (a <= 0)
            return;
        Vec3 c = centre.add(0, 0.02, 0);
        RenderUtils.drawFilledCircle(c, REACH_RADIUS,
                RenderUtils.withAlpha(ThemeManager.getDarkerColor(), (int) (255 * 0.2 * a)));
        RenderUtils.drawFlatCircle(c, REACH_RADIUS,
                RenderUtils.withAlpha(ThemeManager.getMainColor(), (int) (255 * 0.8 * a)), 2f);
    }
}
