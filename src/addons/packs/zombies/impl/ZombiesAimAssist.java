import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.IMob;
import net.minecraft.util.Vec3;

/**
 * Turns the silent rotation toward the nearest hostile mob's head, within a range and a field of view. It aims only: it
 * never attacks or fires. Clicking is still yours, so the shot comes from the ray of the rotation actually sent, as
 * the player would see it.
 */
@ModuleInfo(name = "ZombiesAimAssist", description = "Aims the silent rotation at the nearest mob's head (aim only, never fires)", category = ModuleCategory.COMBAT)
public class ZombiesAimAssist extends Module {

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(3, 12, 8, 0.5));
    public final DoubleProperty fov = new DoubleProperty("FOV", new DoubleValue(10, 180, 90, 1));
    public final DoubleProperty speed = new DoubleProperty("Speed", new DoubleValue(20, 180, 110, 1));

    private Entity target;

    @Override
    protected void onDisable() {
        target = null;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        target = null;
        if (mc.currentScreen != null) return;
        double best = Double.MAX_VALUE;
        double max = range.getValue().getInput();
        for (Entity e : mc.theWorld.loadedEntityList) {
            if (!(e instanceof IMob) || e.isDead) continue;
            double d = mc.thePlayer.getDistanceToEntity(e);
            if (d > max) continue;
            double fovDiff = Math.abs(RotationUtils.fovToEntity(e));
            if (fovDiff > fov.getValue().getInput() / 2) continue;
            if (d < best) {
                best = d;
                target = e;
            }
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (target == null) return;
        Vec3 head = new Vec3(target.posX, target.posY + target.getEyeHeight(), target.posZ);
        float[] r = RotationUtils.rotationsTo(mc.thePlayer.getPositionEyes(1f), head);
        event.setYaw(r[0]);
        event.setPitch(r[1]);
        event.setSpeed((float) speed.getValue().getInput());
        event.setPreventDuplicateLook(true);
        event.setMovementFix(SilentRotationManager.MovementFix.SILENT);
    };
}
