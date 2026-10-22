package arsenic.module.impl.ghost;

import arsenic.module.ModuleTier;
import arsenic.utils.keystrokes.SyntheticKeys;
import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
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
import net.minecraft.client.KeyMapping;
import net.minecraft.world.phys.HitResult;

@ModuleInfo(name = "Clicker", category = ModuleCategory.COMBAT, tier = ModuleTier.LEGIT)
public class Clicker extends Module {

    public final RangeProperty rangeProperty = new RangeProperty("Cps", new RangeValue(1, 20, 7, 9, 1));
    private final MSTimer soundTimer = new MSTimer();
    private boolean lmbDown;
    
    public final BooleanProperty weaponOnly = new BooleanProperty("Weapon Only", true);
    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty clickSounds = new BooleanProperty("Click Sounds", true);

    @Override
    public String getHudInfo() {
        return lmbDown ? String.format("%.1f cps", ClickManager.get().getMedian(ClickManager.Client.CLICKER)) : rangeProperty.getValueString();
    }

    private boolean shouldPlayClickSound() {
        if (!mc.options.keyAttack.isDown() || mc.gui.screen() != null)
            return false;
        if (mc.gameMode != null && mc.gameMode.isDestroying())
            return false;
        return mc.hitResult == null
                || mc.hitResult.getType() != HitResult.Type.BLOCK;
    }

    @EventLink
    public final Listener<EventLiving> eventLivingListener = e -> {
        lmbDown = mc.options.keyAttack.isDown();
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
            if (clickSounds.getValue() && shouldPlayClickSound() && soundTimer.finished(80)) {
                SoundUtils.playSound("click");
                soundTimer.reset();
            }
            clickOnce();
            clicks.onClick(ClickManager.Client.CLICKER, rangeProperty.getValue());
        }
    };

    private void clickOnce() {
        KeyMapping.click(((arsenic.injection.accessor.IMixinKeyMapping) mc.options.keyAttack).getBoundKey());
        SyntheticKeys.press(SyntheticKeys.Key.LMB);
    }
}
