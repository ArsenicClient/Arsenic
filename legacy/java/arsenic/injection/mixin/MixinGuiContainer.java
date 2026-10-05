package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.blatant.KillAura;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiContainer.class)
public abstract class MixinGuiContainer extends GuiScreen {

    private static final int KILLAURA_BUTTON_ID = 7331;

    private GuiButton killAuraButton;

    private static KillAura killAura() {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
    }

    private static String killAuraLabel() {
        KillAura aura = killAura();
        return "KillAura: " + (aura != null && aura.isEnabled() ? "ON" : "OFF");
    }

    @Inject(method = "initGui", at = @At("TAIL"))
    private void addKillAuraButton(CallbackInfo ci) {
        if (killAura() == null)
            return;
        killAuraButton = new GuiButton(KILLAURA_BUTTON_ID, 4, 4, 92, 20, killAuraLabel());
        buttonList.add(killAuraButton);
    }

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void refreshKillAuraLabel(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (killAuraButton != null)
            killAuraButton.displayString = killAuraLabel();
    }

    // GuiContainer doesn't declare actionPerformed, so catch the click here instead
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void killAuraButtonClicked(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (mouseButton != 0 || killAuraButton == null || !killAuraButton.mousePressed(mc, mouseX, mouseY))
            return;
        killAuraButton.playPressSound(mc.getSoundHandler());
        KillAura aura = killAura();
        if (aura != null)
            aura.toggle();
        killAuraButton.displayString = killAuraLabel();
        ci.cancel();
    }
}
