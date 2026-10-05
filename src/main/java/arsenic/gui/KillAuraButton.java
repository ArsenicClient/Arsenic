package arsenic.gui;

import arsenic.main.Arsenic;
import arsenic.module.impl.blatant.KillAura;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

/** A KillAura on/off button in the top-left corner of every container screen. */
public final class KillAuraButton {

    private KillAuraButton() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((mc, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?>) || killAura() == null)
                return;
            Button button = Button.builder(label(), b -> {
                KillAura aura = killAura();
                if (aura != null)
                    aura.toggle();
                b.setMessage(label());
            }).bounds(4, 4, 92, 20).build();
            Screens.getWidgets(screen).add(button);
            // the module can also be toggled by its keybind while the screen is open
            ScreenEvents.afterTick(screen).register(s -> button.setMessage(label()));
        });
    }

    private static KillAura killAura() {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
    }

    private static Component label() {
        KillAura aura = killAura();
        return Component.literal("KillAura: " + (aura != null && aura.isEnabled() ? "ON" : "OFF"));
    }
}
