package arsenic.module.impl.combat;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C02PacketUseEntity;


@ModuleInfo(name = "Aura",category = ModuleCategory.BLATANT)
public class Aura extends Module {
    public final EnumProperty<rotMode> mode = new EnumProperty<>("Mode: ", rotMode.Silent);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(0, 6, 3, 0.1));
    public final DoubleProperty aps = new DoubleProperty("APS", new DoubleValue(0, 20, 14, 1));
    public final BooleanProperty rotate = new BooleanProperty("Rotate", true);
    public final BooleanProperty noGui = new BooleanProperty("Don't hit in gui's", true);

    private long lastAttack = 0;

    // If the Aura works well thank KassuK if it doesn't blame KV.

    // This is not the best way to do the rots probably

    @EventLink
    public final Listener<EventUpdate.Pre> eventPreUpdateListener = event -> {
        if(mc.thePlayer == null || mc.theWorld == null)
            return;

        Entity target = PlayerUtils.getClosestPlayerWithin(range.getValue().getInput());

        if (target == null) {
            return;
        }

        if(rotate.getValue()) {
            float[] rotations = RotationUtils.getRotations(mc.thePlayer.getPositionVector(), target.getPositionVector());
            if (mode.getValue() == rotMode.Silent){
                event.setYaw(rotations[0]);
                event.setPitch(rotations[1]);
            }
        }
    };

    @EventLink
    public final Listener<EventUpdate.Post> eventPostUpdateListener = event -> {
        if(mc.thePlayer == null || mc.theWorld == null)
            return;

        Entity target = PlayerUtils.getClosestPlayerWithin(range.getValue().getInput());

        if (target == null) {
            return;
        }

        if(rotate.getValue()) {
            float[] rotations = RotationUtils.getRotations(mc.thePlayer.getPositionVector(), target.getPositionVector());
            if (mode.getValue() == rotMode.LockView){
                mc.thePlayer.rotationYaw = rotations[0];
                mc.thePlayer.rotationPitch = rotations[1];
            }
        }
    };

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        double delay = 1000 / aps.getValue().getInput();

        Entity target = PlayerUtils.getClosestPlayerWithin(range.getValue().getInput());

        if (target == null) {
            return;
        }

        if (noGui.getValue() && mc.currentScreen != null) return;

        if (mc.thePlayer != null && mc.theWorld != null) {
            if(System.currentTimeMillis() - lastAttack >= delay) {
                mc.thePlayer.swingItem();
                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
                lastAttack = System.currentTimeMillis();
            }
        }
    };

    public enum rotMode {
        Silent,
        LockView
    }
}
