package arsenic.module.impl.combat;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
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
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;


@ModuleInfo(name = "Aura",category = ModuleCategory.BLATANT)
public class Aura extends Module {
    public final EnumProperty<rotMode> mode = new EnumProperty<>("Mode: ", rotMode.Silent);
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(0, 6, 3, 0.1));
    public final DoubleProperty aps = new DoubleProperty("APS", new DoubleValue(0, 20, 14, 1));
    public final BooleanProperty noGui = new BooleanProperty("Don't hit in gui's", true);
    public final BooleanProperty onlyWeapon = new BooleanProperty("Only weapons", true);

    private long lastAttack = 0;
    private boolean attack = false;

    // If the Aura works well thank KassuK if it doesn't blame KV.
    // Do not fucking touch my aura Cosmic go back to your sister you inbred fuck

    @EventLink
    public final Listener<EventUpdate.Pre> eventPreUpdateListener = event -> {
        // Doing it this way randomises it for you, no need for anything else no idea why though
        long delay = (long) (1000 / aps.getValue().getInput());
        if(System.currentTimeMillis() - lastAttack >= delay) {
            attack = true;
            lastAttack = System.currentTimeMillis();
        }

        if(mc.thePlayer == null || mc.theWorld == null)
            return;

        Entity target = PlayerUtils.getClosestPlayerWithin(range.getValue().getInput());

        if (target == null || onlyWeapon.getValue() && !holdingWeapon()) {
            return;
        }

        float[] rotations = RotationUtils.getRotations(mc.thePlayer.getPositionVector(), target.getPositionVector());
        if (mode.getValue() == rotMode.Silent){
            event.setYaw(rotations[0]);
            event.setPitch(rotations[1]);

        }

        if (noGui.getValue() && mc.currentScreen != null) return;

        // We attack on Pre to not flag literally every anti-cheat
        if (attack) {
            // btw using a packet does not flag
            mc.thePlayer.swingItem();
            mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
            attack = false;
            lastAttack = System.currentTimeMillis();
        }
    };

    public boolean holdingWeapon() {
        return mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword || mc.thePlayer.getHeldItem() != null && mc.thePlayer.getHeldItem().getItem() instanceof ItemAxe;
    }

    @EventLink
    public final Listener<EventUpdate.Post> eventPostUpdateListener = event -> {
        if(mc.thePlayer == null || mc.theWorld == null)
            return;

        Entity target = PlayerUtils.getClosestPlayerWithin(range.getValue().getInput());

        if (target == null) {
            return;
        }

        float[] rotations = RotationUtils.getRotations(mc.thePlayer.getPositionVector(), target.getPositionVector());
        if (mode.getValue() == rotMode.LockView){
            mc.thePlayer.rotationYaw = rotations[0];
            mc.thePlayer.rotationPitch = rotations[1];
        }
    };

    public enum rotMode {
        LockView,
        Silent,
        None
    }
}