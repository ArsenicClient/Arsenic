package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.injection.accessor.IMixinS14PacketEntity;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.ghost.backtrack.TimedPacket;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PacketUtil;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@ModuleInfo(name = "Backtrack", category = ModuleCategory.GHOST)
public class BackTrack extends Module {

    public final RangeProperty latencyRange = new RangeProperty("Latency", new RangeValue(10, 1000, 50, 100, 10));
    public final RangeProperty distanceRange = new RangeProperty("Distance", new RangeValue(0, 6, 0, 4.0, 0.1));

    private final Queue<TimedPacket> packetQueue = new ConcurrentLinkedQueue<>();
    private final List<Packet<?>> skipPackets = new ArrayList<>();
    private Vec3 vec3;
    private EntityPlayer target;

    private int currentLatency = 0;


    @Override
    public void onEnable() {
        packetQueue.clear();
        skipPackets.clear();
        vec3 = null;
        target = null;
    }

    @Override
    public void onDisable() {
        //releaseAll();
    }

    @EventLink
    public final Listener<EventUpdate.Pre> eventUpdate = event -> {
        if(target == null || vec3 == null)
            return;

        // stop backtracking if the real entity is closer && they are 0 hurttime, should work well enough
        // no fucking clue if this makes it better or not but I hope so
        if(RotationUtils.getDistanceToEntityBox(target) + 0.2 <= Math.sqrt(mc.thePlayer.getDistanceSq(target.posX, mc.thePlayer.posY, target.posZ)) && target.hurtTime <= 2) {
            currentLatency = 0;
            releaseAll();
            target = null;
            vec3 = null;
            return;
        }

        final double distance = RotationUtils.getDistanceToEntityBox(target);
        if (!distanceRange.hasInRange(distance)) {
            currentLatency = 0;
            releaseAll();
            target = null;
            vec3 = null;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> eventPacketIncoming = event -> {
        Packet<?> packet = event.getPacket();
        if (skipPackets.contains(packet)) {
            skipPackets.remove(packet);
            return;
        }

        try {
            if (mc.thePlayer == null || mc.thePlayer.ticksExisted < 20) {
                packetQueue.clear();
                return;
            }

            if (target == null) {
                releaseAll();
                return;
            }

            if (event.isCancelled())
                return;

            //- Packets that reset state
            if (packet instanceof S08PacketPlayerPosLook || packet instanceof S40PacketDisconnect) {
                releaseAll();
                target = null;
                vec3 = null;
                return; // Don't delay
            } else if (packet instanceof S13PacketDestroyEntities) {
                S13PacketDestroyEntities wrapper = (S13PacketDestroyEntities) packet;
                for (int id : wrapper.getEntityIDs()) {
                    if (id == target.getEntityId()) {
                        target = null;
                        vec3 = null;
                        releaseAll();
                        return; // Don't delay
                    }
                }
            }


            if (packet instanceof S14PacketEntity) {
                S14PacketEntity s14PacketEntity = (S14PacketEntity) packet;
                IMixinS14PacketEntity wrapper = (IMixinS14PacketEntity) packet;
                if (wrapper.getEntityId() == target.getEntityId()) {
                    vec3 = vec3.addVector(s14PacketEntity.func_149062_c() / 32.0D, s14PacketEntity.func_149061_d() / 32.0D,
                            s14PacketEntity.func_149064_e() / 32.0D);
                    TimedPacket timedPacket = new TimedPacket(packet, currentLatency);
                    timedPacket.getTimer().start();
                    packetQueue.add(timedPacket);
                    event.cancel();
                }

            } else if (packet instanceof S18PacketEntityTeleport) {
                S18PacketEntityTeleport wrapper = (S18PacketEntityTeleport) packet;
                if (wrapper.getEntityId() == target.getEntityId()) {
                    vec3 = new Vec3(wrapper.getX() / 32.0D, wrapper.getY() / 32.0D, wrapper.getZ() / 32.0D);
                    TimedPacket timedPacket = new TimedPacket(packet, currentLatency);
                    timedPacket.getTimer().start();
                    packetQueue.add(timedPacket);
                    event.cancel();
                }
            }
        } catch (NullPointerException ignored) {

        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTick = event -> {
        while (!packetQueue.isEmpty()) {
            try {
                if (packetQueue.element().getTimer().hasFinished()) {
                    Packet<?> packet = packetQueue.remove().getPacket();
                    skipPackets.add(packet);
                    PacketUtil.receivePacket(packet);
                } else {
                    break;
                }
            } catch (NullPointerException ignored) {
            }
        }

        if (packetQueue.isEmpty() && target != null) {
            vec3 = target.getPositionVector();
        }
    };

    @EventLink
    public final Listener<EventRenderWorldLast> eventRenderWorldLast = event -> {
        if (target == null || vec3 == null || target.isDead || currentLatency == 0)
            return;

        RenderUtils.drawBoundingBox(vec3, new Color(255, 255, 255));
    };

    @EventLink
    public final Listener<EventAttack> eventAttack = event -> {
        final Vec3 targetPos = event.getTarget().getPositionVector();
        if (event.getTarget() instanceof EntityPlayer) {
            if (target == null || event.getTarget() != target) {
                vec3 = targetPos;
            }
            target = (EntityPlayer) event.getTarget();

            try {
                final double distance = RotationUtils.getDistanceToEntityBox(target);
                if (!distanceRange.hasInRange(distance))
                    return;

            } catch (NullPointerException ignored) {

            }

            currentLatency = (int) latencyRange.getValue().getRandomInRange();
        }
    };


    public void releaseAll() {
        if (!packetQueue.isEmpty()) {
            for (TimedPacket timedPacket : packetQueue) {
                Packet<?> packet = timedPacket.getPacket();
                skipPackets.add(packet);
                PacketUtil.receivePacket(packet);
            }
            packetQueue.clear();
        }
    }
}
