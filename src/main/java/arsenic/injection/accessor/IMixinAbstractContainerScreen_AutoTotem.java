package arsenic.injection.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the slot under the mouse in a container screen, for AutoTotem's Legit switching. */
@Mixin(AbstractContainerScreen.class)
public interface IMixinAbstractContainerScreen_AutoTotem {

    @Accessor("hoveredSlot")
    Slot arsenic$getHoveredSlotAutoTotem();
}
