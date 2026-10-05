package arsenic.module.impl.visual.custommainmenu;

import net.minecraft.client.Minecraft;

/**
 * Swaps the vanilla main menu for the client's own. Not a module: there is nothing to switch, it is
 * always active, and the screen's "Vanilla Menu" button asks for the real one once at a time.
 */
public final class CustomMenu {

    private static boolean vanillaOnce;

    private CustomMenu() {}

    public static void showVanillaNext() {
        vanillaOnce = true;
    }

    public static boolean consumeVanillaRequest() {
        boolean v = vanillaOnce;
        vanillaOnce = false;
        return v;
    }

    public static void display() {
        Minecraft.getMinecraft().displayGuiScreen(new Screen());
    }

}
