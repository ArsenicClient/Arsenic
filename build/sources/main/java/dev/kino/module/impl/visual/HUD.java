package dev.kino.module.impl.visual;

import dev.kino.event.impl.EventKey;
import dev.kino.event.impl.EventRender2D;
import dev.kino.main.Kino;
import dev.kino.module.Module;
import dev.kino.module.ModuleCategory;
import dev.kino.module.ModuleInfo;
import dev.kino.utils.font.TTFontRenderer;
import dev.kino.utils.interfaces.IFontRenderer;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "HUD", category = ModuleCategory.VISUAL, keybind = Keyboard.KEY_Y)
public class HUD extends Module {

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {

        IFontRenderer mcFr = (IFontRenderer) mc.fontRendererObj;
        TTFontRenderer ttfFr = Kino.getInstance().getFonts().FR;

        mcFr.drawStringWithShadow("Test123", 4, 4, -1);
        ttfFr.drawStringWithShadow("Test123", 4, 16, -1);

    };

    @Override
    protected void onEnable() {
        mc.thePlayer.sendChatMessage("enable h");
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        mc.thePlayer.sendChatMessage("disable h");
        super.onDisable();
    }

}
