package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

/**
 * Decides <b>when</b> your hits are allowed to land, rather than which target to hit.
 * <p>
 * The decision is made at the source of the attack via {@code MixinPlayerControllerMp}: when a hit
 * is held back the whole attack is cancelled before {@code PlayerControllerMP#attackEntity} queues
 * its {@code C02PacketUseEntity} and before the client runs its attack logic. Cancelling the packet
 * <i>after</i> the client has already attacked is what desyncs and gets caught by simulation
 * anticheats (Grim); cancelling at the source leaves client and server perfectly consistent - from
 * the server's point of view you simply did not attack that tick. A held hit also suppresses the
 * arm swing (via {@code MixinEntityPlayerSP#swingItem}), so there is no animation or swing packet
 * either.
 * <ul>
 *     <li>{@link Mode#WaitForFirstHit} - don't throw the opening hit; wait until the opponent has
 *     hit you (or a grace window elapses) before your attacks are allowed.</li>
 *     <li>{@link Mode#HitLaterInTrades} - when both players are mid-trade, delay your hit slightly
 *     so you land last and win the trade.</li>
 *     <li>{@link Mode#HurtTime} - don't throw the opening hit; once you have been hit, hold your
 *     attacks until your own {@code hurtTime} has decayed to the configured value, then hit.</li>
 * </ul>
 */
@ModuleInfo(name = "HitSelect", category = ModuleCategory.GHOST)
public class HitSelect extends Module {

    public enum Mode {
        WaitForFirstHit,
        HitLaterInTrades,
        HurtTime
    }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.WaitForFirstHit);

    @PropertyInfo(reliesOn = "Mode", value = "WaitForFirstHit")
    public final DoubleProperty maxWait = new DoubleProperty("Max Wait (ms)", new DoubleValue(0, 2000, 800, 50));

    @PropertyInfo(reliesOn = "Mode", value = "HitLaterInTrades")
    public final DoubleProperty tradeDelay = new DoubleProperty("Trade Delay (ms)", new DoubleValue(0, 1000, 150, 10));

    // hurtTime counts down from 10 (the tick you were hit) to 0. A lower value means you hold your
    // retaliation longer; a higher value hits sooner after taking a hit.
    @PropertyInfo(reliesOn = "Mode", value = "HurtTime")
    public final DoubleProperty hurtTime = new DoubleProperty("Hurt Time", new DoubleValue(0, 10, 9, 1));

    /** A fight is considered over once no attack has been attempted for this long. */
    private static final long COMBAT_RESET_MS = 2500L;

    private EntityLivingBase target;
    private boolean beenHit;      // have we taken a hit this fight? (WaitForFirstHit)
    private boolean inTradePrev;  // were we mid-trade last tick? (HitLaterInTrades)

    private final MSTimer fightTimer = new MSTimer();   // time since the current fight started
    private final MSTimer tradeTimer = new MSTimer();   // time since the current trade started
    private final MSTimer combatTimer = new MSTimer();  // time since the last attack attempt

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

    // Continuously track combat state so the attack hook only has to read flags.
    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Pre> onUpdate = event -> {
        if (mc.thePlayer.hurtTime > 0)
            beenHit = true;

        // Fight ended (target dead / ran off): forget everything for the next engagement.
        if (combatTimer.hasTimeElapsed(COMBAT_RESET_MS))
            resetFight();

        // Detect the start of a trade so the delay is measured from the right moment.
        boolean inTrade = target != null && mc.thePlayer.hurtTime > 0 && target.hurtTime > 0;
        if (inTrade && !inTradePrev)
            tradeTimer.reset();
        inTradePrev = inTrade;
    };

    /**
     * Called from {@code MixinPlayerControllerMp} at the head of {@code attackEntity}. Returns
     * {@code true} to swallow the attack entirely (no packet, no client-side attack).
     */
    public boolean shouldBlock(Entity entity) {
        if (!isEnabled())
            return false;

        // Only gate hits against living entities; interactions on anything else pass through.
        if (!(entity instanceof EntityLivingBase))
            return false;

        // A gap since the last attack means this is a fresh fight.
        if (combatTimer.hasTimeElapsed(COMBAT_RESET_MS))
            resetFight();

        target = (EntityLivingBase) entity;
        boolean block = !shouldAllow();
        combatTimer.reset();
        return block;
    }

    private boolean shouldAllow() {
        switch (mode.getValue()) {
            case WaitForFirstHit:
                return beenHit || fightTimer.hasTimeElapsed((long) maxWait.getValue().getInput());

            case HitLaterInTrades:
                boolean inTrade = mc.thePlayer.hurtTime > 0 && target.hurtTime > 0;
                return !inTrade || tradeTimer.hasTimeElapsed((long) tradeDelay.getValue().getInput());

            case HurtTime:
                // Hold the opening hit, then only allow once our hurtTime has decayed to the target.
                return beenHit && mc.thePlayer.hurtTime <= (int) hurtTime.getValue().getInput();
        }
        return true;
    }
}
