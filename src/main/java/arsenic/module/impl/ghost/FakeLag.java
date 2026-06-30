package arsenic.module.impl.ghost;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.PropertyInfo;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.lag.LagManager;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;

@ModuleInfo(name = "FakeLag", category = ModuleCategory.GHOST)
public class FakeLag extends Module {

    public enum ReleaseMode {
        Instant,
        Smooth
    }

    public final DoubleProperty enableRange = new DoubleProperty("Enable Range", new DoubleValue(4, 64, 20, 1));
    public final DoubleProperty safeRange = new DoubleProperty("Safe Range", new DoubleValue(1, 20, 5, 0.5));
    public final RangeProperty delay = new RangeProperty("Delay", new RangeValue(0, 2000, 100, 200, 10));
    public final DoubleProperty buildupDuration = new DoubleProperty("Buildup Duration", new DoubleValue(0, 3000, 600, 50));
    public final DoubleProperty buildupDelay = new DoubleProperty("Buildup Delay", new DoubleValue(0, 5000, 500, 50));
    public final EnumProperty<ReleaseMode> releaseMode = new EnumProperty<>("Release Mode", ReleaseMode.Smooth);
    @PropertyInfo(reliesOn = "Release Mode", value = "Smooth")
    public final DoubleProperty releaseChunk = new DoubleProperty("Release Chunk", new DoubleValue(1, 10, 1, 1));
    private static final int MAX_POSITION_HISTORY = 400;

    private final List<Vec3> positionHistory = new ArrayList<>();

    private boolean lagging;
    private double currentDelay = 0;
    private double targetDelay = 0;
    private long lastReleaseTime = 0;

    private EntityPlayer closestPlayer;
    private double closestDistance = Double.MAX_VALUE;

    @EventLink
    public final Listener<EventTick> eventTickListener = eventTick -> {
        if (mc.thePlayer == null) {
            setEnabled(false);
            reset();
            return;
        }

        recordPosition();
        findClosestPlayer();

        if (closestDistance <= safeRange.getValue().getInput()) {
            // an enemy is already too close - never start a buildup, and bail out instantly if one was running
            if (lagging)
                stopLag(true);
            return;
        }

        if (closestDistance > enableRange.getValue().getInput()) {
            // nobody worth lagging for anymore, release normally if we were running
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

        if (System.currentTimeMillis() - lastReleaseTime < buildupDelay.getValue().getInput())
            return;

        startLag();
    };

    @EventLink
    public final Listener<EventAttack> eventAttack = event -> stopLag(false);

    private void recordPosition() {
        positionHistory.add(0, new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ));
        while (positionHistory.size() > MAX_POSITION_HISTORY)
            positionHistory.remove(positionHistory.size() - 1);
    }

    private void findClosestPlayer() {
        closestPlayer = null;
        closestDistance = Double.MAX_VALUE;

        // cast to int in case getPlayersWithin only accepts an int radius
        for (EntityPlayer player : PlayerUtils.getPlayersWithin((int) Math.ceil(enableRange.getValue().getInput()))) {
            double distance = mc.thePlayer.getDistanceToEntity(player);
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
        Vec3 enemyPos = new Vec3(closestPlayer.posX, closestPlayer.posY, closestPlayer.posZ);

        double serverSidedDistance = serverSided.distanceTo(enemyPos);
        return serverSidedDistance < closestDistance;
    }

    private void startLag() {
        if (closestDistance <= safeRange.getValue().getInput())
            return;

        lagging = true;
        currentDelay = 0;
        targetDelay = delay.getValue().getRandomInRange();
        LagManager.delayOutgoing(Packet.class, packet -> (long) currentDelay);
    }

    private void buildUp() {
        if (currentDelay >= targetDelay)
            return;

        double increment = buildupDuration.getValue().getInput() <= 0
                ? targetDelay
                : (targetDelay * 50.0) / buildupDuration.getValue().getInput(); // 50ms ~ 1 tick at 20 TPS

        currentDelay = Math.min(targetDelay, currentDelay + increment);
    }

    private void stopLag(boolean emergency) {
        if (!lagging)
            return;

        lagging = false;
        lastReleaseTime = System.currentTimeMillis();
        LagManager.undelayOutgoing(Packet.class);

        if (!emergency && releaseMode.getValue() == ReleaseMode.Smooth) {
            LagManager.releaseDelayedOutgoingChunked(LagManager.ALL_PACKETS, (int) releaseChunk.getValue());
        } else {
            LagManager.releaseDelayedOutgoing(LagManager.ALL_PACKETS);
        }
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