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
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.render.RenderUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.*;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
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

    public enum AutoBlockMode { Off, Animation, Blink }
    public final EnumProperty<AutoBlockMode> autoBlockMode = new EnumProperty<>("AutoBlock", AutoBlockMode.Off);

    public RangeProperty aps = new RangeProperty("APS", new RangeValue(1, 20, 10, 1, 1));
    public DoubleProperty attackRange = new DoubleProperty("Attack Range", new DoubleValue(1, 6, 4.5, 0.1));
    public DoubleProperty findRange = new DoubleProperty("Find Range", new DoubleValue(1, 6, 4.5, 0.1));
    public final RangeProperty speed = new RangeProperty("speed", new RangeValue(1, 100, 20, 50,1));
    public BooleanProperty moveFix = new BooleanProperty("MoveFix", false);
    public BooleanProperty raycast = new BooleanProperty("Raycast", false);
    public BooleanProperty troughWalls = new BooleanProperty("Through Walls", false);
    public BooleanProperty swing = new BooleanProperty("Show Swing", true);
    public BooleanProperty esp = new BooleanProperty("ESP", true);

    public EntityPlayer target = null;
    private final MSTimer attackTimer = new MSTimer();

    private boolean isBlocking;
    private boolean renderBlocking;
    private boolean autoBlockNeedsAttack;
    private boolean deferredBlock;

    @Override
    protected void onEnable() {
        target = null;
        isBlocking = false;
        renderBlocking = false;
        autoBlockNeedsAttack = false;
        deferredBlock = false;
    }

    @Override
    protected void onDisable() {
        target = null;
        renderBlocking = false;
        cleanupBlock();
    }

    public boolean isRenderBlocking() {
        return isEnabled() && renderBlocking;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        if (!canAura()) return;
        float[] rots = RotationUtils.getRotationsToEntity(target);
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
        if (!canAura()) {
            cleanupBlock();
            return;
        }

        if (target != null) {
            if (troughWalls.getValue() || mc.thePlayer.canEntityBeSeen(target)) {
                AutoBlockMode abMode = autoBlockMode.getValue();
                boolean wantBlock = abMode != AutoBlockMode.Off && canSwordBlock();

                if (deferredBlock) {
                    if (wantBlock) doBlock(abMode);
                    deferredBlock = false;
                } else if (autoBlockNeedsAttack) {
                    attack(false);
                    attackTimer.reset();
                    autoBlockNeedsAttack = false;
                    if (wantBlock) deferredBlock = true;
                } else if (attackTimer.hasTimeElapsed(getAttackDelay())) {
                    if (wantBlock) {
                        if (isBlocking) {
                            doUnblock(abMode);
                            autoBlockNeedsAttack = true;
                        } else {
                            attack(false);
                            attackTimer.reset();
                            if (wantBlock) deferredBlock = true;
                        }
                    } else {
                        attack(false);
                        attackTimer.reset();
                    }
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
        if (RotationUtils.getDistanceToEntityBox(target) <= attackRange.getValue().getInput()) {
            if (!raycast.getValue()) {
                swing();
                mc.playerController.attackEntity(mc.thePlayer, target);
            } else {
                PlayerUtils.click();
            }
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

    private boolean canAura() {
        return target != null && !Arsenic.getInstance().getModuleManager().getModuleByClass(Scaffold.class).isEnabled();
    }

    public static float getRandom(float min, float max) {
        SecureRandom random = new SecureRandom();
        return random.nextFloat() * (max - min) + min;
    }

    private boolean canSwordBlock() {
        return mc.thePlayer.getCurrentEquippedItem() != null
            && mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword;
    }

    private void doBlock(AutoBlockMode mode) {
        if (isBlocking || !canSwordBlock()) return;
        ItemStack stack = mc.thePlayer.getCurrentEquippedItem();
        mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(new BlockPos(-1, -1, -1), 255, stack, 0, 0, 0));
        isBlocking = true;
        renderBlocking = true;
    }

    private void doUnblock(AutoBlockMode mode) {
        if (!isBlocking) return;
        mc.getNetHandler().addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        isBlocking = false;
    }

    private void cleanupBlock() {
        if (deferredBlock) {
            deferredBlock = false;
        }
        if (autoBlockNeedsAttack) {
            if (isBlocking) doUnblock(autoBlockMode.getValue());
            autoBlockNeedsAttack = false;
        }
        if (isBlocking) doUnblock(autoBlockMode.getValue());
        renderBlocking = false;
    }
}
