package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventLiving;
import arsenic.event.impl.EventPacket;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C02PacketUseEntity;

@ModuleInfo(name = "HitSelect", category = ModuleCategory.GHOST)
public class HitSelect extends Module {

    public enum Mode {
        Burst,
        WaitForFirstHit,
        HitLaterInTrades,
        Criticals
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Burst);
    public final DoubleProperty pauseDuration = new DoubleProperty("Pause Duration", new DoubleValue(0, 1000, 500, 10));
    public final DoubleProperty cancelRate = new DoubleProperty("Cancel Rate", new DoubleValue(0, 100, 0, 1));
    public final DoubleProperty inCombatRate = new DoubleProperty("In Combat", new DoubleValue(0, 100, 0, 1));
    public final DoubleProperty missedSwingRate = new DoubleProperty("Missed Swings", new DoubleValue(0, 100, 0, 1));
    public final BooleanProperty fakeSwing = new BooleanProperty("Fake Swing", true);
    @PropertyInfo(reliesOn = "Mode", value = "Criticals")
    public final BooleanProperty disableDuringKB = new BooleanProperty("Disable During KB", false);
    @PropertyInfo(reliesOn = "Mode", value = "Criticals")
    public final BooleanProperty onlyWhileDamaged = new BooleanProperty("Only While Damaged", false);
    public final BooleanProperty useServerAttackTime = new BooleanProperty("Use Server Attack Time", false);

    private EntityLivingBase target;
    private final MSTimer pauseTimer = new MSTimer();
    private final MSTimer tradeTimer = new MSTimer();
    private boolean wasHitSinceEnable;
    private boolean firstTradeHit;

    @Override
    protected void onEnable() {
        target = null;
        wasHitSinceEnable = false;
        firstTradeHit = false;
    }

    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        if (event.getTarget() instanceof EntityLivingBase) {
            target = (EntityLivingBase) event.getTarget();
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> onLiving = event -> {
        if (mc.thePlayer.hurtTime > 0) {
            wasHitSinceEnable = true;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.OutGoing> onPacket = event -> {
        if (!(event.getPacket() instanceof C02PacketUseEntity)) return;

        C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();

        if (shouldCancel(packet)) {
            event.cancel();
        }
    };

    private boolean shouldCancel(C02PacketUseEntity packet) {
        if (packet.getAction() != C02PacketUseEntity.Action.ATTACK) return false;

        boolean hasTarget = packet.getEntityFromWorld(mc.theWorld) instanceof EntityLivingBase;

        if (hasTarget) {
            EntityLivingBase entity = (EntityLivingBase) packet.getEntityFromWorld(mc.theWorld);
            if (entity != null) target = entity;
        }

        if (!isModeConditionMet(hasTarget)) return true;

        return shouldApplyCancelRate(hasTarget);
    }

    private boolean isModeConditionMet(boolean hasTarget) {
        switch (mode.getValue()) {
            case Burst:
                return isBurstConditionMet(hasTarget);
            case WaitForFirstHit:
                return isWaitForFirstHitConditionMet(hasTarget);
            case HitLaterInTrades:
                return isHitLaterConditionMet(hasTarget);
            case Criticals:
                return isCriticalsConditionMet(hasTarget);
        }
        return true;
    }

    private boolean isBurstConditionMet(boolean hasTarget) {
        if (!hasTarget) return true;
        return target == null || target.hurtTime <= 0;
    }

    private boolean isWaitForFirstHitConditionMet(boolean hasTarget) {
        if (!hasTarget) return true;

        if (!wasHitSinceEnable && !pauseTimer.hasTimeElapsed((long) pauseDuration.getValue().getInput())) {
            return false;
        }

        if (!pauseTimer.hasTimeElapsed((long) pauseDuration.getValue().getInput())) {
            pauseTimer.reset();
        }

        return isBurstConditionMet(hasTarget);
    }

    private boolean isHitLaterConditionMet(boolean hasTarget) {
        if (!hasTarget) return true;
        if (target == null) return true;

        boolean inTrade = mc.thePlayer.hurtTime > 0 && target.hurtTime > 0;

        if (!inTrade) {
            firstTradeHit = false;
            tradeTimer.reset();
            return isBurstConditionMet(hasTarget);
        }

        if (inTrade && useServerAttackTime.getValue()) {
            if (!tradeTimer.hasTimeElapsed((long) pauseDuration.getValue().getInput())) {
                return false;
            }
            tradeTimer.reset();
            return isBurstConditionMet(hasTarget);
        }

        if (!firstTradeHit) {
            firstTradeHit = true;
            tradeTimer.reset();
            return isBurstConditionMet(hasTarget);
        }

        if (!tradeTimer.hasTimeElapsed((long) pauseDuration.getValue().getInput())) {
            return false;
        }

        tradeTimer.reset();
        return isBurstConditionMet(hasTarget);
    }

    private boolean isCriticalsConditionMet(boolean hasTarget) {
        if (!hasTarget) return true;

        if (disableDuringKB.getValue() && mc.thePlayer.hurtTime > 0) {
            return false;
        }

        if (onlyWhileDamaged.getValue() && mc.thePlayer.hurtTime <= 0) {
            return false;
        }

        if (mc.thePlayer.onGround) {
            return isBurstConditionMet(hasTarget);
        }

        if (mc.thePlayer.motionY >= 0) {
            return false;
        }

        return isBurstConditionMet(hasTarget);
    }

    private boolean shouldApplyCancelRate(boolean hasTarget) {
        double rate;

        if (!hasTarget) {
            rate = missedSwingRate.getValue().getInput();
        } else if (isInCombat()) {
            rate = inCombatRate.getValue().getInput();
        } else {
            rate = cancelRate.getValue().getInput();
        }

        return Math.random() * 100 < rate;
    }

    private boolean isInCombat() {
        return target != null && (mc.thePlayer.hurtTime > 0 || target.hurtTime > 0);
    }

    public boolean shouldFakeSwing() {
        return isEnabled() && fakeSwing.getValue();
    }
}
