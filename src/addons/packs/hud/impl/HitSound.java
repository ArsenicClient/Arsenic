import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Plays a sound when one of your hits lands, and another when the target dies from it. Each sound can be picked (or
 * turned off), and the volume and pitch are adjustable. The hit confirm sound is the client's own and ignores volume
 * and pitch. A landed hit is an attack whose target is hurt on one of the next ticks.
 */
@ModuleInfo(name = "HitSound", description = "Plays a sound when one of your hits lands, and another on a kill", category = ModuleCategory.RENDER)
public class HitSound extends Module {

    public enum Choice {
        CONFIRM("Hit Confirm", null),
        CLICK("Click", "random.click"),
        ORB("Orb", "random.orb"),
        POP("Pop", "random.pop"),
        ANVIL("Anvil", "random.anvil_land"),
        LEVEL_UP("Level Up", "random.levelup"),
        NONE("None", null);

        private final String label;
        private final String sound;

        Choice(String label, String sound) {
            this.label = label;
            this.sound = sound;
        }

        public String getSound() {
            return sound;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public final EnumProperty<Choice> hitSound = new EnumProperty<>("Hit Sound", Choice.CONFIRM);
    public final EnumProperty<Choice> killSound = new EnumProperty<>("Kill Sound", Choice.LEVEL_UP);
    public final DoubleProperty volume = new DoubleProperty("Volume", new DoubleValue(0, 1, 0.5, 0.05));
    public final DoubleProperty pitch = new DoubleProperty("Pitch", new DoubleValue(0.5, 2, 1, 0.05));
    public final BooleanProperty onlyPlayers = new BooleanProperty("Only Players", false);

    private final MSTimer pendingTimer = new MSTimer();
    private Entity pending;

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> onAttack = event -> {
        if (onlyPlayers.getValue() && !(event.getTarget() instanceof EntityPlayer)) {
            pending = null;
            return;
        }
        pending = event.getTarget();
        pendingTimer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (pending == null) return;
        if (pending instanceof EntityLivingBase && ((EntityLivingBase) pending).hurtTime > 0) {
            EntityLivingBase target = (EntityLivingBase) pending;
            play(hitSound.getValue());
            if (target.isDead || target.getHealth() <= 0) play(killSound.getValue());
            pending = null;
        } else if (pendingTimer.hasTimeElapsed(200)) {
            pending = null;
        }
    };

    private void play(Choice choice) {
        if (choice == Choice.NONE) return;
        if (choice == Choice.CONFIRM) {
            SoundUtils.hitConfirm();
            return;
        }
        mc.thePlayer.playSound(choice.getSound(), (float) volume.getValue().getInput(), (float) pitch.getValue().getInput());
    }
}
