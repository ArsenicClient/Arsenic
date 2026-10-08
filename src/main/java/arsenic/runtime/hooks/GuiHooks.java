package arsenic.runtime.hooks;

import arsenic.runtime.Access;
import arsenic.main.Arsenic;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.impl.visual.custommainmenu.MenuTheme;
import arsenic.module.impl.visual.custommainmenu.ScreenTransition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.achievement.GuiAchievement;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;

import java.util.List;

/** Screen and widget hooks. */
public final class GuiHooks {

    private static final Access.FieldRef INPUT_FIELD = Access.field(GuiChat.class, "inputField");
    private static final Access.FieldRef BUTTON_LIST = Access.field(GuiScreen.class, "buttonList");
    private static final Access.FieldRef HOVERED = Access.field(GuiButton.class, "hovered");
    private static final Access.MethodRef MOUSE_DRAGGED = Access.method(GuiButton.class, "mouseDragged", Minecraft.class, int.class, int.class);
    // protected in Minecraft (Forge makes them public), so read by reflection to work in every game
    private static final Access.FieldRef SLOT_WIDTH = Access.field(GuiSlot.class, "width");
    private static final Access.FieldRef SLOT_TOP = Access.field(GuiSlot.class, "top");
    private static final Access.FieldRef SLOT_BOTTOM = Access.field(GuiSlot.class, "bottom");
    private static final Access.FieldRef SLOT_LEFT = Access.field(GuiSlot.class, "left");
    private static final Access.FieldRef SLOT_RIGHT = Access.field(GuiSlot.class, "right");

    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");

    // chat completion preview, computed on key press and drawn with the screen
    private static String trimmedAutoCompletion;
    private static String lastArg;
    private static Boolean isLastArgValidArg;

    // only one container screen is open at a time
    private static final int KILLAURA_BUTTON_ID = 7331;
    private static GuiButton killAuraButton;

    private GuiHooks() {}

    // ---- GuiChat ----

    /** GuiChat.keyTyped RETURN. */
    public static void chatKeyTypedReturn(GuiChat self, char typedChar, int keyCode) {
        GuiTextField inputField = INPUT_FIELD.get(self);
        if (inputField.getText().startsWith(".")) {
            inputField.setTextColor(Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor());

            if (keyCode != 15 && keyCode != 1) {
                Arsenic.getArsenic().getCommandManager().updateAutoCompletions(inputField.getText());
            }

            String latestAutoCompletion = Arsenic.getArsenic().getCommandManager().getAutoCompletionWithoutRotation();
            lastArg = inputField.getText().substring(inputField.getText().lastIndexOf((inputField.getText().contains(" ") ? ' ' : '.')) + 1);
            // lastArg is whatever was typed, so it must not be read as a regex (a stray \ or [ crashed the game)
            trimmedAutoCompletion = latestAutoCompletion.toLowerCase().replaceFirst(java.util.regex.Pattern.quote(lastArg.toLowerCase()), "");
            isLastArgValidArg = (trimmedAutoCompletion.length() == latestAutoCompletion.length() || latestAutoCompletion.length() < lastArg.length()) && lastArg.length() != 0;
        } else {
            inputField.setTextColor(0xE0E0E0);
        }
    }

    /** GuiChat.keyTyped HEAD. @return true to cancel */
    public static boolean chatKeyTypedHead(GuiChat self, char typedChar, int keyCode) {
        GuiTextField inputField = INPUT_FIELD.get(self);
        if (inputField.getText().startsWith(".") && keyCode == 15) {
            inputField.setText(inputField.getText().substring(0,
                    inputField.getText().lastIndexOf((inputField.getText().contains(" ") ? ' ' : '.')) + 1));
            inputField.writeText(Arsenic.getArsenic().getCommandManager().getAutoCompletion());
            chatKeyTypedReturn(self, typedChar, keyCode);
            return true;
        }
        return false;
    }

    /** GuiChat.drawScreen RETURN. */
    public static void chatDrawScreenReturn(GuiChat self, int mouseX, int mouseY, float partialTicks) {
        GuiTextField inputField = INPUT_FIELD.get(self);
        if (!inputField.getText().startsWith(".") || trimmedAutoCompletion == null)
            return;
        FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
        if (isLastArgValidArg) {
            font.drawStringWithShadow(
                    trimmedAutoCompletion,
                    inputField.xPosition + font.getStringWidth(inputField.getText().replace(lastArg, "")),
                    inputField.yPosition - font.FONT_HEIGHT * 1.2f,
                    0x999999
            );
        } else {
            font.drawStringWithShadow(
                    trimmedAutoCompletion,
                    inputField.xPosition + font.getStringWidth(inputField.getText()),
                    inputField.yPosition,
                    0x999999);
        }
    }

    // ---- GuiContainer ----

    private static KillAura killAura() {
        return Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
    }

    private static String killAuraLabel() {
        KillAura aura = killAura();
        return "KillAura: " + (aura != null && aura.isEnabled() ? "ON" : "OFF");
    }

    /** GuiContainer.initGui TAIL. */
    public static void containerInitGuiTail(GuiContainer self) {
        killAuraButton = null;
        if (killAura() == null)
            return;
        killAuraButton = new GuiButton(KILLAURA_BUTTON_ID, 4, 4, 92, 20, killAuraLabel());
        List<GuiButton> buttonList = BUTTON_LIST.get(self);
        buttonList.add(killAuraButton);
    }

