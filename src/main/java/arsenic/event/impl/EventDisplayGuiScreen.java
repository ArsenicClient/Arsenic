package arsenic.event.impl;

import arsenic.event.types.CancellableEvent;
import net.minecraft.client.gui.screens.Screen;

public class EventDisplayGuiScreen extends CancellableEvent {

    private Screen guiScreen;

    public EventDisplayGuiScreen(Screen guiScreen) {
        this.guiScreen = guiScreen;
    }

    public Screen getGuiScreen() {
        return guiScreen;
    }

    public void setGuiScreen(Screen guiScreen) {
        this.guiScreen = guiScreen;
    }
}
