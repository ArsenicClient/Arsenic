package arsenic.module.impl.movement;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.impl.world.Scaffold;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.potion.Potion;

@ModuleInfo(name = "Sprint", category = ModuleCategory.MOVEMENT, hidden = true)
public class Sprint extends Module {
    public final EnumProperty<sMode> sprintMode = new EnumProperty<>("Mode: ", sMode.Legit);
    public final BooleanProperty allDirections = new BooleanProperty("All Directions", false);
    public final BooleanProperty ignoreBlindness = new BooleanProperty("Ignore Blindness", false);

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (allDirections.getValue()) {
            mc.thePlayer.setSprinting(true);
        }

        if (ignoreBlindness.getValue() && mc.thePlayer.isPotionActive(Potion.blindness)) {
            mc.thePlayer.setSprinting(true);
        }

        sprintMode.getValue().setSprinting();
    };

    @Override
    protected void onDisable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
        mc.thePlayer.setSprinting(false);
    }

    public enum sMode {
        Omni(() -> mc.thePlayer.setSprinting(true)),
        Legit(() -> KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), true));

        private final Runnable v;

        sMode(Runnable v) {
            this.v = v;
        }

        public void setSprinting() {
            v.run();
        }
    }
}
