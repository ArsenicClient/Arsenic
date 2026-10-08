import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import net.minecraft.potion.Potion;

/**
 * Removes the blindness and nausea effects on your client, so the dark fog and the wobbling screen do not show. The
 * server still applies the effects; this only removes what you see. Removing them again each tick keeps them off when
 * the server resends them.
 */
@ModuleInfo(name = "AntiBlind", description = "Removes the blindness and nausea effects on your client", category = ModuleCategory.RENDER)
public class AntiBlind extends Module {

    public final BooleanProperty blindness = new BooleanProperty("Blindness", true);
    public final BooleanProperty nausea = new BooleanProperty("Nausea", true);

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (blindness.getValue() && mc.thePlayer.isPotionActive(Potion.blindness)) {
            mc.thePlayer.removePotionEffectClient(Potion.blindness.getId());
        }
        if (nausea.getValue() && mc.thePlayer.isPotionActive(Potion.confusion)) {
            mc.thePlayer.removePotionEffectClient(Potion.confusion.getId());
        }
    };
}
