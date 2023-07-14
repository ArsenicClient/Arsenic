package arsenic.module.impl.combat;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRunTick;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C02PacketUseEntity;


@ModuleInfo(name = "Aura",category = ModuleCategory.BLATANT)
public class Aura extends Module { //TODO: add raycasting, add rotation modes (bypasses basically)
    public final EnumProperty<rotMode> mode = new EnumProperty<>("Mode: ", rotMode.Silent);
    public final EnumProperty<m> blockMode = new EnumProperty<>("BlockMode: ", m.Vanilla);
    public final DoubleProperty range = new DoubleProperty("Reach", new DoubleValue(0, 6, 3, 0.1));
    public final RangeProperty aps = new RangeProperty("APS", new RangeValue(1, 20, 10,15, 1));
    public final DoubleProperty rps = new DoubleProperty("Rotation Speed", new DoubleValue(1, 180, 70, 1));
    public final BooleanProperty fixs = new BooleanProperty("Rotation Fix", true);
    public final BooleanProperty weaponOnly = new BooleanProperty("WeaponOnly", true);
    public final BooleanProperty noGui = new BooleanProperty("Don't hit in gui's", true);

    private long lastAttack = 0;
    private boolean blocking;

    // If the Aura works well thank Cosmic if it doesn't blame KV and KassuK. :p
    //WHO MADE THIS AHHHHHHHHHHHHHHHHHHHHHHHHHHHHHH


    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        if(mc.thePlayer == null || mc.theWorld == null)
            return;

        Entity target = PlayerUtils.getClosestPlayerWithin(range.getValue().getInput());
        if (weaponOnly.getValue() && !PlayerUtils.isPlayerHoldingWeapon()) {
            target = null;
        }
        if (target == null) {
            return;
        }

        float[] rotations = RotationUtils.getRotations(mc.thePlayer.getPositionVector(), target.getPositionVector());
        if (mode.getValue() == rotMode.Silent){
            event.setJumpFix(fixs.getValue());
            event.setDoMovementFix(fixs.getValue());
            event.setSpeed((float) rps.getValue().getInput());
            event.setYaw(rotations[0]);
            event.setPitch(rotations[1]);
        }
    };

    @EventLink
    public final Listener<EventUpdate.Post> eventPostUpdateListener = event -> {
        if(mc.thePlayer == null || mc.theWorld == null)
            return;

        Entity target = PlayerUtils.getClosestPlayerWithin(range.getValue().getInput());
        if (weaponOnly.getValue() && !PlayerUtils.isPlayerHoldingWeapon()) {
            target = null;
        }
        if (target == null) {
            return;
        }

        float[] rotations = RotationUtils.getRotations(mc.thePlayer.getPositionVector(), target.getPositionVector());
        if (mode.getValue() == rotMode.LockView){
            mc.thePlayer.rotationYaw = rotations[0];
            mc.thePlayer.rotationPitch = rotations[1];
        }
    };

    @EventLink
    public final Listener<EventTick> eventTick = event -> {
        double delay = 1000 / aps.getValue().getRandomInRange();

        Entity target = PlayerUtils.getClosestPlayerWithin(range.getValue().getInput());
        if (weaponOnly.getValue() && !PlayerUtils.isPlayerHoldingWeapon()) {
            target = null;
        }

        if (mc.thePlayer != null && mc.theWorld != null){
            if (target != null && !blockMode.getValue().equals(m.None)) {
                if (PlayerUtils.isPlayerHoldingWeapon()) {
                    switch (blockMode.getValue()) {
                        case Vanilla:
                            this.block();
                            break;
                        case Damage:
                            if (mc.thePlayer.hurtTime > 0) {
                                this.block();
                            }
                            break;
                        case BlockHit:
                            if (mc.thePlayer.ticksExisted % 15 == 0 && mc.thePlayer.prevSwingProgress < mc.thePlayer.swingProgress){
                                KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                            }
                            break;
                        case Spam:
                            if (mc.thePlayer.prevSwingProgress < mc.thePlayer.swingProgress) {
                                KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
                            }
                            break;
                    }
                } else {
                    this.unblock();
                }
            }
        }
        if (target == null) {
            return;
        }

        if (noGui.getValue() && mc.currentScreen != null) return;

        if (mc.thePlayer != null && mc.theWorld != null) {
            if(System.currentTimeMillis() - lastAttack >= delay) {
                mc.thePlayer.swingItem();
                //why send c02 it does not bypass anything
                //mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
                mc.playerController.attackEntity(mc.thePlayer,target);
                lastAttack = System.currentTimeMillis();
            }
        }
    };

    private void block() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        this.blocking = true;
    }

    private void unblock() {
        if (this.blocking) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
            this.blocking = false;
        }
    }
    public enum rotMode {
        Silent,
        LockView
    }

    public enum m {
        Vanilla,
        Damage,
        BlockHit,
        Spam,
        None
    }
}
