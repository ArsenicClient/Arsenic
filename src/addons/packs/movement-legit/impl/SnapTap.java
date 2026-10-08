import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventMovementInput;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import net.minecraft.client.settings.KeyBinding;

/**
 * Resolves opposing keys the way SOCD keyboards do: when W and S are both held, the one pressed most recently wins, and
 * when A and D are both held, the same. The addon only changes the input when both keys of an axis are held. Nothing is
 * injected: the server sees the same keys you pressed, in the order a keyboard would send them.
 */
@ModuleInfo(name = "SnapTap", description = "Last-pressed key wins for W/S and A/D, like SOCD keyboards", category = ModuleCategory.MOVEMENT)
public class SnapTap extends Module {

    private long counter;
    private long wOrder, sOrder, aOrder, dOrder;
    private boolean wWas, sWas, aWas, dWas;

    @RequiresPlayer
    @EventLink
    public final Listener<EventMovementInput> onInput = event -> {
        KeyBinding w = mc.gameSettings.keyBindForward, s = mc.gameSettings.keyBindBack;
        KeyBinding a = mc.gameSettings.keyBindLeft, d = mc.gameSettings.keyBindRight;

        boolean wNow = w.isKeyDown(), sNow = s.isKeyDown(), aNow = a.isKeyDown(), dNow = d.isKeyDown();
        if (wNow && !wWas) wOrder = ++counter;
        if (sNow && !sWas) sOrder = ++counter;
        if (aNow && !aWas) aOrder = ++counter;
        if (dNow && !dWas) dOrder = ++counter;
        wWas = wNow;
        sWas = sNow;
        aWas = aNow;
        dWas = dNow;

        if (wNow && sNow) event.setSpeed(wOrder > sOrder ? 1f : -1f);
        if (aNow && dNow) event.setStrafe(aOrder > dOrder ? 1f : -1f);
    };
}