    /** GuiContainer.drawScreen HEAD. */
    public static void containerDrawScreenHead(GuiContainer self, int mouseX, int mouseY, float partialTicks) {
        if (killAuraButton != null)
            killAuraButton.displayString = killAuraLabel();
    }

    /** GuiContainer.mouseClicked HEAD (GuiContainer doesn't declare actionPerformed). @return true to cancel */
    public static boolean containerMouseClickedHead(GuiContainer self, int mouseX, int mouseY, int mouseButton) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mouseButton != 0 || killAuraButton == null || !killAuraButton.mousePressed(mc, mouseX, mouseY))
            return false;
        killAuraButton.playPressSound(mc.getSoundHandler());
        KillAura aura = killAura();
        if (aura != null)
            aura.toggle();
        killAuraButton.displayString = killAuraLabel();
        return true;
    }

    // ---- GuiScreen ----

    /** GuiScreen.drawWorldBackground HEAD: the plain dark veil is replaced by the ocean theme. @return true */
    public static boolean drawWorldBackground(GuiScreen self, int tint) {
        MenuTheme.drawBackground(self.width, self.height);
        return true;
    }

    /** GuiScreen.drawBackground HEAD: the dirt tile is replaced by the ocean theme. @return true */
    public static boolean drawBackground(GuiScreen self, int tint) {
        MenuTheme.drawBackground(self.width, self.height);
        return true;
    }

    /** GuiScreen.sendChatMessage(String, boolean) HEAD. @return true to cancel */
    public static boolean sendChatMessage(GuiScreen self, String msg, boolean addToChat) {
        if (!msg.startsWith("."))
            return false;
        Arsenic.getInstance().getCommandManager().executeCommand(msg);
        Minecraft.getMinecraft().ingameGUI.getChatGUI().addToSentMessages(msg);
        return true;
    }

    // ---- lists, buttons, sliders ----

    /** GuiSlot.drawContainerBackground HEAD (added by Forge). @return true */
    public static boolean slotContainerBackground(GuiSlot self, Tessellator tessellator) {
        MenuTheme.drawListPane(SLOT_LEFT.getInt(self), SLOT_TOP.getInt(self), SLOT_RIGHT.getInt(self), SLOT_BOTTOM.getInt(self));
        return true;
    }

    /** GuiSlot.overlayBackground HEAD. @return true */
    public static boolean slotOverlayBackground(GuiSlot self, int startY, int endY, int startAlpha, int endAlpha) {
        int left = SLOT_LEFT.getInt(self);
        MenuTheme.drawListBar(left, left + SLOT_WIDTH.getInt(self), startY, endY);
        return true;
    }

    /** GuiButton.drawButton HEAD: every vanilla button is drawn as a themed pill. @return true */
    public static boolean drawButton(GuiButton self, Minecraft mc, int mouseX, int mouseY) {
        if (self.visible) {
            HOVERED.setBoolean(self, MenuTheme.drawButton(self, mc, mouseX, mouseY));
            // sliders do their dragging (and used to draw their knob) here; the sound sliders draw a vanilla
            // knob without binding a texture themselves, so give them the widget sheet
            mc.getTextureManager().bindTexture(WIDGETS);
            GlStateManager.color(1f, 1f, 1f, 1f);
            MOUSE_DRAGGED.invoke(self, mc, mouseX, mouseY);
        }
        return true;
    }

    /** GuiOptionSlider.mouseDragged: the first drawTexturedModalRect (left knob half) becomes one themed pill. */
    public static void sliderKnob(GuiOptionSlider self, int x, int y, int u, int v, int w, int h) {
        MenuTheme.drawSlider(self.xPosition, x, y, 20);
    }

    /** GuiOptionSlider.mouseDragged: the second drawTexturedModalRect (right knob half) is dropped. */
    public static void sliderKnobSecondHalf(GuiOptionSlider self, int x, int y, int u, int v, int w, int h) {
    }

    /** GuiAchievement.updateAchievementWindow HEAD: never draws the "Achievement get!" toast. @return true */
    public static boolean updateAchievementWindow(GuiAchievement self) {
        return true;
    }

    /**
     * EntityRenderer.updateCameraAndRender, the call to GuiScreen.drawScreen (games without Forge, which draw the
     * screen there): the same cross-fade as {@link #forgeDrawScreenHead}.
     */
    public static void drawScreen(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        forgeDrawScreenHead(screen, mouseX, mouseY, partialTicks);
        try {
            screen.drawScreen(mouseX, mouseY, partialTicks);
        } finally {
            forgeDrawScreenReturn(screen, mouseX, mouseY, partialTicks);
        }
    }

    /** ForgeHooksClient.drawScreen HEAD: wraps every screen draw with the cross-fade from the previous screen. */
    public static void forgeDrawScreenHead(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        ScreenTransition.beginContent(screen.width, screen.height);
    }

    /** ForgeHooksClient.drawScreen RETURN. */
    public static void forgeDrawScreenReturn(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        ScreenTransition.endContent(screen.width, screen.height);
    }
}
