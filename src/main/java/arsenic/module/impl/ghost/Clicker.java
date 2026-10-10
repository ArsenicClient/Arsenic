package arsenic.module.impl.ghost;

import arsenic.utils.keystrokes.SyntheticKeys;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventLiving;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRunTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.rangeproperty.RangeProperty;
import arsenic.module.property.impl.rangeproperty.RangeValue;
import arsenic.utils.java.SoundUtils;
import arsenic.utils.click.ClickManager;
import arsenic.utils.minecraft.PlayerUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.MovingObjectPosition;

@ModuleInfo(name = "Clicker", category = ModuleCategory.COMBAT)
public class Clicker extends Module {

    public final RangeProperty rangeProperty = new RangeProperty("Cps", new RangeValue(1, 20, 7, 9, 1));
    // Chance a click is followed by a second one inside the same tick, as jitter and butterfly clicking do
    private static final double DOUBLE_CLICK_CHANCE = 0.15;
    private final MSTimer soundTimer = new MSTimer();
    private boolean lmbDown;
    
    public final BooleanProperty weaponOnly = new BooleanProperty("Weapon Only", true);

    @Override
    public String getHudInfo() {
        return lmbDown ? String.format("%.1f cps", ClickManager.get().getMedian(ClickManager.Client.CLICKER)) : rangeProperty.getValueString();
    }

    private boolean shouldPlayClickSound() {
        if (!mc.gameSettings.keyBindAttack.isKeyDown() || mc.currentScreen != null)
            return false;
        if (mc.playerController != null && ((arsenic.injection.accessor.IMixinPlayerControllerMp) mc.playerController).isHittingBlock())
            return false;
        return mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK;
    }

    @EventLink
    public final Listener<EventLiving> eventLivingListener = e -> {
        lmbDown = mc.gameSettings.keyBindAttack.isKeyDown();
    };
    
    @RequiresPlayer
    @EventLink(Priorities.VERY_LOW)
    public final Listener<EventRender2D> eventRunTickListener = e -> {
        if (!lmbDown)
            return;

        if (weaponOnly.getValue() && !PlayerUtils.isPlayerHoldingWeapon())
            return;

        ClickManager clicks = ClickManager.get();
        if (clicks.isDue(ClickManager.Client.CLICKER)) {
            if (shouldPlayClickSound() && soundTimer.finished(80)) {
                SoundUtils.playSound("click");
                soundTimer.reset();
            }
            clickOnce();
            clicks.onClick(ClickManager.Client.CLICKER, rangeProperty.getValue(), DOUBLE_CLICK_CHANCE);
        }
    };

    private void clickOnce() {
        KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
        SyntheticKeys.press(SyntheticKeys.Key.LMB);
    }
}
