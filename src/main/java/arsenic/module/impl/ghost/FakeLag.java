package arsenic.module.impl.ghost;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.lag.LagManager;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "LagRange", category = ModuleCategory.COMBAT)
public class FakeLag extends Module {

    public final RangeProperty delay = new RangeProperty("Delay", new RangeValue(0, 2000, 100, 200, 10));
    /** Minimum gap after releasing a lag burst before another may start. */
    public final DoubleProperty cooldown = new DoubleProperty("Cooldown (ms)", new DoubleValue(0, 2000, 500, 10));
    private static final int MAX_POSITION_HISTORY = 400;

    private final List<Vec3> positionHistory = new ArrayList<>();

    private boolean lagging;
    private double currentDelay = 0;
    private double targetDelay = 0;
    private long lastReleaseTime = 0;

    private Player closestPlayer;
    private double closestDistance = Double.MAX_VALUE;

    @EventLink
    public final Listener<EventTick> eventTickListener = eventTick -> {
        if (mc.player == null) {
            setEnabled(false);
            reset();
            return;
        }

        recordPosition();
        findClosestPlayer();

        if (closestDistance <= 5) {
            if (lagging)
                stopLag(true);
            return;
        }

        if (closestDistance > 20) {
            if (lagging)
                stopLag(false);
            return;
        }

        if (lagging) {
            if (serverSidedPositionIsCloser())
                stopLag(false);
            else
                buildUp();
            return;
        }

        if (System.currentTimeMillis() - lastReleaseTime < cooldown.getValue().getInput())
            return;

        startLag();
    };

    @EventLink
    public final Listener<EventAttack> eventAttack = event -> {
        if(event.getTarget() instanceof Player)
            stopLag(false);
    };

    /**
     * Reports the delay actually being applied right now, not the configured target - the whole
     * point of the buildup is that the two differ for most of a lag cycle.
     */
    @Override
    public String getHudInfo() {
        if (!lagging)
            return closestPlayer == null ? null : "armed";
        return Math.round(currentDelay) + "ms";
    }

    private void recordPosition() {
        positionHistory.add(0, new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        while (positionHistory.size() > MAX_POSITION_HISTORY)
            positionHistory.remove(positionHistory.size() - 1);
    }

    private void findClosestPlayer() {
        closestPlayer = null;
        closestDistance = Double.MAX_VALUE;

        // cast to int in case getPlayersWithin only accepts an int radius
        for (Player player : PlayerUtils.getPlayersWithin((int) Math.ceil(20))) {
            double distance = mc.player.distanceTo(player);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestPlayer = player;
            }
        }
    }


    private boolean serverSidedPositionIsCloser() {
        if (closestPlayer == null || positionHistory.isEmpty())
            return false;

        int ticksAgo = (int) (LagManager.getPingAsTicks() + currentDelay / 20);
        ticksAgo = Math.max(0, Math.min(ticksAgo, positionHistory.size() - 1));

        Vec3 serverSided = positionHistory.get(ticksAgo);
        Vec3 enemyPos = new Vec3(closestPlayer.getX(), closestPlayer.getY(), closestPlayer.getZ());

        double serverSidedDistance = serverSided.distanceTo(enemyPos);
        return serverSidedDistance < closestDistance;
    }

    private void startLag() {
        if (closestDistance <= 5)
            return;

        lagging = true;
        currentDelay = 0;
        targetDelay = delay.getValue().getRandomInRange();
        LagManager.delayOutgoing(Packet.class, packet -> (long) currentDelay);
    }

    private void buildUp() {
        if (currentDelay >= targetDelay)
            return;

        double increment = 600 <= 0
                ? targetDelay
                : (targetDelay * 50.0) / 600; // 50ms ~ 1 tick at 20 TPS

        currentDelay = Math.min(targetDelay, currentDelay + increment);
    }

    private void stopLag(boolean emergency) {
        if (!lagging)
            return;

        lagging = false;
        lastReleaseTime = System.currentTimeMillis();
        LagManager.undelayOutgoing(Packet.class);
        LagManager.releaseDelayedOutgoing(LagManager.ALL_PACKETS);
    }

    private void reset() {
        lagging = false;
        currentDelay = 0;
        targetDelay = 0;
        positionHistory.clear();
    }

    @Override
    public void onDisable() {
        stopLag(true);
        reset();
    }
}