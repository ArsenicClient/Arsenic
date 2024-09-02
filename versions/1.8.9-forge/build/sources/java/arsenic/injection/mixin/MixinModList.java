package arsenic.injection.mixin;

import arsenic.main.Arsenic;
import arsenic.module.impl.misc.ModSpoofer;
import arsenic.module.property.Property;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mixin(FMLHandshakeMessage.ModList.class)
public abstract class MixinModList {
    @Shadow(remap = false)
    private Map<String, String> modTags;

    @Inject(method = "toBytes", at = @At(value = "HEAD"), cancellable = true, remap = false)
    public void toBytes(ByteBuf buffer, CallbackInfo callbackInfo) {
        ModSpoofer modSpoofer = Arsenic.getArsenic().getModuleManager().getModuleByClass(ModSpoofer.class);
        if (Minecraft.getMinecraft().isSingleplayer() || !modSpoofer.isEnabled()) return;

        callbackInfo.cancel();


        if (!modSpoofer.cancel.getValue()) {
            List<String> mods = modSpoofer.mods.stream().map(Property::getName).collect(Collectors.toList());
            List<Map.Entry<String, String>> shownTags = modTags.entrySet().stream().filter((a) -> !mods.contains(a.getKey())).collect(Collectors.toList());

            ByteBufUtils.writeVarInt(buffer, shownTags.size(), 2);
            for (Map.Entry<String, String> modTag : shownTags) {
                ByteBufUtils.writeUTF8String(buffer, modTag.getKey());
                ByteBufUtils.writeUTF8String(buffer, modTag.getValue());
            }
        }
    }
}