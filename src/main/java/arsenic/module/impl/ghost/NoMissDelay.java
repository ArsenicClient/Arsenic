package arsenic.module.impl.ghost;

import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.client.TargetManager;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.minecraft.ItemUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps your sword or axe charge when you swing at air: the miss no longer resets the attack cooldown, plays no swing
 * and sends no punch packet, so the next hit on a player still lands at full strength.
 */
@ModuleInfo(name = "NoMissDelay", description = "Missed swings no longer reset your weapon cooldown", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class NoMissDelay extends Module {

    /** Chance per air swing to act like vanilla, so not every single miss keeps its charge. */
    public final DoubleProperty humanize = new DoubleProperty("Humanize %", new DoubleValue(0, 100, 0, 1));
    public final BooleanProperty inCombatOnly = new BooleanProperty("In Combat Only", false);
    @ShowWhen(property = "In Combat Only", is = "true")
    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(3, 8, 5, 0.5));

    /** True between the head of a click and its end when this click's miss is being suppressed. */
    private boolean suppressing;

    @Override
    protected void onDisable() {
        suppressing = false;
    }

    /**
     * Called at the start of every left click (Minecraft.startAttack). Decides whether this click, if it is a miss with
     * a sword or axe, should keep the attack cooldown.
     */
    public void beginClick() {
        suppressing = false;
        if (!isEnabled() || mc.player == null || mc.level == null)
            return;
        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.MISS)
            return;
        ItemStack held = mc.player.getMainHandItem();
        if (!ItemUtils.isSword(held) && !ItemUtils.isAxe(held))
            return;
        if (inCombatOnly.getValue() && !enemyNearby())
            return;
        // humanize: this miss behaves like vanilla
        if (ThreadLocalRandom.current().nextInt(1, 101) <= (int) humanize.getValue().getInput())
            return;
        suppressing = true;
    }

    /** Whether the miss in the current click should skip its cooldown reset, swing and punch packet. */
    public boolean isSuppressing() {
        return suppressing && isEnabled();
    }

    public void endClick() {
        suppressing = false;
    }

    private boolean enemyNearby() {
        double r = range.getValue().getInput();
        double rSq = r * r;
        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive())
                continue;
            if (!TargetManager.isValidTarget(player))
                continue;
            if (mc.player.distanceToSqr(player) <= rSq)
                return true;
        }
        return false;
    }
}
