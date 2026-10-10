import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.timer.MSTimer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

/**
 * Keeps a fishing rod going: casts when the line is not out, and reels in when the bobber is pulled down (the bite).
 * Switches to a rod in the hotbar when needed and puts your slot back when disabled. Every action waits a random delay.
 * A bite is read from the bobber's downward motion, which is the usual sign and not checked on a server.
 */
@ModuleInfo(name = "AutoFish", description = "Casts a fishing rod and reels in when you get a bite", category = ModuleCategory.PLAYER)
public class AutoFish extends Module {

    public final DoubleProperty delay = new DoubleProperty("Delay (ms)", new DoubleValue(100, 1000, 300, 10));
    public final BooleanProperty switchToRod = new BooleanProperty("Switch To Rod", true);

    private final MSTimer action = MSTimer.expired();
    private int savedSlot = -1;
    private long nextWait;

    @Override
    protected void onDisable() {
        if (savedSlot != -1 && mc.thePlayer != null) mc.thePlayer.inventory.currentItem = savedSlot;
        savedSlot = -1;
    }

    @Override
    public boolean isSwappingHotbar() {
        return savedSlot != -1;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.currentScreen != null) return;

        ItemStack held = mc.thePlayer.inventory.getCurrentItem();
        if (held == null || held.getItem() != Items.fishing_rod) {
            if (!switchToRod.getValue()) return;
            int slot = findRod();
            if (slot == -1) return;
            if (savedSlot == -1) savedSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = slot;
            return;
        }

        if (!action.hasTimeElapsed(nextWait, false)) return;
        if (mc.thePlayer.fishEntity == null) {
            cast();
        } else if (mc.thePlayer.fishEntity.motionY < -0.04 || mc.thePlayer.fishEntity.ticksExisted > 800) {
            cast();   // a bite (or a line left out too long): reel in, which the next cast then replaces
        }
    };

    private void cast() {
        ItemStack rod = mc.thePlayer.inventory.getCurrentItem();
        if (rod != null) mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, rod);
        action.reset();
        double base = delay.getValue().getInput();
        nextWait = (long) (base * (0.8 + Math.random() * 0.4)) + 800;
    }

    private static int findRod() {
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[i];
            if (s != null && s.getItem() == Items.fishing_rod) return i;
        }
        return -1;
    }
}
