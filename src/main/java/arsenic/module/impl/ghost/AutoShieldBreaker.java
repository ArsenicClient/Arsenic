package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.notifications.Notification;
import arsenic.notifications.NotificationManager;
import arsenic.notifications.NotificationType;
import arsenic.utils.minecraft.BadPacketsManager;
import arsenic.utils.minecraft.ContainerUtils;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Breaks a raised shield with an axe: it switches to an axe (from the hotbar, or swapped in from the inventory), hits the
 * shielding player once the hit delay has passed and switches back. On Hit mode only holds the axe while you aim at a
 * shielding player and leaves the clicking to you.
 */
@ModuleInfo(name = "AutoShieldBreaker", description = "Switches to an axe and breaks raised shields", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class AutoShieldBreaker extends Module {

    /** Players this close (to the hitbox) can be hit. */
    private static final double ATTACK_RANGE = 3.0;
    /** A pending break gives up this long after its hit delay if it never landed. */
    private static final long PENDING_GRACE_MS = 400;
    private static final float SILENT_AIM_SPEED = 180f;

    public enum Mode {
        /** Holds an axe while you aim at a shielding player; you do the hitting. */
        OnHit,
        /** Switches to an axe and hits the shielding player by itself. */
        Automatic
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Automatic);

    @ShowWhen(property = "Mode", is = "Automatic")
    public final BooleanProperty stun = new BooleanProperty("Stun", false);
    @ShowWhen(property = "Mode", is = "Automatic")
    public final DoubleProperty chance = new DoubleProperty("Chance", new DoubleValue(0, 100, 100, 1));
    @ShowWhen(property = "Mode", is = "Automatic")
    public final BooleanProperty fromInventory = new BooleanProperty("From Inventory", true);
    @ShowWhen(property = "Mode", is = "Automatic")
    public final RangeProperty hitDelay = new RangeProperty("Hit Delay (ms)", new RangeValue(0, 300, 100, 150, 1));
    @ShowWhen(property = "Mode", is = "Automatic")
    public final RangeProperty breakCooldown = new RangeProperty("Break Cooldown (ms)", new RangeValue(0, 3000, 900, 1200, 10));
    public final BooleanProperty swapBack = new BooleanProperty("Swap Back", true);
    @ShowWhen(property = "Mode", is = "Automatic")
    public final BooleanProperty silentAim = new BooleanProperty("Silent Aim", false);
    @ShowWhen(property = "Silent Aim", is = "true")
    public final DoubleProperty maxYaw = new DoubleProperty("Max Yaw", new DoubleValue(0, 180, 180, 1));

    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty notify = new BooleanProperty("Notify", true);

    private final HotbarUse hotbar = new HotbarUse();
    private final MSTimer swapTimer = new MSTimer();
    private final MSTimer breakTimer = MSTimer.expired();

    /** Inventory slot (9-35) the axe came from and the hotbar slot it went to, or -1 when nothing was swapped in. */
    private int invSlot = -1, invHotbarSlot = -1;
    /** An axe is out for a break that has not landed yet. */
    private boolean pending;
    private long rolledHitDelay, rolledCooldown;
    /** The shielding player Silent Aim is turning to this tick. */
    private Player aimTarget;

    @Override
    public boolean isSwappingHotbar() {
        return hotbar.active() || invSlot != -1;
    }

    @Override
    protected void onEnable() {
        pending = false;
        aimTarget = null;
        rollHitDelay();
        rollCooldown();
        breakTimer.lastMS = 0;
    }

    @Override
    protected void onDisable() {
        if (mc.player != null)
            restore();
        else
            forget();
        pending = false;
        aimTarget = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aimTarget = null;
        if (mode.getValue() != Mode.Automatic || !silentAim.getValue() || !canAct())
            return;
        if (!pending && !breakTimer.hasTimeElapsed(rolledCooldown))
            return;
        aimTarget = silentAimTarget();
        if (aimTarget == null)
            return;
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), RotationUtils.getBestHitVec(aimTarget));
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(SILENT_AIM_SPEED);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        if (!mc.player.isAlive()) {
            forget();
            pending = false;
            return;
        }
        if (mode.getValue() == Mode.OnHit) {
            onHit();
            return;
        }

        // a break that never landed stops waiting
        if (pending && swapTimer.hasTimeElapsed(rolledHitDelay + PENDING_GRACE_MS))
            pending = false;

        if (!pending && isSwappingHotbar() && mc.gui.screen() == null && !holdingConsumable()) {
            if (swapBack.getValue())
                restore();
            else
                forget();
        }

        if (!canAct())
            return;
        // the ray the attack would use: the silent rotation when it is aiming, the camera otherwise
        HitResult hit = event.getRayTraceEntity();
        if (!(hit instanceof EntityHitResult entityHit) || !(entityHit.getEntity() instanceof Player target))
            return;
        if (!isShielding(target) || !TargetManager.isValidTarget(target)
                || RotationUtils.getDistanceToEntityBox(target) > ATTACK_RANGE)
            return;
        automatic(target);
    };

    private void automatic(Player target) {
        if (!pending && !breakTimer.hasTimeElapsed(rolledCooldown))
            return;

        if (!ItemUtils.isAxe(mc.player.getMainHandItem())) {
            if (pending)
                return;
            if (ThreadLocalRandom.current().nextDouble(100) >= chance.getValue().getInput()) {
                // missed the roll: wait out another cooldown before trying again
                breakTimer.reset();
                rollCooldown();
                return;
            }
            if (!equipAxe())
                return;
            pending = true;
            swapTimer.reset();
            rollHitDelay();
            return;
        }

        // the axe has to be held since an earlier tick: never change slot and attack together
        if (BadPacketsManager.serverUsingItem() || BadPacketsManager.bad(true, false, false, false, true))
            return;
        if (!swapTimer.hasTimeElapsed(rolledHitDelay))
            return;

        mc.gameMode.attack(mc.player, target);
        PlayerUtils.swingItem();
        if (stun.getValue()) {
            mc.gameMode.attack(mc.player, target);
            PlayerUtils.swingItem();
        }
        if (notify.getValue())
            NotificationManager.show(new Notification(NotificationType.INFO, getName(), "Broke " + target.getName().getString() + "'s shield", 1));

        pending = false;
        breakTimer.reset();
        rollCooldown();
    }

    /** On Hit: hold an axe while the crosshair is on a shielding player, and put the old item back after. */
    private void onHit() {
        boolean want = canAct() && mc.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof Player target && isShielding(target)
                && TargetManager.isValidTarget(target);
        if (want) {
            if (!ItemUtils.isAxe(mc.player.getMainHandItem())) {
                int slot = HotbarUse.find(ItemUtils::isAxe);
                if (slot != -1)
                    hotbar.select(slot);
            }
        } else if (hotbar.active() && mc.gui.screen() == null) {
            if (swapBack.getValue())
                hotbar.release();
            else
                forget();
        }
    }

    /** Puts an axe in the main hand, from the hotbar or swapped in from the inventory. */
    private boolean equipAxe() {
        int slot = HotbarUse.find(ItemUtils::isAxe);
        if (slot != -1) {
            hotbar.select(slot);
            return true;
        }
        if (!fromInventory.getValue())
            return false;
        for (int i = 9; i <= 35; i++) {
            if (ItemUtils.isAxe(mc.player.getInventory().getItem(i))) {
                int selected = mc.player.getInventory().getSelectedSlot();
                ContainerUtils.swap(i, selected);
                invSlot = i;
                invHotbarSlot = selected;
                return true;
            }
        }
        return false;
    }

    private void restore() {
        if (invSlot != -1 && invHotbarSlot != -1 && mc.player != null && mc.gameMode != null)
            ContainerUtils.swap(invSlot, invHotbarSlot);
        invSlot = invHotbarSlot = -1;
        hotbar.release();
    }

    /** Stops tracking a swap without moving anything back. */
    private void forget() {
        invSlot = invHotbarSlot = -1;
        if (hotbar.active()) {
            // keep the current slot: release() would select the old one, so re-select where we are first
            int current = mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
            hotbar.release();
            if (current != -1)
                mc.player.getInventory().setSelectedSlot(current);
        }
    }

    private boolean canAct() {
        return mc.gui.screen() == null && mc.isWindowActive() && mc.player.fallDistance <= 2.0
                && !mc.player.isUsingItem() && !holdingConsumable();
    }

    /** Food, potions and throwables in the main hand: switching away would cut the use or throw short. */
    private boolean holdingConsumable() {
        ItemStack held = mc.player.getMainHandItem();
        return held.get(DataComponents.FOOD) != null || held.is(Items.POTION) || held.is(Items.SPLASH_POTION)
                || held.is(Items.LINGERING_POTION) || held.is(Items.SNOWBALL) || held.is(Items.EGG)
                || held.is(Items.ENDER_PEARL) || held.is(Items.EXPERIENCE_BOTTLE);
    }

    private static boolean isShielding(Player player) {
        return player.isUsingItem() && player.getUseItem().is(Items.SHIELD);
    }

    /** The closest shielding target within Max Yaw of where you look, in reach and in sight. */
    private Player silentAimTarget() {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        float max = (float) maxYaw.getValue().getInput();
        for (Player p : mc.level.players()) {
            if (p == mc.player || !p.isAlive() || !isShielding(p))
                continue;
            double dist = RotationUtils.getDistanceToEntityBox(p);
            if (dist > ATTACK_RANGE || dist >= bestDist || !mc.player.hasLineOfSight(p))
                continue;
            float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), p.getEyePosition(1f));
            if (Math.abs(Mth.wrapDegrees(rots[0] - mc.player.getYRot())) > max)
                continue;
            if (!TargetManager.isValidTarget(p))
                continue;
            best = p;
            bestDist = dist;
        }
        return best;
    }

    private void rollHitDelay() {
        rolledHitDelay = (long) hitDelay.getValue().getRandomInRange();
    }

    private void rollCooldown() {
        rolledCooldown = (long) breakCooldown.getValue().getRandomInRange();
    }
}
