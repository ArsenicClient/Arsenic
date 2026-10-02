package arsenic.module.impl.visual;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.EnumProperty;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

@ModuleInfo(name = "FullBright", category = ModuleCategory.RENDER, keybind = InputConstants.KEY_F, hidden = true)
public class FullBright extends Module {
    public final EnumProperty<fEnum> fullbrightmode = new EnumProperty<>("Mode: ",fEnum.Gamma );

    /** Read by MixinLightmapRenderStateExtractor while the lightmap is built. */
    public boolean isGammaActive() {
        return isEnabled() && fullbrightmode.getValue() == fEnum.Gamma;
    }

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (fullbrightmode.getValue().equals(fEnum.Potion)) {
            if (mc.player.getEffect(MobEffects.NIGHT_VISION) == null) {
                mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 69420));
            }
        }
    };

    @Override
    protected void onDisable() {
        if (mc.player != null)
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
    }

    public enum fEnum {
        Gamma,Potion
    }
}
