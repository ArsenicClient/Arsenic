package arsenic.injection.mixin;

import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Container.class)
public class MixinContainer {

    @Inject(method = "slotClick", at = @At("HEAD"))
    public void slotClick(int slotId, int clickedButton, int mode, EntityPlayer playerIn, CallbackInfoReturnable<ItemStack> cir) {
        PlayerUtils.addWaterMarkedMessageToChat(slotId + " " + clickedButton + " " + mode);
    }

}
