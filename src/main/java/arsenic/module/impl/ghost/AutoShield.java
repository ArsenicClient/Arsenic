package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.io.Keys;
import arsenic.utils.minecraft.ItemUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Raises the shield while a player close to you is swinging at you or has a weapon pointed at you, and lowers it when
 * the threat is gone. It stays out of the way while you attack or KillAura has a target. Smart mode times the block
 * to the nearest attacker's sword cooldown, drops it for your own crits and never blocks an axe.
 */
@ModuleInfo(name = "AutoShield", description = "Raises your shield when someone attacks you", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class AutoShield extends Module {

    public enum Mode { Simple, Smart }

    /** How long the server keeps a shield disabled after an axe hit. */
    private static final long DISABLE_LOCKOUT_MS = 5000;
    /** No blocking this long after our own attack, so use and attack never share a tick. */
    private static final long POST_ATTACK_MS = 100;

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(2, 6, 4, 0.5));
    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Simple);
    @ShowWhen(property = "Mode", is = "Smart")
    public final DoubleProperty fov = new DoubleProperty("FOV", new DoubleValue(0, 180, 90, 5));
    @ShowWhen(property = "Mode", is = "Smart")
    public final DoubleProperty movementThreshold = new DoubleProperty("Movement Threshold", new DoubleValue(0, 10, 0.1, 0.1));
    @ShowWhen(property = "Mode", is = "Smart")
    public final DoubleProperty cooldownCorrection = new DoubleProperty("Sword Cooldown Correction", new DoubleValue(0, 30, 10, 1));
    public final BooleanProperty disableTracker = new BooleanProperty("Disable Tracker", true);
    @ShowWhen(property = "Disable Tracker", is = "true")
    public final BooleanProperty swapToSword = new BooleanProperty("Swap To Sword", false);

    private boolean holding;
    private boolean shieldWasCooling;
    private long lockoutUntil;
    private int pendingSwap = -1;
    private volatile long lastOwnAttack;
    private volatile int lastSwingerId = Integer.MIN_VALUE;
    private volatile long lastSwingTime;
    private volatile boolean respawned;

    @Override
    public String getHudInfo() {
        return mode.getValue().name().toLowerCase();
    }

    @Override
    protected void onDisable() {
        if (holding)
            release();
        holding = false;
        shieldWasCooling = false;
        lockoutUntil = 0;
        pendingSwap = -1;
        lastSwingerId = Integer.MIN_VALUE;
    }

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onIncoming = event -> {
        if (event.getPacket() instanceof ClientboundSwingAnimationPacket swing) {
            if (swing.hand() == InteractionHand.MAIN_HAND) {
                lastSwingerId = swing.entityId();
                lastSwingTime = System.currentTimeMillis();
            }
        } else if (event.getPacket() instanceof ClientboundRespawnPacket) {
            respawned = true;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> onOutgoing = event -> {
        if (event.getPacket() instanceof ServerboundAttackPacket)
            lastOwnAttack = System.currentTimeMillis();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        long now = System.currentTimeMillis();
        if (respawned) {
            respawned = false;
            lockoutUntil = 0;
            pendingSwap = -1;
        }

        trackDisable(now);

        // the slot changes only once the shield is down, never while still using it (Grim PacketOrder)
        if (pendingSwap >= 0 && !holding && !mc.player.isUsingItem()) {
            if (mc.player.getInventory().getSelectedSlot() != pendingSwap)
                mc.player.getInventory().setSelectedSlot(pendingSwap);
            pendingSwap = -1;
        }

        boolean want = mc.gui.screen() == null && now >= lockoutUntil && shieldReady() && !auraBusy()
                && (mode.getValue() == Mode.Smart ? smartWants(now) : !mc.options.keyAttack.isDown() && threatened());
        if (want) {
            mc.options.keyUse.setDown(true);
            holding = true;
        } else if (holding) {
            release();
        }
    };

    private void release() {
        holding = false;
        mc.options.keyUse.setDown(mc.gui.screen() == null && Keys.isMouseDown(1));
    }

    /** A held shield going on cooldown means an axe just disabled it: let go for the lockout and maybe pull a sword. */
    private void trackDisable(long now) {
        ItemStack shield = heldShield();
        boolean cooling = shield != null && mc.player.getCooldowns().isOnCooldown(shield);
        if (cooling && !shieldWasCooling && disableTracker.getValue()) {
            lockoutUntil = now + DISABLE_LOCKOUT_MS;
            if (holding)
                release();
            if (swapToSword.getValue()) {
                for (int i = 0; i < 9; i++) {
                    if (ItemUtils.isSword(mc.player.getInventory().getItem(i))) {
                        pendingSwap = i;
                        break;
                    }
                }
            }
        }
        shieldWasCooling = cooling;
    }

    private ItemStack heldShield() {
        ItemStack main = mc.player.getMainHandItem();
        if (main.is(Items.SHIELD))
            return main;
        ItemStack off = mc.player.getOffhandItem();
        return off.is(Items.SHIELD) ? off : null;
    }

    /** A shield in either hand that is not on cooldown (an axe disables it for a while). */
    private boolean shieldReady() {
        ItemStack main = mc.player.getMainHandItem();
        ItemStack off = mc.player.getOffhandItem();
        return (main.is(Items.SHIELD) && !mc.player.getCooldowns().isOnCooldown(main))
                || (off.is(Items.SHIELD) && !mc.player.getCooldowns().isOnCooldown(off) && !usableInMainHand(main));
    }

    /** Right click would use the main hand item instead of raising an offhand shield. */
    private static boolean usableInMainHand(ItemStack stack) {
        return stack.has(DataComponents.FOOD) || stack.has(DataComponents.CONSUMABLE)
                || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION) || stack.is(Items.SNOWBALL)
                || stack.is(Items.EGG) || stack.is(Items.ENDER_PEARL) || stack.is(Items.EXPERIENCE_BOTTLE)
                || stack.is(Items.WIND_CHARGE);
    }

    private boolean auraBusy() {
        KillAura aura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        return aura != null && aura.isEnabled() && aura.target != null;
    }

    private boolean validThreat(Player p) {
        return p != mc.player && p.isAlive() && !AntiBot.isBot(p)
                && !Arsenic.getArsenic().getFriendManager().isFriend(p);
    }

    private boolean threatened() {
        double max = range.getValue().getInput();
        Vec3 eyes = mc.player.getEyePosition(1f);
        for (Player p : mc.level.players()) {
            if (!validThreat(p))
                continue;
            double dist = mc.player.distanceTo(p);
            if (dist > max)
                continue;
            if (p.isSwinging())
                return true;
            // a weapon held up and looking at us from striking distance
            if (dist <= 3.5 && ItemUtils.isWeapon(p.getMainHandItem())) {
                Vec3 toUs = eyes.subtract(p.getEyePosition(1f)).normalize();
                if (p.getViewVector(1f).dot(toUs) > 0.9)
                    return true;
            }
        }
        return false;
    }

    /** Blocks between the nearest attacker's sword hits and lets go for our own crits and against axes. */
    private boolean smartWants(long now) {
        if (now - lastOwnAttack < POST_ATTACK_MS)
            return false;
        Player target = nearestInFov();
        if (target == null || mc.player.distanceTo(target) > range.getValue().getInput())
            return false;
        if (mc.player.isFallFlying())
            return false;
        ItemStack weapon = target.getMainHandItem();
        // an axe would just disable the shield, and something that is not a melee weapon is no threat
        if (ItemUtils.isAxe(weapon) || !(ItemUtils.isSword(weapon) || ItemUtils.isMace(weapon) || ItemUtils.isSpear(weapon)))
            return false;

        boolean ownReady = mc.player.getAttackStrengthScale(0f) > 0.94f;
        // falling with a charged swing: drop the shield so the crit can land
        if (!mc.player.onGround() && mc.player.fallDistance > 0 && ownReady)
            return false;
        if (mc.options.keyAttack.isDown() && ownReady)
            return false;

        long rechargeMs = (long) (625.0 * (0.94 - cooldownCorrection.getValue().getInput() / 100.0));
        boolean targetCharged = lastSwingerId != target.getId() || now - lastSwingTime > rechargeMs;
        if (targetCharged && (approaching(target) || mc.player.distanceTo(target) <= 3.0))
            return true;
        // rising from a jump: nothing to crit with yet, so stay covered
        return !mc.player.onGround() && mc.player.fallDistance <= 0;
    }

    private Player nearestInFov() {
        double maxAngle = fov.getValue().getInput();
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 look = mc.player.getViewVector(1f);
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        for (Player p : mc.level.players()) {
            if (!validThreat(p))
                continue;
            double d = mc.player.distanceToSqr(p);
            if (d >= bestDist)
                continue;
            Vec3 to = p.getEyePosition(1f).subtract(eyes).normalize();
            double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, look.dot(to)))));
            if (angle > maxAngle)
                continue;
            best = p;
            bestDist = d;
        }
        return best;
    }

    /** The target moved towards us this tick faster than the threshold. */
    private boolean approaching(Player target) {
        double dx = target.getX() - target.xo, dy = target.getY() - target.yo, dz = target.getZ() - target.zo;
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (speed < movementThreshold.getValue().getInput() / 10.0 || speed == 0)
            return false;
        double tx = mc.player.getX() - target.getX(), ty = mc.player.getY() - target.getY(), tz = mc.player.getZ() - target.getZ();
        return dx * tx + dy * ty + dz * tz > 0;
    }
}
