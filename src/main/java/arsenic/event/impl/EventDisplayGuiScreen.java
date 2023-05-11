package arsenic.event.impl;

import arsenic.event.types.CancellableEvent;
import net.minecraft.client.gui.GuiScreen;

public class EventDisplayGuiScreen extends CancellableEvent {

    private final GuiScreen guiScreen;

    public EventDisplayGuiScreen(GuiScreen guiScreen) {
        this.guiScreen = guiScreen;
    }

    public GuiScreen getGuiScreen() {
        return guiScreen;
    }
}
