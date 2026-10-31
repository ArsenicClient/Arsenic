package arsenic.module.impl.blatant;

import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.SliderScale;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.event.impl.EventUpdate;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.impl.ghost.Hitflick;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.utils.click.ClickManager;
import arsenic.utils.minecraft.AutoBlocker;
import arsenic.utils.minecraft.BadPacketsManager;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.minecraft.ServerInfo;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.render.TargetFx;
import arsenic.utils.aimcore.TargetPicker;
import arsenic.utils.rotations.AimController;
import arsenic.utils.rotations.SilentRotationManager;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.lag.LagManager;
import arsenic.utils.timer.MSTimer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "KillAura", category = ModuleCategory.COMBAT, tier = ModuleTier.BLATANT)
public class KillAura extends Module {

    public RangeProperty speed = new RangeProperty("speed", new RangeValue(1, 360, 20, 50,1), SliderScale.LOG);
    @PropertyGroup(Group.ADVANCED)
    public RangeProperty returnSpeed = new RangeProperty("Return Speed", new RangeValue(1, 90, 5, 15, 1), SliderScale.LOG);
    public RangeProperty cps = new RangeProperty("CPS", new RangeValue(1, 20, 8, 12, 1));
    public final EnumProperty<AimController.RotationMode> rotationMode = new EnumProperty<>("Rotation Mode", AimController.RotationMode.Lazy);
    @ShowWhen(property = "Rotation Mode", is = "Heuristics")
    @PropertyGroup(Group.ADVANCED)
    public final DoubleProperty missChance = new DoubleProperty("Miss Chance", new DoubleValue(0, 50, 5, 1));
    @ShowWhen(property = "Rotation Mode", is = "Heuristics")
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty pitchBand = new BooleanProperty("Pitch Band", false);
    @ShowWhen(property = "Pitch Band", is = "true")
    @PropertyGroup(Group.ADVANCED)
    public final RangeProperty pitchRange = new RangeProperty("Pitch Range", new RangeValue(-90, 90, 15, 25, 1));
    public final BooleanProperty silentRotations = new BooleanProperty("Silent Rotations", true);
    public final BooleanProperty disableOnFlag = new BooleanProperty("Disable On Flag", true);
    public final BooleanProperty attackCooldown = new BooleanProperty("Attack Cooldown", true);
    @PropertyGroup(Group.ADVANCED)
    public final BooleanProperty weaponOnly = new BooleanProperty("Weapon Only", false);
    @PropertyGroup(Group.VISUAL)
    public final EnumProperty<TargetFx.Style> circleStyle = new EnumProperty<>("Circle", TargetFx.Style.CLASSIC);
    public final EnumProperty<AutoBlocker.Mode> autoBlock = new EnumProperty<>("Auto Block", AutoBlocker.Mode.None);
    public Player target = null;
    private boolean hadTarget = false;
    private boolean wasUsingItem;
    private final AutoBlocker blocker = new AutoBlocker();
    private AutoBlocker.Mode lastBlockMode = AutoBlocker.Mode.None;
    private boolean blockActive;
    private final MSTimer onTargetTimer = new MSTimer();
    private boolean everOnTarget = false;

    private static final double DEFAULT_REACH = 3.0;

    private static final float PREDICTION_TICKS = 1f;
    private static final double CLICK_GRACE_MS = 300;
    private static final double PRE_AIM_RANGE = 4;
    private static final double PRE_SWING_RANGE = 4.5;

    private final AimController aim = new AimController();
    private final TargetPicker picker = new TargetPicker();


    @Override
    public String getHudInfo() {
        return TargetManager.sortMode.getValue().name().toLowerCase();
    }

    @Override
    protected void onEnable() {
        target = null;
        hadTarget = false;
        everOnTarget = false;
        aim.reset();
        ClickManager.get().reset(ClickManager.Client.KILLAURA);
    }

