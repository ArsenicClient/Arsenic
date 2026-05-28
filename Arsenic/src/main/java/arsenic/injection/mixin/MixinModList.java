package arsenic.injection.mixin;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(FMLHandshakeMessage.ModList.class)
public abstract class MixinModList extends FMLHandshakeMessage  {
    @Shadow(remap = false)
    private Map<String, String> modTags;

    @Inject(method = "toBytes", at = @At(value = "HEAD"), remap = false, cancellable = true)
    public void toBytes(ByteBuf buffer, CallbackInfo ci) {
        if(Minecraft.getMinecraft().isSingleplayer())
            return;
        super.toBytes(buffer);
        modTags.remove("arsenic");
        ByteBufUtils.writeVarInt(buffer, modTags.size(), 2);
        for (Map.Entry<String, String> modTag : modTags.entrySet()) {
            ByteBufUtils.writeUTF8String(buffer, modTag.getKey());
            ByteBufUtils.writeUTF8String(buffer, modTag.getValue());
        }
        ci.cancel();
    }
}