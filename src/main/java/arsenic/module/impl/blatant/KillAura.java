package arsenic.module.impl.blatant;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.client.AntiBot;
import arsenic.module.impl.world.Scaffold;
import arsenic.module.property.PropertyInfo;
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
import net.minecraft.entity.EntityLivingBase;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@ModuleInfo(name = "KillAura", category = ModuleCategory.BLATANT)
public class KillAura extends Module {

    public BooleanProperty switchAura = new BooleanProperty("Switch", false);
    @PropertyInfo(reliesOn = "Switch", value = "true")
    public DoubleProperty switchDelayV = new DoubleProperty("Switch Delay", new DoubleValue(1, 1000, 150, 10));
    public RangeProperty aps = new RangeProperty("APS", new RangeValue(1, 20, 10, 1, 1));
    public DoubleProperty attackRange = new DoubleProperty("Attack Range", new DoubleValue(1, 6, 4.5, 0.1));
    public DoubleProperty findRange = new DoubleProperty("Find Range", new DoubleValue(1, 6, 4.5, 0.1));

    public DoubleProperty smooth = new DoubleProperty("Smoothness", new DoubleValue(1, 10, 2, 1));

    public BooleanProperty moveFix = new BooleanProperty("MoveFix", false);

    public BooleanProperty troughWalls = new BooleanProperty("Through Walls", false);
    public BooleanProperty attackTeamates = new BooleanProperty("Attack TeamMates", false);
    public BooleanProperty swing = new BooleanProperty("Show Swing", true);
    public BooleanProperty esp = new BooleanProperty("ESP", true);

    //target shit
    public EntityPlayer target = null;
    private final ArrayList<EntityPlayer> targetArray = new ArrayList<>();
    private int targetCount = 0;

    private final MSTimer attackTimer = new MSTimer();
    private final MSTimer switchDelay = new MSTimer();

    public boolean switchTargets;

    @Override
    protected void onEnable() {
        target = null;
        switchTargets = true;
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        target = null;
        super.onDisable();
    }


    private boolean canAttackTroughWalls(EntityLivingBase target) {
        if (troughWalls.getValue()) {
            return true;
        }

        return mc.thePlayer.canEntityBeSeen(target);
    }

    @EventLink
    public final Listener<EventSilentRotation> eventSilentRotationListener = event -> {
        if (nullCheck()) {
            return;
        }
        if (target == null || Arsenic.getInstance().getModuleManager().getModuleByClass(Scaffold.class).isEnabled()) {
            return;
        }
        float[] rots = RotationUtils.getRotationsToEntity(target, (int) smooth.getValue().getInput());
        event.setYaw(rots[0]);
        event.setPitch(rots[1]);
        event.setJumpFix(moveFix.getValue());
        event.setDoMovementFix(moveFix.getValue());
        event.setSpeed(180f);
    };

    @EventLink
    public final Listener<EventTick> eventTickListener = event -> {
        if (nullCheck()) {
            return;
        }
        if (Arsenic.getInstance().getModuleManager().getModuleByClass(Scaffold.class).isEnabled()) {
            return;
        }
        if (Arsenic.getInstance().getModuleManager().getModuleByClass(AutoBlock.class).isEnabled() && Arsenic.getInstance().getModuleManager().getModuleByClass(AutoBlock.class).blockMode.getValue() == AutoBlock.bMode.HYPIXEL) {
            if (switchTargets || target == null) {
                switchTargets = false;
                getTarget();
            }
        } else {
            getTarget();
        }
        if (target != null) {
            if (RotationUtils.getDistanceToEntityBox(target) <= attackRange.getValue().getInput() && canAttackTroughWalls(target)) {
                if (Arsenic.getInstance().getModuleManager().getModuleByClass(AutoBlock.class).isEnabled() && Arsenic.getInstance().getModuleManager().getModuleByClass(AutoBlock.class).blockMode.getValue() == AutoBlock.bMode.HYPIXEL) {
                    return;
                }
                if (attackTimer.hasTimeElapsed(getAttackDelay())) {
                    attack(false);
                    attackTimer.reset();
                }
            }
        }
    };

    @EventLink
    public final Listener<EventRenderWorldLast> renderWorldLast = event -> {
        if (nullCheck()) {
            return;
        }
        if (target == null || !esp.getValue()) {
            return;
        }
        RenderUtils.drawCircle(target, event.partialTicks, 0.55, Arsenic.getInstance().getThemeManager().getCurrentTheme().getMainColor(), 255);
    };

    public void attack(boolean interact) {
        if (target == null) {
            return;
        }

        if (RotationUtils.getDistanceToEntityBox(target) <= attackRange.getValue().getInput()) {
            swing();

            mc.playerController.attackEntity(mc.thePlayer, target);

            if (interact) {
                mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.INTERACT));
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
        // ik this is a mess but it works perfectly
        EntityPlayer temp_target = null;
        float health = Float.MAX_VALUE;
        targetArray.clear();
        List<EntityPlayer> inAttackRangeTargets = new ArrayList<>();

        for (EntityPlayer e : mc.theWorld.playerEntities) {
            if (Arsenic.getInstance().getModuleManager().getModuleByClass(AntiBot.class).isEnabled() && AntiBot.isBot(e)) {
                continue;
            }

            double calculatedDistance = RotationUtils.getDistanceToEntityBox(e);

            if (calculatedDistance <= findRange.getValue().getInput()) {
                if (e != mc.thePlayer && e.getHealth() > 0) {
                    if (PlayerUtils.isEntityTeamSameAsPlayer(e)) {
                        if (attackTeamates.getValue()) {
                            targetArray.add(e);
                            if (calculatedDistance <= attackRange.getValue().getInput()) {
                                inAttackRangeTargets.add(e);
                            }
                            if (e.getHealth() < health) {
                                health = e.getHealth();
                                temp_target = e;
                            }
                        }
                    } else {
                        targetArray.add(e);
                        if (calculatedDistance <= attackRange.getValue().getInput()) {
                            inAttackRangeTargets.add(e);
                        }
                        if (e.getHealth() < health) {
                            health = e.getHealth();
                            temp_target = e;
                        }
                    }
                }
            }
        }

        targetArray.sort(Comparator.comparingInt(t0 -> t0.hurtTime));
        targetArray.sort(Comparator.comparingDouble(RotationUtils::getDistanceToEntityBox));

        if (switchAura.getValue()) {
            if (switchDelay.hasTimeElapsed(switchDelayV.getValue().getInput())) {
                targetCount++;
                switchDelay.reset();
            }

            if (targetCount >= inAttackRangeTargets.size()) {
                targetCount = 0;
            }

            if (!inAttackRangeTargets.isEmpty()) {
                temp_target = inAttackRangeTargets.get(targetCount);
            } else if (!targetArray.isEmpty()) {
                temp_target = targetArray.get(targetCount % targetArray.size());
            }
        } else {
            targetCount = 0;
            if (!targetArray.isEmpty()) {
                temp_target = targetArray.get(targetCount);
            }
        }

        target = temp_target;
    }


    private long getAttackDelay() {
        double x = aps.getValue().getMax();
        double y = aps.getValue().getMin();
        float finalValue = getRandom((float) x, (float) y) + 6;
        return (long) (1000L / finalValue);
    }

    public static float getRandom(float min, float max) {
        SecureRandom random = new SecureRandom();
        return random.nextFloat() * (max - min) + min;
    }

    public enum Mode {
        vanilla,
        hypixel,
        fake
    }
}