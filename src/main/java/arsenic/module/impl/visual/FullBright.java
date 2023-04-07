package arsenic.module.impl.visual;

import arsenic.module.property.impl.EnumProperty;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.input.Keyboard;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;

@ModuleInfo(name = "FullBright", category = ModuleCategory.WORLD, keybind = Keyboard.KEY_F)
public class FullBright extends Module {
    public final EnumProperty<fEnum> fullbrightmode = new EnumProperty<>("Mode: ",fEnum.Gamma );

    int originalGamma = 0;

    @Override
    protected void onEnable() {
        originalGamma = (int) mc.gameSettings.gammaSetting;
    }
    @Mod.EventHandler
    protected void onTick() {
        if (fullbrightmode.getValue().equals(fEnum.Gamma)) {
            mc.gameSettings.gammaSetting = 1000;
        }
        if (fullbrightmode.getValue().equals(fEnum.Potion)) {
            Potion nightVision = Potion.getPotionFromResourceLocation("night_vision");
            PotionEffect nightVisionEffect = mc.thePlayer.getActivePotionEffect(nightVision);
            if (nightVisionEffect == null) {
                mc.thePlayer.addPotionEffect(new PotionEffect(Potion.nightVision.id, 100));
            }
        }
    }

    @Override
    protected void onDisable() {
        mc.gameSettings.gammaSetting = originalGamma;
    }

    public enum fEnum {
        Gamma,Potion
    }
}
