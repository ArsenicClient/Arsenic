package arsenic.runtime;

import arsenic.utils.render.capture.SilentView;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

/** The Forge part of {@link InjectedLaunch}, kept apart so other games never load Forge's classes. */
final class ForgeLaunch {

    private ForgeLaunch() {}

    /** Runs the client's start-up as Minecraft's mod: Forge's event bus wants to know which mod registers a listener. */
    static void asMinecraft(Runnable init) {
        Object controller = Access.field(Loader.class, "modController").get(Loader.instance());
        Access.FieldRef active = Access.field(controller.getClass(), "activeContainer");
        ModContainer previous = active.get(controller);
        active.set(controller, Loader.instance().getMinecraftModContainer());
        try {
            init.run();
            SilentView.register();
        } finally {
            active.set(controller, previous);
        }
    }
}
