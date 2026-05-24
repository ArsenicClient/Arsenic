package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.event.impl.EventUpdate;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.List;

@ModuleInfo(name = "AutoPot", category = ModuleCategory.PLAYER)
public class AutoPot extends Module {

    public final DoubleProperty healthThreshold = new DoubleProperty("Health %", new DoubleValue(1, 100, 40, 1));
    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(100, 3000, 500, 50));
    public final BooleanProperty healOnly = new BooleanProperty("Heal only", true);
    public final BooleanProperty silentRotation = new BooleanProperty("Silent rotation", false);

    private long lastThrow;
    private boolean shouldLookDown;
    private long lookDownUntil;
    private boolean shouldThrow;
    private int throwSlot = -1;

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        long now = System.currentTimeMillis();
        if (now - lastThrow < delay.getValue().getInput()) return;

        float healthPct = (mc.thePlayer.getHealth() / mc.thePlayer.getMaxHealth()) * 100.0f;
        if (healthPct > healthThreshold.getValue().getInput()) return;

        int potSlot = findBestPot();
        if (potSlot == -1) return;

        if (silentRotation.getValue()) {
            throwSlot = potSlot;
            shouldThrow = true;
            shouldLookDown = true;
            lookDownUntil = now + 200;
        } else {
            int oldSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = potSlot;
            mc.thePlayer.rotationPitch = 90;
            shouldLookDown = true;
            lookDownUntil = now + 200;
            if (healOnly.getValue()) mc.playerController.updateController();
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem());
            mc.thePlayer.inventory.currentItem = oldSlot;
            lastThrow = now;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation> onUpdate = event -> {
        event.setSpeed(180);
        if (silentRotation.getValue()) {
            if (shouldLookDown && System.currentTimeMillis() < lookDownUntil) {
                event.setPitch(90);
            } else {
                shouldLookDown = false;
            }
        } else if (shouldLookDown && System.currentTimeMillis() < lookDownUntil) {
            event.setPitch(90);
        } else {
            shouldLookDown = false;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventUpdate.Post> onPostUpdate = event -> {
        if (silentRotation.getValue() && shouldThrow) {
            int oldSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = throwSlot;
            if (healOnly.getValue()) mc.playerController.updateController();
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem());
            mc.thePlayer.inventory.currentItem = oldSlot;
            lastThrow = System.currentTimeMillis();
            shouldThrow = false;
            throwSlot = -1;
        }
    };

    private int findBestPot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof ItemPotion)) continue;
            ItemPotion pot = (ItemPotion) stack.getItem();
            if (healOnly.getValue()) {
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
