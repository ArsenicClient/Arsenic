package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.io.Keys;
import arsenic.utils.minecraft.BadPacketsManager;
import arsenic.utils.minecraft.HotbarUse;
import arsenic.utils.minecraft.ItemUtils;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Pulls the mace out once you have fallen far enough with a player below you, and hits them with it. Stun Slam opens
 * with an axe on a raised shield first, so the smash that follows is not blocked. It can also take the elytra off for
 * the dive, aim the hit silently and put your old slot back afterwards.
 */
@ModuleInfo(name = "AutoMace", description = "Swaps to the mace mid-fall and smashes the target below", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class AutoMace extends Module {

    /** Players this close (to the hitbox) can be hit. */
    private static final double ATTACK_RANGE = 3.0;
    /** Minimum time between two automatic hits, and between two stun slams. */
    private static final long HIT_COOLDOWN_MS = 800;
    /** A mace pulled out that never hit goes back after this long (with Swap Back). */
    private static final long IDLE_RESTORE_MS = 2000;
    /** Below this fall Breach Check prefers Breach; above it Density does more. */
    private static final double BREACH_FALL = 7.0;
    private static final float SILENT_AIM_SPEED = 180f;

    public enum StunMode { Chance, HoldKey }

    public enum Bind {
        None, Middle, Mouse4, Mouse5, R, F, G, V, X, Z, C, Q, Tab, Left_Alt, Caps_Lock
    }

    private static AutoMace instance;

    public final DoubleProperty targetDistance = new DoubleProperty("Horizontal Target Dist", new DoubleValue(0, 6, 5, 0.1));
    public final DoubleProperty minFall = new DoubleProperty("Min Fall Distance", new DoubleValue(0, 100, 10, 0.5));

    public final BooleanProperty stunSlam = new BooleanProperty("Stun Slam", false);
    @ShowWhen(property = "Stun Slam", is = "true")
    public final EnumProperty<StunMode> stunMode = new EnumProperty<>("Stun Slam Mode", StunMode.Chance);
    @ShowWhen(property = "Stun Slam", is = "true")
    @ShowWhen(property = "Stun Slam Mode", is = "HoldKey")
    public final EnumProperty<Bind> stunBind = new EnumProperty<>("Stun Slam Bind", Bind.None);
    @ShowWhen(property = "Stun Slam", is = "true")
    @ShowWhen(property = "Stun Slam Mode", is = "Chance")
    public final DoubleProperty chance = new DoubleProperty("Chance", new DoubleValue(0, 100, 100, 1));
    @ShowWhen(property = "Stun Slam", is = "true")
    @ShowWhen(property = "Stun Slam Mode", is = "Chance")
    public final BooleanProperty scaleWithFall = new BooleanProperty("Scale With Fall", false);
    @ShowWhen(property = "Stun Slam", is = "true")
    public final DoubleProperty stunMinFall = new DoubleProperty("Stun Min Fall", new DoubleValue(0, 100, 0, 0.5));
    @ShowWhen(property = "Stun Slam", is = "true")
    public final DoubleProperty axeHoldDelay = new DoubleProperty("Axe Hold Delay (ticks)", new DoubleValue(0, 10, 0, 1));

    public final BooleanProperty breachCheck = new BooleanProperty("Breach Check", false);
    public final BooleanProperty trigger = new BooleanProperty("Trigger", true);
    @ShowWhen(property = "Trigger", is = "true")
    public final BooleanProperty silentAim = new BooleanProperty("Silent Aim", false);
    @ShowWhen(property = "Trigger", is = "true")
    @ShowWhen(property = "Silent Aim", is = "true")
    public final DoubleProperty maxYaw = new DoubleProperty("Max Yaw", new DoubleValue(0, 180, 180, 1));
    public final BooleanProperty takeoffElytra = new BooleanProperty("Takeoff Elytra", false);
    public final BooleanProperty swapBack = new BooleanProperty("Swap Back", false);
    @ShowWhen(property = "Swap Back", is = "true")
    public final DoubleProperty swapBackDelay = new DoubleProperty("Swap Back Delay (ms)", new DoubleValue(0, 500, 50, 5));
    public final BooleanProperty expandHitbox = new BooleanProperty("Expand Hitbox", false);
    @ShowWhen(property = "Expand Hitbox", is = "true")
    public final DoubleProperty hitboxExpansion = new DoubleProperty("Hitbox Expansion", new DoubleValue(0, 5, 0.5, 0.1));

    private final HotbarUse hotbar = new HotbarUse();
    private final MSTimer hitTimer = MSTimer.expired();
    private final MSTimer stunTimer = MSTimer.expired();
    private final MSTimer swapBackTimer = new MSTimer();
    private final MSTimer idleTimer = new MSTimer();

    /** Nearest player under us, refreshed each tick; the hitbox expansion only applies to them. */
    private volatile Player target;
    /** Player the silent rotation is turning to this tick. */
    private Player aimTarget;
    /** Stun slam: the shielding player, ticks the axe has been out, and whether the axe hit landed. */
    private Player stunTarget;
    private int axeTicks = -1;
    /** The mace hit landed and the hotbar goes back once Swap Back Delay has passed. */
    private boolean swapBackPending;
    /** Ticks since the main hand last changed; an attack never shares a tick with a slot change. */
    private int heldTicks;
    private int lastSelected = -1;

    public AutoMace() {
        instance = this;
    }

    @Override
    public boolean isSwappingHotbar() {
        return hotbar.active();
    }

    @Override
    public String getHudInfo() {
        return stunSlam.getValue() ? "Stun" : null;
    }

    @Override
    protected void onEnable() {
        resetState();
    }

    @Override
    protected void onDisable() {
        if (mc.player != null && swapBack.getValue())
            hotbar.release();
        else
            forget();
        resetState();
    }

    private void resetState() {
        target = null;
        aimTarget = null;
        stunTarget = null;
        axeTicks = -1;
        swapBackPending = false;
        heldTicks = 0;
        lastSelected = -1;
    }

    /** Extra pick radius for an entity, called from the Entity mixin; 0 unless Expand Hitbox applies to it. */
    public static float extraPickRadius(Entity entity) {
        AutoMace m = instance;
        if (m == null || !m.isEnabled() || !m.expandHitbox.getValue() || mc.player == null)
            return 0f;
        Player t = m.target;
        if (t == null || entity != t || mc.player.fallDistance <= m.minFall.getValue().getInput())
            return 0f;
        // the openprestige expansion is scaled down by five so the slider reads in fine steps
        return (float) (m.hitboxExpansion.getValue().getInput() / 5.0);
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        aimTarget = null;
        if (!trigger.getValue() || !silentAim.getValue() || !canAct() || stunTarget != null)
            return;
        if (mc.player.fallDistance <= minFall.getValue().getInput() || bestMaceSlot() == -1)
            return;
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player)
            return;
        Player p = aimCandidate();
        if (p == null)
            return;
        aimTarget = p;
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), RotationUtils.getBestHitVec(p));
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setSpeed(SILENT_AIM_SPEED);
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> onPost = event -> {
        int selected = mc.player.getInventory().getSelectedSlot();
        if (selected != lastSelected) {
            lastSelected = selected;
            heldTicks = 0;
        } else {
            heldTicks++;
        }

        if (!mc.player.isAlive()) {
            forget();
            resetState();
            return;
        }

        target = nearestBelow();

        // a landed hit: go back to the old slot once the delay has passed
        if (swapBackPending) {
            if (swapBackTimer.hasTimeElapsed((long) swapBackDelay.getValue().getInput())) {
                swapBackPending = false;
                hotbar.release();
            }
            return;
        }

        if (stunTarget != null) {
            stunSlamTick(event);
            return;
        }

        int mace = bestMaceSlot();
        boolean falling = mace != -1 && mc.player.fallDistance > minFall.getValue().getInput();
        if (!falling || target == null || !canAct()) {
            // a mace that was pulled out but never used goes back after a while
            if (hotbar.active() && swapBack.getValue() && idleTimer.hasTimeElapsed(IDLE_RESTORE_MS))
                hotbar.release();
            else if (hotbar.active() && !swapBack.getValue() && (mc.player.onGround() || target == null))
                forget();
            return;
        }

        if (takeoffElytra.getValue() && takeOffElytra())
            return;

        HitResult hit = event.getRayTraceEntity();
        Player aimed = hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof Player p
                && validTarget(p) ? p : null;

        // a raised shield facing us: open with the axe
        if (aimed != null && stunSlam.getValue() && trigger.getValue() && shieldFacing(aimed)
                && stunTimer.hasTimeElapsed(HIT_COOLDOWN_MS) && fallReadyForStun() && stunRoll()) {
            int axe = HotbarUse.find(ItemUtils::isAxe);
            if (axe != -1) {
                stunTarget = aimed;
                axeTicks = 0;
                if (mc.player.getInventory().getSelectedSlot() != axe)
                    selectSlot(axe);
                return;
            }
        }

        if (mc.player.getInventory().getSelectedSlot() != mace) {
            selectSlot(mace);
            return;
        }

        if (!trigger.getValue() || aimed == null || !hitTimer.hasTimeElapsed(HIT_COOLDOWN_MS))
            return;
        if (RotationUtils.getDistanceToEntityBox(aimed) > ATTACK_RANGE)
            return;
        if (!attack(aimed))
            return;
        hitTimer.reset();
        if (swapBack.getValue() && hotbar.active()) {
            swapBackPending = true;
            swapBackTimer.reset();
        }
    };

    /** Axe out: wait the hold delay, hit, then the normal path pulls the mace for the smash. */
    private void stunSlamTick(EventSilentRotation.Post event) {
        Player t = stunTarget;
        if (!t.isAlive() || mc.player.distanceTo(t) > 6.0 || !canAct()) {
            stunTarget = null;
            axeTicks = -1;
            return;
        }
        if (!ItemUtils.isAxe(mc.player.getMainHandItem())) {
            int axe = HotbarUse.find(ItemUtils::isAxe);
            if (axe == -1) {
                stunTarget = null;
                axeTicks = -1;
                return;
            }
            selectSlot(axe);
            return;
        }
        axeTicks++;
        int hold = Math.max(1, (int) axeHoldDelay.getValue().getInput());
        if (axeTicks < hold)
            return;
        HitResult hit = event.getRayTraceEntity();
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() == t
                && RotationUtils.getDistanceToEntityBox(t) <= ATTACK_RANGE) {
            if (!attack(t))
                return;
            stunTimer.reset();
            // let the mace hit follow straight away
            hitTimer.lastMS = 0;
            stunTarget = null;
            axeTicks = -1;
        } else if (axeTicks > hold + 10) {
            stunTarget = null;
            axeTicks = -1;
        }
    }

    /** Attack before swing, never in the tick the slot changed (Grim PacketOrderE). */
    private boolean attack(Player p) {
        if (heldTicks < 1 || BadPacketsManager.serverUsingItem() || BadPacketsManager.bad(true, false, false, false, true))
            return false;
        mc.gameMode.attack(mc.player, p);
        PlayerUtils.swingItem();
        return true;
    }

    private void selectSlot(int slot) {
        if (!hotbar.active())
            idleTimer.reset();
        hotbar.select(slot);
    }

    /** Swaps the elytra for a chestplate when diving at a target, from the hotbar or the inventory. */
    private boolean takeOffElytra() {
        if (!mc.player.isFallFlying() || !mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA))
            return false;
        Player t = target;
        if (t == null || mc.player.distanceTo(t) > (shieldFacing(t) ? 8.0 : 6.0))
            return false;
        int hotbarSlot = HotbarUse.find(s -> s.is(Items.NETHERITE_CHESTPLATE));
        if (hotbarSlot == -1)
            hotbarSlot = HotbarUse.find(s -> s.is(Items.DIAMOND_CHESTPLATE));
        if (hotbarSlot != -1) {
            // equipping swaps the elytra into that hotbar slot
            int before = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(hotbarSlot);
            mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            PlayerUtils.swingItem();
            if (!hotbar.active())
                mc.player.getInventory().setSelectedSlot(before);
            return true;
        }
        int invSlot = inventorySlot(Items.NETHERITE_CHESTPLATE);
        if (invSlot == -1)
            invSlot = inventorySlot(Items.DIAMOND_CHESTPLATE);
        if (invSlot == -1)
            return false;
        int menu = mc.player.inventoryMenu.containerId;
        mc.gameMode.handleContainerInput(menu, invSlot, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(menu, 6, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(menu, invSlot, 0, ContainerInput.PICKUP, mc.player);
        return true;
    }

    /** Inventory menu slot (9-35) holding the item, or -1. */
    private int inventorySlot(net.minecraft.world.item.Item item) {
        for (int i = 9; i <= 35; i++)
            if (mc.player.getInventory().getItem(i).is(item))
                return i;
        return -1;
    }

    /** The hotbar mace with the most Density (or Breach on short falls with Breach Check), or -1. */
    private int bestMaceSlot() {
        boolean breach = breachCheck.getValue() && mc.player.fallDistance <= BREACH_FALL;
        int best = -1, bestLevel = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!ItemUtils.isMace(stack))
                continue;
            int level = ItemUtils.enchantLevel(breach ? Enchantments.BREACH : Enchantments.DENSITY, stack);
            if (level > bestLevel) {
                bestLevel = level;
                best = i;
            }
        }
        return best;
    }

    private boolean fallReadyForStun() {
        return mc.player.fallDistance >= Math.max(minFall.getValue().getInput(), stunMinFall.getValue().getInput());
    }

    private boolean stunRoll() {
        if (stunMode.getValue() == StunMode.HoldKey)
            return stunBind.getValue() != Bind.None && bindDown(stunBind.getValue());
        double c = chance.getValue().getInput();
        if (scaleWithFall.getValue()) {
            double floor = Math.max(minFall.getValue().getInput(), stunMinFall.getValue().getInput());
            c *= Mth.clamp((mc.player.fallDistance - floor) / 10.0, 0.0, 1.0);
        }
        return ThreadLocalRandom.current().nextDouble(100) < c;
    }

    /** Nearest valid player within the horizontal target distance, measured at our own height. */
    private Player nearestBelow() {
        double max = targetDistance.getValue().getInput();
        double maxSq = max * max;
        Player best = null;
        double bestSq = Double.MAX_VALUE;
        for (Player p : mc.level.players()) {
            if (!validTarget(p))
                continue;
            double dx = p.getX() - mc.player.getX(), dz = p.getZ() - mc.player.getZ();
            double sq = dx * dx + dz * dz;
            if (sq <= maxSq && sq < bestSq) {
                bestSq = sq;
                best = p;
            }
        }
        return best;
    }

    /** The target under us if it is in reach, in sight and within Max Yaw of where we look. */
    private Player aimCandidate() {
        Player p = target;
        if (p == null || RotationUtils.getDistanceToEntityBox(p) > ATTACK_RANGE || !mc.player.hasLineOfSight(p))
            return null;
        float[] rots = RotationUtils.rotationsTo(mc.player.getEyePosition(1f), p.getEyePosition(1f));
        if (Math.abs(Mth.wrapDegrees(rots[0] - mc.player.getYRot())) > maxYaw.getValue().getInput())
            return null;
        return p;
    }

    private boolean validTarget(Player p) {
        return p != mc.player && p.isAlive() && !AntiBot.isBot(p)
                && !Arsenic.getArsenic().getFriendManager().isFriend(p);
    }

    private boolean canAct() {
        return mc.gui.screen() == null && !mc.player.isUsingItem();
    }

    /** A raised shield that faces us, so a plain hit would be blocked. */
    private boolean shieldFacing(Player p) {
        if (!p.isUsingItem() || !p.getUseItem().is(Items.SHIELD))
            return false;
        Vec3 toUs = mc.player.position().subtract(p.position()).normalize();
        return p.getViewVector(1f).dot(toUs) >= 0;
    }

    /** Stops tracking the swap and keeps whatever is in hand now. */
    private void forget() {
        if (!hotbar.active())
            return;
        int current = mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
        hotbar.release();
        if (current != -1)
            mc.player.getInventory().setSelectedSlot(current);
    }

    private static boolean bindDown(Bind b) {
        int mask = switch (b) {
            case Middle -> 2;
            case Mouse4 -> 8;
            case Mouse5 -> 16;
            default -> 0;
        };
        if (mask != 0)
            return (org.lwjgl.sdl.SDLMouse.SDL_GetMouseState(null, null) & mask) != 0;
        return Keys.isKeyDown(Keys.getKeyIndex(b.name()));
    }
}