    @Override
    protected void onDisable() {
        if (lastBlockMode == AutoBlocker.Mode.Legit && mc.player != null)
            blocker.tickLegit(null, false);
        blocker.stopPacket();
        AutoBlocker.setRenderBlocking(false);
        lastBlockMode = AutoBlocker.Mode.None;
    }

    /** Runs the selected autoblock for this tick; the result gates the attack in the post listener. */
    private void autoBlockTick() {
        AutoBlocker.Mode mode = autoBlock.getValue();
        if (mode != AutoBlocker.Mode.Legit && lastBlockMode == AutoBlocker.Mode.Legit)
            blocker.tickLegit(null, false);
        if (mode != lastBlockMode && AutoBlocker.isPacketMode(lastBlockMode))
            blocker.stopPacket();
        lastBlockMode = mode;

        blockActive = target != null && PlayerUtils.isPlayerHoldingBlocker() && !autoProjectile().isBusy();
        AutoBlocker.setRenderBlocking(AutoBlocker.isPacketMode(mode) && blockActive
                && mc.player.distanceTo(target) <= attackRange() + 1);
        if (mode == AutoBlocker.Mode.Legit)
            blocker.tickLegit(target, blockActive);
        else if (AutoBlocker.isPacketMode(mode))
            blocker.tickPacket(mode, target, blockActive);
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        aim.setPitchBand(pitchBandActive(), (float) pitchRange.getValue().getMin(), (float) pitchRange.getValue().getMax());
        target = pickTarget();
        autoBlockTick();
        // the packet block is ours: a real click alongside it is a second action in the tick (Grim MultiActions)
        if (target != null && AutoBlocker.isPacketMode(autoBlock.getValue()))
            event.setBlockUserInput(true);
        aim.updateDrift();
        if (target != null && (hitflick().ownsRotation() || autoProjectile().ownsRotation())) {
            hadTarget = true;
            return;
        }
        if (target != null) {
            aim.setMissChance((float) (missChance.getValue().getInput() / 100));
            float[] rots = aim.aimAt(target, PREDICTION_TICKS);
            aim.rotate(event, target, rots, rotationMode.getValue(),
                    (float) speed.getValue().getMin(), (float) speed.getValue().getMax(), flickBudget());
            hadTarget = true;
        } else if (hadTarget) {
            aim.cancelFlick();
            event.setSpeed((float) returnSpeed.getValue().getRandomInRange());
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> eventTickListener = event -> {
        if (target == null && hadTarget && !event.isModified()) {
            hadTarget = false;
        }
        Hitflick hitflick = hitflick();
        boolean flickInProgress = hitflick.ownsRotation();
        if (!silentRotations.getValue() && target != null && !flickInProgress) {
            mc.player.setYRot(event.getYaw());
            mc.player.setXRot(event.getPitch());
        }
        // a packet block is ours and lasts one tick, so it does not hold the next attack back
        boolean usingItem = mc.player.isUsingItem() && !blocker.isBlocking();
        boolean packetBlock = AutoBlocker.isPacketMode(autoBlock.getValue());
        HitResult raytrace = event.getRayTraceEntity();
        Entity hit = raytrace instanceof net.minecraft.world.phys.EntityHitResult entityHit ? entityHit.getEntity() : null;
        if (hit != null && mc.player.getEyePosition(1f).distanceTo(raytrace.getLocation()) > attackRange())
            hit = null;
        if (target != null && hit == target) {
            onTargetTimer.reset();
            everOnTarget = true;
        }
        if (hitflick.attackedThisTick()) {
            resetAttackCycle();
        }
        Player hitPlayer = hit instanceof Player && TargetManager.isValidTarget((Player) hit)
                ? (Player) hit : null;
        // Hypixel Hold lowers the sword a tick early when the next tick's click would hurt the target
        if (packetBlock)
            blocker.releaseBeforeAttack(autoBlock.getValue(), hitPlayer != null
                    && ClickManager.get().remainingMs(ClickManager.Client.KILLAURA) <= 50
                    && blocker.shouldHit(autoBlock.getValue(), hitPlayer, raytrace.getLocation(), 1));
        if ((target != null || hitPlayer != null)
                && !(weaponOnly.getValue() && !PlayerUtils.isPlayerHoldingWeapon())
                && cooldownReady()
                && !Arsenic.getArsenic().getServerInfo().isInGuiServerSide()
                && !flickInProgress
                && ClickManager.get().isDue(ClickManager.Client.KILLAURA)
                && !usingItem && !wasUsingItem
                // the server's view of item use, whatever the client's says (Grim MultiActions)
                && !BadPacketsManager.serverUsingItem()
                && !autoProjectile().isBusy()
                && !((autoBlock.getValue() == AutoBlocker.Mode.Legit || packetBlock)
                && BadPacketsManager.bad(false, false, false, true, false))
                && !(packetBlock && !blocker.canAttack())) {
            if (hitPlayer != null) {
                // Hypixel waits for a hit that would do damage
                if (packetBlock && !blocker.shouldHit(autoBlock.getValue(), hitPlayer, raytrace.getLocation())) {
                } else if (hitflick.isEnabled() && hitflick.shouldFlick() && hitflick.armFlick(hit, event.getYaw())) {
                    resetAttackCycle();
                } else {
                    mc.gameMode.attack(mc.player, hit);
                    PlayerUtils.swingItem();
                    resetAttackCycle();
                    // KillAura attacks once per tick, so the second click of a double goes out right away
                    // (not with a packet block, whose cycle leaves no room for it)
                    if (!packetBlock && ClickManager.get().doubleClickPending(ClickManager.Client.KILLAURA)) {
                        mc.gameMode.attack(mc.player, hit);
                        PlayerUtils.swingItem();
                        resetAttackCycle();
                    }
                    if (packetBlock)
                        blocker.onAttack(autoBlock.getValue(), hit, raytrace.getLocation(), blockActive);
                }
            } else if (hit == null && target != null && shouldSwingAtAir(event)) {
                PlayerUtils.swingItem();
                resetAttackCycle();
            }
        }
        wasUsingItem = usingItem;
    };


    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Post> onPostMotion = event -> {
        if (AutoBlocker.isPacketMode(autoBlock.getValue()))
            blocker.afterMovement();
    };

    /** Drops an attack that would share a tick with a sword release (Grim PacketOrderI). */
    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> onOutgoing = event -> {
        if (blocker.onOutgoing(event.getPacket()) && AutoBlocker.isPacketMode(autoBlock.getValue()))
            event.cancel();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onServerMove = event -> {
        if (disableOnFlag.getValue() && event.getPacket() instanceof ClientboundPlayerPositionPacket)
            setEnabled(false);
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if(target == null)
            return;
        int col = Arsenic.getInstance().getThemeManager().getCurrentTheme().getMainColor();
        RenderUtils.drawRing(target, event.partialTicks, 0.7, col, 255, circleStyle.getValue());
    };

    /** The reach the server gives us (3 blocks unless an attribute changes it). */
    private static double attackRange() {
        return mc.player != null ? mc.player.entityInteractionRange() : DEFAULT_REACH;
    }

    /**
     * Latest-version attacks do reduced damage until the weapon has recharged, so wait for it. Off for servers that
     * have no attack cooldown (1.8 ones), where CPS alone decides.
     */
    private boolean cooldownReady() {
        return !attackCooldown.getValue() || AutoBlocker.isPacketMode(autoBlock.getValue())
                || mc.player.getAttackStrengthScale(0.5f) >= 0.95f;
    }

    private AutoProjectile autoProjectile() {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(AutoProjectile.class);
    }

    private Hitflick hitflick() {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(Hitflick.class);
    }

    private Player pickTarget() {
        if (Arsenic.getArsenic().getServerInfo().isInGuiServerSide()
                || weaponOnly.getValue() && !PlayerUtils.isPlayerHoldingWeapon())
            return null;
        List<Player> candidates = TargetManager.getTargets();
        if (pitchBandActive()) {
            List<Player> reachable = new ArrayList<>(candidates.size());
            for (Player p : candidates) {
                if (aim.inPitchBand(p))
                    reachable.add(p);
            }
            candidates = reachable;
        }
        double aimRange = attackRange() + (candidates.size() == 1 ? PRE_AIM_RANGE : 0);
        picker.valueScale = TargetManager.sortMode.getValue() == TargetManager.SortMode.Fov ? 0.04f : 1f;
        SilentRotationManager srm = Arsenic.getArsenic().getSilentRotationManager();
        Vec3 eyes = mc.player.getEyePosition(1f);
        List<TargetPicker.Candidate> list = new ArrayList<>(candidates.size());
        for (Player p : candidates) {
            AABB box = p.getBoundingBox();
            double dx = (box.minX + box.maxX) / 2 - eyes.x, dz = (box.minZ + box.maxZ) / 2 - eyes.z;
            double dy = Mth.clamp(eyes.y, box.minY, box.maxY) - eyes.y;
            float yaw = RotationUtils.yawTo(dx, dz);
            float pitch = RotationUtils.pitchTo(dx, dy, dz);
            float angle = Math.max(Math.abs(Mth.wrapDegrees(yaw - srm.yaw)), Math.abs(pitch - srm.pitch));
            list.add(new TargetPicker.Candidate(p.getId(), TargetManager.sortValue(p),
                    RotationUtils.getDistanceToEntityBox(p), angle));
        }
        int i = picker.pick(list, target == null ? Integer.MIN_VALUE : target.getId(), aimRange);
        return i < 0 ? null : candidates.get(i);
    }

    private boolean pitchBandActive() {
        return rotationMode.getValue() == AimController.RotationMode.Heuristics && pitchBand.getValue();
    }

    private float flickBudget() {
        long remainingMs = ClickManager.get().remainingMs(ClickManager.Client.KILLAURA);
        return remainingMs / 50f + LagManager.getPingAsTicks() / 2f;
    }

    private void resetAttackCycle() {
        ClickManager.get().onClick(ClickManager.Client.KILLAURA, cps.getValue());
    }

    /**
     * A miss swing at a target that is not under the crosshair. Heuristics mode also swings at a target that is
     * still closing in, just out of reach, the way players start clicking before the first hit lands.
     */
    private boolean shouldSwingAtAir(EventSilentRotation.Post event) {
        double dist = RotationUtils.getDistanceToEntityBox(target);
        if (dist <= attackRange() && shouldMissClick(event))
            return true;
        return rotationMode.getValue() == AimController.RotationMode.Heuristics && dist <= PRE_SWING_RANGE;
    }

    private boolean shouldMissClick(EventSilentRotation.Post event) {
        double graceMs = CLICK_GRACE_MS;
        if (graceMs <= 0)
            return false;
        if (everOnTarget && onTargetTimer.getTime() <= graceMs)
            return true;

        int lookahead = Math.max(1, (int) Math.round(graceMs / 50.0));
        Vec3 eyes = mc.player.getEyePosition(1f);
        Vec3 look = Entity.calculateViewVector(event.getPitch(), event.getYaw());
        Vec3 end = eyes.add(look.x * attackRange(), look.y * attackRange(), look.z * attackRange());
        float border = target.getPickRadius();
        for (int t = 1; t <= lookahead; t++) {
            AABB box = aim.predictBox(target, t).inflate(border, border, border);
            if (box.contains(eyes) || box.clip(eyes, end).isPresent())
                return true;
        }

        float speed = Math.max(event.getSpeed(), 0.01f);
        float[] rots = aim.getPredictedRotations(target, lookahead);
        float yawDelta = Math.abs(RotationUtils.getYawDifference(rots[0], event.getYaw()));
        float pitchDelta = Math.abs(rots[1] - event.getPitch());
        return Math.max(yawDelta, pitchDelta) / speed <= lookahead;
    }

}
