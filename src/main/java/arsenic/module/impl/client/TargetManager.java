package arsenic.module.impl.client;

import arsenic.main.Arsenic;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.property.PropertyInfo;
import arsenic.utils.rotations.RotationUtils;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.PlayerUtils;
import java.util.Comparator;
import java.util.List;

/**
 * @author kv
 */
@ModuleInfo(name = "Targets",category = ModuleCategory.SETTINGS)
public class TargetManager extends Module {
    public static EnumProperty<SortMode> sortMode = new EnumProperty<>("Sort Mode",SortMode.Distance);
    public static BooleanProperty teams = new BooleanProperty("Target Teammates", true),
            invis = new BooleanProperty("Target Invis", true),
            bots = new BooleanProperty("Target Bots", true),
            unArmoured = new BooleanProperty("Target UnArmoured", true);
    public static DoubleProperty fov = new DoubleProperty("General FOV", new DoubleValue(0,360,180,1)),
            auraFov = new DoubleProperty("Aura FOV", new DoubleValue(0,360,360,1)),
            distance = new DoubleProperty("Distance", new DoubleValue(3,10,8,1));
    @PropertyInfo(reliesOn = "Sort Mode", value = "Lock")
    public final DoubleProperty lockDist = new DoubleProperty("Locked Distance", new DoubleValue(3,10,5,1));
    private static EntityPlayer lockedTarget;

    @Override
    protected void onEnable() {
        this.setEnabled(false);
    }


    private static double getFOV() {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class).isEnabled() ? auraFov.getValue().getInput() : fov.getValue().getInput();
    }

    @EventLink
    public Listener<EventAttack> eventAttackListener = e -> { //todo fix this later
        lockedTarget = e.getTarget() instanceof EntityPlayer && mc.thePlayer.getDistanceToEntity(e.getTarget()) <= lockDist.getValue().getInput() ? (EntityPlayer) e.getTarget() : lockedTarget;
    };

    public static EntityPlayer getTarget() {
        List<EntityPlayer> en = PlayerUtils.getPlayersWithin(distance.getValue().getInput());
        en.removeIf(player -> !isValidTarget(player));
        return en.isEmpty() ? null : en.stream().min(Comparator.comparingDouble(target -> sortMode.getValue().sv.value(target))).get();
    }

    private static boolean isValidTarget(EntityPlayer ep) {
        return (
                (ep != mc.thePlayer)
                        && (bots.getValue() || !AntiBot.isBot(ep))
                        && (teams.getValue() || !PlayerUtils.isEntityTeamSameAsPlayer(ep)
                        && (invis.getValue() || !ep.isInvisible())
                        && (unArmoured.getValue() || !PlayerUtils.isPlayerWearingArmour(ep))
                        && PlayerUtils.withinFov(ep, (float) getFOV())
        ));
    }


    public enum SortMode {
        Distance(player -> mc.thePlayer.getDistanceToEntity(player)),
        HurtSwitch(player -> (float) player.hurtTime),
        Fov(player -> (float) Math.abs(RotationUtils.fovFromEntity(player))),
        Lock(player -> player == lockedTarget ? 0f : 1f),
        Health(EntityLivingBase::getHealth);
        private final SortValue sv;
        SortMode(SortValue sv) {
            this.sv = sv;
        }
    }

    @FunctionalInterface
    private interface SortValue {
        Float value(EntityPlayer player);
    }
}