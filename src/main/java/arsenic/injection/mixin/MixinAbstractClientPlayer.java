package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.ModuleManager;
import arsenic.module.impl.client.Cape;
import arsenic.module.impl.client.CapeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
@SideOnly(Side.CLIENT)
public abstract class MixinAbstractClientPlayer {

    @Inject(method = "getLocationCape", at = @At("HEAD"), cancellable = true)
    private void getCape(CallbackInfoReturnable<ResourceLocation> cir) {
        if (Minecraft.getMinecraft().thePlayer != (Object) this) return;
        Arsenic arsenic = Arsenic.getInstance();
        if (arsenic == null) return;
        ModuleManager moduleManager = arsenic.getModuleManager();
        if (moduleManager == null) return;
        Cape cape = moduleManager.getModuleByClass(Cape.class);
        if (cape != null && cape.isEnabled()) {
            CapeHandler capeHandler = CapeHandler.getInstance();
            if (capeHandler.hasCape()) {
                cir.setReturnValue(capeHandler.getCapeLocation());
            }
        }
    }
}
