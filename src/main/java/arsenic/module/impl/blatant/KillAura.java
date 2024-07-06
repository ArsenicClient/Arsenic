package arsenic.module.impl.blatant;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.impl.world.Scaffold;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.*;
import java.security.SecureRandom;

@ModuleInfo(name = "KillAura", category = ModuleCategory.BLATANT)
public class KillAura extends Module {
    private Enum<TargetManager.SortMode> prevVal;
    public BooleanProperty switchAura = new BooleanProperty("Switch", false){
        @Override
        public void onValueUpdate() {
            if (this.getValue()) {
                prevVal = TargetManager.sortMode.getValue();
                TargetManager.sortMode.setValue(TargetManager.SortMode.HurtSwitch);
            } else {
                if (prevVal != null){
                    TargetManager.sortMode.setValue((TargetManager.SortMode) prevVal);
                }
            }
        }
    };

    public RangeProperty aps = new RangeProperty("APS", new RangeValue(1, 20, 10, 1, 1));
    public DoubleProperty attackRange = new DoubleProperty("Attack Range", new DoubleValue(1, 6, 4.5, 0.1));
    public DoubleProperty findRange = new DoubleProperty("Find Range", new DoubleValue(1, 6, 4.5, 0.1));
    public final RangeProperty speed = new RangeProperty("speed", new RangeValue(1, 100, 20, 50,1));
    public BooleanProperty moveFix = new BooleanProperty("MoveFix", false); //why is this even an option
    public BooleanProperty raycast = new BooleanProperty("Raycast", false);
    public BooleanProperty troughWalls = new BooleanProperty("Through Walls", false);
    public BooleanProperty swing = new BooleanProperty("Show Swing", true);
    public BooleanProperty esp = new BooleanProperty("ESP", true);

    public EntityPlayer target = null;
    private final MSTimer attackTimer = new MSTimer();

    @Override
    protected void onEnable() {
        target = null;
    }

    @Override
    protected void onDisable() {
        target = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        if (!canAura()) return;
        float[] rots = RotationUtils.getRotationsToEntity(target); //smoothing is already done in rotation manager.
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setJumpFix(moveFix.getValue());
        event.setDoMovementFix(moveFix.getValue());
        event.setSpeed((float) speed.getValue().getRandomInRange());
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> eventTickListener = event -> {
        getTarget();
        if (!canAura()) return;

        if (target != null) {
            if (RotationUtils.getDistanceToEntityBox(target) <= attackRange.getValue().getInput() && (troughWalls.getValue() || mc.thePlayer.canEntityBeSeen(target))) {
                AutoBlock ab = Arsenic.getArsenic().getModuleManager().getModuleByClass(AutoBlock.class);
                if (ab.isEnabled() && ab.blockMode.getValue() == AutoBlock.bMode.Hypixel) {
                    return;
                }
                if (attackTimer.hasTimeElapsed(getAttackDelay())) {
                    attack(false);
                    attackTimer.reset();
                }
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if (!canAura() || !esp.getValue()) {
            return;
        }
        RenderUtils.drawCircle(target, event.partialTicks, 0.7, Arsenic.getInstance().getThemeManager().getCurrentTheme().getMainColor(), 255);
    };

    public void attack(boolean interact) {
        if (!canAura()) return;

        if (!raycast.getValue()) {
            swing();
            mc.playerController.attackEntity(mc.thePlayer, target);
            if (interact) {
                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.INTERACT));
            }
        } else {
            PlayerUtils.click();
        }
    }

    private void swing() {
        if (swing.getValue()) {
            mc.thePlayer.swingItem();
        } else {
            mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
        }
    }

    private void getTarget() {
        EntityPlayer e = TargetManager.getTarget();
        if (e == null) return;
        double calculatedDistance = RotationUtils.getDistanceToEntityBox(e);

        if (calculatedDistance <= findRange.getValue().getInput()) {
            if (e.getHealth() > 0) {
                target = e;
                return;
            }
        }

        target = null;
    }


    private long getAttackDelay() {
        double x = aps.getValue().getMax();
        double y = aps.getValue().getMin();
        float finalValue = getRandom((float) x, (float) y) + 6;
        return (long) (1000L / finalValue);
    }

    private boolean canAura(){
        return target != null && !Arsenic.getInstance().getModuleManager().getModuleByClass(Scaffold.class).isEnabled();
    }

    public static float getRandom(float min, float max) {
        SecureRandom random = new SecureRandom();
        return random.nextFloat() * (max - min) + min;
    }
}