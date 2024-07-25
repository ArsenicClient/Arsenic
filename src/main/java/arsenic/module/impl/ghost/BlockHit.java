package arsenic.module.impl.ghost;

import arsenic.asm.RequiresPlayer;
import arsenic.main.Arsenic;
import arsenic.module.impl.world.Scaffold;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import org.lwjgl.input.Mouse;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.*;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.minecraft.PlayerUtils;

/**
 * @author Cosmic
 * @since 25/7/2024
 */

@ModuleInfo(name = "BlockHit", category = ModuleCategory.GHOST)
public class BlockHit extends Module {
    public final EnumProperty<mode> blockType = new EnumProperty<>("Mode", mode.Normal);
    public final BooleanProperty rmb = new BooleanProperty("RightClick Only",false);
    public final RangeProperty blockTime = new RangeProperty("Block For", new RangeValue(0, 300, 90,109, 1));
    public final RangeProperty blockAfterHits = new RangeProperty("Block After Hits", new RangeValue(1, 10, 2,3, 1));
    MSTimer blockingTimer = new MSTimer();
    int hits;

    @RequiresPlayer
    @EventLink
    public final Listener<EventLiving> livingEventListener = event -> {
        if (Arsenic.getInstance().getModuleManager().getModuleByClass(Scaffold.class).isEnabled()) return;
        if (mc.objectMouseOver == null) return;

        if (isAutoblock()){
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(),Mouse.isButtonDown(0));
            return;
        }

        if ((rmb.getValue() && !Mouse.isButtonDown(1)) || !PlayerUtils.isPlayerHoldingSword() || mc.objectMouseOver.entityHit == null){
            hits = 0;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(),Mouse.isButtonDown(1));
            return;
        }

        if (hits >= blockAfterHits.getValue().getRandomInRange()){
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(),true);
            if (blockingTimer.hasTimeElapsed((long) blockTime.getValue().getRandomInRange())){
                hits = 0;
                blockingTimer.reset();
            }
            return;
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(),Mouse.isButtonDown(1));
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventAttack> attackEventListener = event -> {
        if (mc.thePlayer.isBlocking() || isAutoblock()) return;
        if (event.getTarget() instanceof EntityLivingBase) {
            if (((EntityLivingBase) event.getTarget()).hurtTime > 0) {
                hits++;
            }
        }
    };

    public boolean isAutoblock(){
        return blockType.getValue().equals(mode.AutoBlock);
    }
    public enum mode {
        Normal,
        AutoBlock
    }
}