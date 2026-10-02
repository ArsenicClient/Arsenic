package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.client.Cape;
import arsenic.module.impl.client.CapeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class MixinAbstractClientPlayer {

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void arsenic$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        if (Minecraft.getInstance().player != (Object) this)
            return;
        Cape cape = Arsenic.getInstance().getModuleManager().getModuleByClass(Cape.class);
        CapeHandler capeHandler = CapeHandler.getInstance();
        if (cape == null || !cape.isEnabled() || !capeHandler.hasCape())
            return;
        PlayerSkin skin = cir.getReturnValue();
        cir.setReturnValue(new PlayerSkin(skin.body(), capeHandler.getCape(), skin.elytra(), skin.model(), skin.secure()));
    }
}
