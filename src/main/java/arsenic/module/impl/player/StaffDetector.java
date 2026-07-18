package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.notifications.Notification;
import arsenic.notifications.NotificationManager;
import arsenic.notifications.NotificationType;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

import java.util.HashSet;
import java.util.Set;

@ModuleInfo(name = "StaffDetector", category = ModuleCategory.PLAYER, dev = true)
public class StaffDetector extends Module {

    private static final String[] STAFF_PREFIXES = {
        "MOD_", "ADMIN_", "STAFF_", "HELPER_", "GM_", "TRAINEE_",
        "MOD", "ADMIN", "STAFF", "HELPER", "TRAINEE", "BUILDER"
    };

    public final DoubleProperty checkInterval = new DoubleProperty("CheckInterval", new DoubleValue(1, 30, 10, 1));
    public final BooleanProperty tabCheck = new BooleanProperty("TAB", true);
    public final BooleanProperty velocityCheck = new BooleanProperty("Velocity", true);
    public final EnumProperty<WarnMode> warnMode = new EnumProperty<>("Warn", WarnMode.CHAT);

    private final MSTimer checkTimer = new MSTimer();
    private final Set<String> detectedStaff = new HashSet<>();
    private int lastTabSize;

    @Override
    protected void onEnable() {
        detectedStaff.clear();
        lastTabSize = 0;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTick = event -> {
        if (!tabCheck.getValue())
            return;

        if (!checkTimer.hasTimeElapsed((long) checkInterval.getValue().getInput() * 1000))
            return;

        if (mc.getNetHandler() == null)
            return;

        int currentSize = mc.getNetHandler().getPlayerInfoMap().size();

        if (lastTabSize == 0) {
            lastTabSize = currentSize;
            return;
        }

        if (currentSize > lastTabSize) {
            mc.getNetHandler().getPlayerInfoMap().stream()
                    .filter(info -> info.getGameProfile() != null)
                    .map(info -> info.getGameProfile().getName())
                    .filter(name -> !detectedStaff.contains(name))
                    .filter(this::isStaffName)
                    .forEach(name -> {
                        detectedStaff.add(name);
                        warn("Staff detected: " + name + " §7(TAB)");
                    });
        }

        lastTabSize = currentSize;
        checkTimer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventPacket.Incoming.Pre> eventPacket = event -> {
        if (!velocityCheck.getValue())
            return;

        if (event.getPacket() instanceof S12PacketEntityVelocity) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                int motionY = packet.getMotionY();
                int motionX = packet.getMotionX();
                int motionZ = packet.getMotionZ();

                if (motionX == 0 && motionZ == 0 && motionY / 8000.0 > 0.075) {
                    warn("Staff is watching §7(Velocity)");
                }
            }
        }
    };

    private boolean isStaffName(String name) {
        String upper = name.toUpperCase();
        for (String prefix : STAFF_PREFIXES) {
            if (upper.startsWith(prefix))
                return true;
        }
        return false;
    }

    private void warn(String message) {
        switch (warnMode.getValue()) {
            case CHAT:
                PlayerUtils.addWaterMarkedMessageToChat("§c[STAFF] §7" + message);
                break;
            case NOTIFICATION:
                NotificationManager.show(new Notification(NotificationType.WARNING, "StaffDetector", message, 3));
                break;
        }
    }   

    public enum WarnMode {
        CHAT, NOTIFICATION
    }
}
