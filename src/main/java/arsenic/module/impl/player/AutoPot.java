package arsenic.module.impl.player;

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
import net.minecraft.item.ItemPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.potion.PotionEffect;

import java.util.List;

@ModuleInfo(name = "AutoPot", category = ModuleCategory.PLAYER)
public class AutoPot extends Module {

    /** Health percentage below which to pot. */
    public final DoubleProperty healthThreshold = new DoubleProperty("Health %", new DoubleValue(1, 100, 40, 1));

    /** Gap between throws. Lower heals through more damage and looks less like a person. */
    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(100, 3000, 500, 50));

    /**
     * Only healing potions are ever thrown, and the throw always moves the real view.
     * <p>
     * The silent-rotation path pitched the view server side only, which is the single most obvious
     * thing this module could do - the player is suddenly facing straight down for one tick without
     * their screen moving. It was off by default for that reason, so it is simply gone.
     */
    private static final boolean HEAL_ONLY = true;

    private long lastThrow;
    private boolean shouldLookDown;
    private long lookDownUntil;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        long now = System.currentTimeMillis();
        if (now - lastThrow < delay.getValue().getInput()) return;

        float healthPct = (mc.player.getHealth() / mc.player.getMaxHealth()) * 100.0f;
        if (healthPct > healthThreshold.getValue().getInput()) return;

        int potSlot = findBestPot();
        if (potSlot == -1) return;

        int oldSlot = mc.player.inventory.currentItem;
        mc.player.inventory.currentItem = potSlot;
        mc.player.rotationPitch = 90;
        shouldLookDown = true;
        lookDownUntil = now + 200;
        mc.gameMode.updateController();
        mc.gameMode.sendUseItem(mc.player, mc.level, mc.player.inventory.getCurrentItem());
        mc.player.inventory.currentItem = oldSlot;
        lastThrow = now;
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onUpdate = event -> {
        event.setSpeed(180);
        if (shouldLookDown && System.currentTimeMillis() < lookDownUntil) {
            event.setPitch(90);
        } else {
            shouldLookDown = false;
        }
    };

    private int findBestPot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.inventory.getStackInSlot(i);
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
