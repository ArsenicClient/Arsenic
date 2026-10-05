package arsenic.module.impl.player;

import arsenic.utils.timer.MSTimer;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.List;

@ModuleInfo(name = "AutoPot", category = ModuleCategory.PLAYER, tier = ModuleTier.EXTRA)
public class AutoPot extends Module {

    public final DoubleProperty healthThreshold = new DoubleProperty("Health %", new DoubleValue(1, 100, 40, 1));

    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(100, 3000, 500, 50));

    private static final boolean HEAL_ONLY = true;

    private final MSTimer throwTimer = MSTimer.expired();
    private boolean shouldLookDown;
    private final MSTimer lookDown = MSTimer.expired();

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (!throwTimer.finished((long) delay.getValue().getInput())) return;

        float healthPct = (mc.thePlayer.getHealth() / mc.thePlayer.getMaxHealth()) * 100.0f;
        if (healthPct > healthThreshold.getValue().getInput()) return;

        int potSlot = findBestPot();
        if (potSlot == -1) return;

        int oldSlot = mc.thePlayer.inventory.currentItem;
        mc.thePlayer.inventory.currentItem = potSlot;
        mc.thePlayer.rotationPitch = 90;
        shouldLookDown = true;
        lookDown.reset();
        mc.playerController.updateController();
        mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem());
        mc.thePlayer.inventory.currentItem = oldSlot;
        throwTimer.reset();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onUpdate = event -> {
        event.setSpeed(180);
        if (shouldLookDown && lookDown.getTime() < 200) {
            event.setPitch(90);
        } else {
            shouldLookDown = false;
        }
    };

    private int findBestPot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof ItemPotion)) continue;
            ItemPotion pot = (ItemPotion) stack.getItem();
            if (HEAL_ONLY) {
                List<PotionEffect> effects = pot.getEffects(stack);
                if (effects != null) {
                    for (PotionEffect effect : effects) {
                        if (effect.getPotionID() == Potion.heal.id) return i;
                    }
                }
            } else {
                return i;
            }
        }
        return -1;
    }
}
