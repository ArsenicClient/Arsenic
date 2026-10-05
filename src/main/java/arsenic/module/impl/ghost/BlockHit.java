package arsenic.module.impl.ghost;

import arsenic.utils.minecraft.PlayerUtils;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventAttack;
import arsenic.event.impl.EventRunTick;
import arsenic.event.impl.EventSilentRotation;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import arsenic.utils.minecraft.ItemUtils;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

import static arsenic.utils.lag.LagManager.getPing;


@ModuleInfo(name = "BlockHit", category = ModuleCategory.COMBAT)
public class BlockHit extends Module {
    public final DoubleProperty hurtime = new DoubleProperty("Hurtime", new DoubleValue(0.0, 10.0, 2.0, 1.0));

    public final EnumProperty<mode> blockType = new EnumProperty<>("Mode", mode.Legit);

    @Override
    public String getHudInfo() {
        return blockType.getValue().name().toLowerCase();
    }
    public LivingEntity target;
    public boolean down;
    public int swapHurtTime = 1;
    public boolean hasSwapped = false;

    @EventLink
    public final Listener<EventAttack> eventAttackListener = event -> {
        if (event.getTarget() != null && event.getTarget() instanceof LivingEntity) {
            target = (LivingEntity) event.getTarget();
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

        HitResult mop = event.getRayTraceEntity();
        boolean aimingAtPlayer = mop instanceof net.minecraft.world.phys.EntityHitResult entityHit
                && entityHit.getEntity() instanceof Player
                && entityHit.getEntity() != mc.player;

        List<Integer> swords = swordSlots();
        if (!aimingAtPlayer || swords.isEmpty()) {
            if (down) release();
            return;
        }

        if (!down) press();

        if (swords.size() >= 2 && target != null && !hasSwapped && target.hurtTime == swapHurtTime) {
            int current = mc.player.getInventory().getSelectedSlot();
            int next = current == swords.get(0) ? swords.get(1) : swords.get(0);
            mc.player.getInventory().setSelectedSlot(next);
            hasSwapped = true;
        }
    };

    private List<Integer> swordSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != null && ItemUtils.isSword(stack))
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
        return PlayerUtils.isPlayerHoldingSword();
    }

    private void release() {
        mc.options.keyUse.setDown(false);
        down = false;
        target = null;
    }

    private void press() {
        down = true;
        mc.options.keyUse.setDown(true);
    }



    public enum mode {
        Legit,
        TwoSword,
    }
}