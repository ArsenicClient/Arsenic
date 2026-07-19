package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRunTick;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.util.MovingObjectPosition;

import java.util.ArrayList;
import java.util.List;

import static arsenic.utils.lag.LagManager.getPing;

/**
 * @author Kv
 * @since 1/4/26 (Aus)
 */

@ModuleInfo(name = "BlockHit", category = ModuleCategory.GHOST)
public class BlockHit extends Module {
    public final EnumProperty<mode> blockType = new EnumProperty<>("Mode", mode.Legit);
    public final BooleanProperty rmb = new BooleanProperty("RightClick Only",false);
    public final DoubleProperty hurtime = new DoubleProperty("Hurtime", new DoubleValue(0.0, 10.0, 2.0, 1.0));
    public EntityLivingBase target;
    public boolean down;
    // TwoSword timing: mirrors SprintReset's hurtTime gate so we swap only when it's time to attack.
    public int swapHurtTime = 1;
    public boolean hasSwapped = false;

    @EventLink
    public final Listener<EventAttack> eventAttackListener = event -> {
        if (event.getTarget() != null && event.getTarget() instanceof EntityLivingBase) {
            target = (EntityLivingBase) event.getTarget();
            hasSwapped = false;
            swapHurtTime = Math.max(1, getPing() / 20) + 1;
        }
    };


    @RequiresPlayer
    @EventLink
    public final Listener<EventRunTick> eventTickListener = event -> {
        if(blockType.getValue() != mode.Legit)
            return;
        if(!isPlayerHoldingSword())
            return;
        if(target == null) {
            if(down) release();
            return;
        }
        if(!down && target.hurtTime > hurtime.getValue().getInput()) {
            press();
            return;
        }
        if(down && target.hurtTime <= hurtime.getValue().getInput()) {
            release();
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventSilentRotation.Post> twoSwordListener = event -> {
        if (blockType.getValue() != mode.TwoSword)
            return;

        // Aiming at another player, resolved from the silent-rotation raytrace (blocks + entities).
        MovingObjectPosition mop = event.getRayTraceEntity();
        boolean aimingAtPlayer = mop != null
                && mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && mop.entityHit instanceof EntityPlayer
                && mop.entityHit != mc.thePlayer;

        List<Integer> swords = swordSlots();
        if (!aimingAtPlayer || swords.isEmpty()) {
            if (down) release();
            return;
        }

        // Keep blocking (hold right click) the whole time we're aimed at a player.
        if (!down) press();

        // Swap between the two swords ONLY when the hurtTime gate says it's time to attack again —
        // the same timing SprintReset uses — so we swap once per hit cycle, not every tick.
        if (swords.size() >= 2 && target != null && !hasSwapped && target.hurtTime == swapHurtTime) {
            int current = mc.thePlayer.inventory.currentItem;
            int next = current == swords.get(0) ? swords.get(1) : swords.get(0);
            mc.thePlayer.inventory.currentItem = next;
            hasSwapped = true;
        }
    };

    /** Hotbar slots (0-8) that currently hold a sword. */
    private List<Integer> swordSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof ItemSword)
                slots.add(i);
        }
        return slots;
    }

    @Override
    protected void onDisable() {
        if (down) release();
        super.onDisable();
    }

    public boolean isPlayerHoldingSword() {
        return (mc.thePlayer.getCurrentEquippedItem() != null)
                && (mc.thePlayer.getCurrentEquippedItem().getItem() instanceof ItemSword);
    }

    private void release() {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, false);
        down = false;
        target = null;
    }

    private void press() {
        down = true;
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(key, true);
        //KeyBinding.onTick(key);
    }



    public enum mode {
        Legit,
        TwoSword,
    }
}