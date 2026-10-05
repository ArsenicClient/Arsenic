package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.timer.MSTimer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

@ModuleInfo(name = "HitSelect", category = ModuleCategory.COMBAT)
public class HitSelect extends Module {

    public enum Mode {
        WaitForFirstHit,
        HitLaterInTrades,
        HurtTime
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.WaitForFirstHit);




    private static final long COMBAT_RESET_MS = 2500L;

    private LivingEntity target;
    private boolean beenHit;
    private boolean inTradePrev;

    private final MSTimer fightTimer = new MSTimer();
    private final MSTimer tradeTimer = new MSTimer();
    private final MSTimer combatTimer = new MSTimer();

    @Override
    public String getHudInfo() {
        return mode.getValue().name().toLowerCase();
    }

    @Override
    protected void onEnable() {
        resetFight();
        combatTimer.reset();
    }

    private void resetFight() {
        target = null;
        beenHit = false;
        inTradePrev = false;
        fightTimer.reset();
        tradeTimer.reset();
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Pre> onUpdate = event -> {
        if (mc.player.hurtTime > 0)
            beenHit = true;

        if (combatTimer.hasTimeElapsed(COMBAT_RESET_MS))
            resetFight();

        boolean inTrade = target != null && mc.player.hurtTime > 0 && target.hurtTime > 0;
        if (inTrade && !inTradePrev)
            tradeTimer.reset();
        inTradePrev = inTrade;
    };

    public boolean shouldBlock(Entity entity) {
        if (!isEnabled())
            return false;

        if (!(entity instanceof LivingEntity))
            return false;

        if (combatTimer.hasTimeElapsed(COMBAT_RESET_MS))
            resetFight();

        target = (LivingEntity) entity;
        boolean block = !shouldAllow();
        combatTimer.reset();
        return block;
    }

    private boolean shouldAllow() {
        switch (mode.getValue()) {
            case WaitForFirstHit:
                return beenHit || fightTimer.hasTimeElapsed((long) 800);

            case HitLaterInTrades:
                boolean inTrade = mc.player.hurtTime > 0 && target.hurtTime > 0;
                return !inTrade || tradeTimer.hasTimeElapsed((long) 150);

            case HurtTime:
                return beenHit && mc.player.hurtTime <= (int) 9;
        }
        return true;
    }
}
