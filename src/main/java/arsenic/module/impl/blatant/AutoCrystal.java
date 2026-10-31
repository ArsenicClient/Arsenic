package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * End crystal PvP: breaks a crystal that would hurt the nearest player enough without hurting you much, and otherwise
 * places one on obsidian or bedrock where it would. Damage is the vanilla explosion formula with armour as a flat
 * reduction, so it is close but not exact. The aim is silent, and the action goes out only once the rotation being
 * sent points at it.
 */
@ModuleInfo(name = "AutoCrystal", description = "Places and breaks end crystals around the nearest player", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AutoCrystal extends Module {

    private static final double ENEMY_SEARCH = 10;
    private static final float AIM_TOLERANCE = 6f;

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(3, 6, 4.5, 0.5));
    public final DoubleProperty minDamage = new DoubleProperty("Min Damage", new DoubleValue(1, 20, 5, 0.5));
    public final DoubleProperty maxSelfDamage = new DoubleProperty("Max Self Damage", new DoubleValue(1, 20, 6, 0.5));
    public final DoubleProperty delay = new DoubleProperty("Delay (ticks)", new DoubleValue(0, 10, 2, 1));
    public final BooleanProperty place = new BooleanProperty("Place", true);
    public final BooleanProperty breakCrystals = new BooleanProperty("Break", true);

    private final HotbarUse swap = new HotbarUse();
    private int cooldown;
    private EndCrystal breakTarget;
    private BlockHitResult placeHit;
    private Vec3 aim;

    @Override
    public boolean isSwappingHotbar() {
        return swap.active();
    }

    @Override
    protected void onDisable() {
        swap.release();
        clearPlan();
    }

    private void clearPlan() {
        breakTarget = null;
        placeHit = null;
        aim = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        swap.tick();
        clearPlan();
        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (mc.gui.screen() != null || mc.player.isUsingItem())
            return;
        Player enemy = nearestEnemy();
        if (enemy == null)
            return;
        if (breakCrystals.getValue())
            planBreak(enemy);
        if (aim == null && place.getValue() && crystalHand() != null)
            planPlace(enemy);
        if (aim == null)
            return;
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(180f);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (aim == null)
            return;
        float[] want = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), aim);
        if (Math.abs(RotationUtils.getYawDifference(event.getYaw(), want[0])) > AIM_TOLERANCE
                || Math.abs(event.getPitch() - want[1]) > AIM_TOLERANCE)
            return;
        if (breakTarget != null) {
            mc.gameMode.attack(mc.player, breakTarget);
            PlayerUtils.swingItem();
        } else if (placeHit != null) {
            placeCrystal();
        }
        cooldown = (int) delay.getValue().getInput();
        clearPlan();
    };

    private void placeCrystal() {
        InteractionHand hand = crystalHand();
        if (hand == null)
            return;
        if (hand == InteractionHand.MAIN_HAND) {
            if (!mc.player.getMainHandItem().is(Items.END_CRYSTAL))
                swap.switchOnce(HotbarUse.find(s -> s.is(Items.END_CRYSTAL)));
        }
        mc.gameMode.useItemOn(mc.player, hand, placeHit);
        PlayerUtils.swingItem();
    }

    /** The hand a crystal can be placed from (offhand first, so the hotbar stays put), or null. */
    private static InteractionHand crystalHand() {
        if (mc.player.getOffhandItem().is(Items.END_CRYSTAL))
            return InteractionHand.OFF_HAND;
        return HotbarUse.find(s -> s.is(Items.END_CRYSTAL)) != -1 ? InteractionHand.MAIN_HAND : null;
    }

    private void planBreak(Player enemy) {
        double reach = range.getValue().getInput();
        Vec3 eyes = mc.player.getEyePosition(1f);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Entity e : mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(reach + 1), x -> x instanceof EndCrystal)) {
            Vec3 centre = e.position().add(0, 0.5, 0);
            if (eyes.distanceTo(centre) > reach)
                continue;
            double score = score(e.position(), enemy);
            if (score > bestScore) {
                bestScore = score;
                breakTarget = (EndCrystal) e;
                aim = centre;
            }
        }
    }

    private void planPlace(Player enemy) {
        double reach = range.getValue().getInput();
        Vec3 eyes = mc.player.getEyePosition(1f);
        int r = (int) Math.ceil(reach);
        BlockPos origin = BlockPos.containing(eyes);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    var state = mc.level.getBlockState(pos);
                    if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK))
                        continue;
                    Vec3 top = new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
                    if (eyes.y <= top.y || eyes.distanceTo(top) > reach
                            || !mc.level.getBlockState(pos.above()).isAir() || !mc.level.getBlockState(pos.above(2)).isAir()
                            || !mc.level.getEntities((Entity) null, new AABB(pos.above()), x -> !x.isSpectator()).isEmpty())
                        continue;
                    BlockHitResult hit = mc.level.clip(new ClipContext(eyes, top, ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE, mc.player));
                    if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos) || hit.getDirection() != Direction.UP)
                        continue;
                    double score = score(top, enemy);
                    if (score > bestScore) {
                        bestScore = score;
                        placeHit = hit;
                        aim = hit.getLocation();
                    }
                }
            }
        }
    }

    /** Damage to the enemy less damage to us, or negative infinity when the crystal is not worth it. */
    private double score(Vec3 crystal, Player enemy) {
        double self = damage(crystal, mc.player);
        double target = damage(crystal, enemy);
        if (target < minDamage.getValue().getInput() || self > maxSelfDamage.getValue().getInput()
                || self >= mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1)
            return Double.NEGATIVE_INFINITY;
        return target - self;
    }

    private Player nearestEnemy() {
        Player best = null;
        for (Player p : mc.level.players()) {
            if (!TargetManager.isValidTarget(p) || mc.player.distanceTo(p) > ENEMY_SEARCH)
                continue;
            if (best == null || mc.player.distanceTo(p) < mc.player.distanceTo(best))
                best = p;
        }
        return best;
    }

    /** Vanilla explosion damage (power 6) from a crystal at this point, with armour and difficulty roughly applied. */
    private static double damage(Vec3 crystal, LivingEntity e) {
        double d = Math.sqrt(e.distanceToSqr(crystal)) / 12.0;
        if (d > 1)
            return 0;
        double impact = (1 - d) * exposure(crystal, e);
        double dmg = (impact * impact + impact) / 2 * 7 * 12 + 1;
        Difficulty difficulty = mc.level.getDifficulty();
        if (difficulty == Difficulty.PEACEFUL)
            return 0;
        if (difficulty == Difficulty.EASY)
            dmg = Math.min(dmg / 2 + 1, dmg);
        else if (difficulty == Difficulty.HARD)
            dmg *= 1.5;
        return dmg * (1 - Math.min(20, e.getArmorValue()) / 25.0);
    }

    /** The share of the entity's box corners and centre the crystal can see. */
    private static double exposure(Vec3 from, LivingEntity e) {
        AABB box = e.getBoundingBox();
        double[] xs = {box.minX, box.maxX}, ys = {box.minY, box.maxY}, zs = {box.minZ, box.maxZ};
        int seen = 0, total = 0;
        for (double x : xs) {
            for (double y : ys) {
                for (double z : zs) {
                    total++;
                    if (clear(from, new Vec3(x, y, z), e))
                        seen++;
                }
            }
        }
        total++;
        if (clear(from, box.getCenter(), e))
            seen++;
        return seen / (double) total;
    }

    private static boolean clear(Vec3 a, Vec3 b, LivingEntity e) {
        return mc.level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e))
                .getType() == HitResult.Type.MISS;
    }
}
