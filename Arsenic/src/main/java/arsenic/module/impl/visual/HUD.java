package arsenic.module.impl.visual;

import com.google.gson.JsonObject;
import java.awt.*;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventShader;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fml.client.config.GuiButtonExt;

import java.util.function.BinaryOperator;

@ModuleInfo(name = "HUD", category = ModuleCategory.SETTINGS, hidden = true)
public class HUD extends Module {

    public final EnumProperty<hMode> colorMode = new EnumProperty<>("Color Mode: ", hMode.RAINBOW);
    public final EnumProperty<WatermarkMode> watermarkMode = new EnumProperty<>("Watermark: ", WatermarkMode.TEXT);
    public final EnumProperty<ArrayListSort> arraylistSort = new EnumProperty<>("Sort: ", ArrayListSort.LENGTH);
    public final EnumProperty<ArrayListBackground> arraylistBackground = new EnumProperty<>("Background: ", ArrayListBackground.RECTANGLE);
    public final BooleanProperty showCoords = new BooleanProperty("Show Coords", false);
    public final BooleanProperty showKeybinds = new BooleanProperty("Show Keybinds", false);
    public final BooleanProperty editPosition = new BooleanProperty("Edit Position", false);
    public final double opacity = 75;

    public static int arrayListX = 0;
    public static int arrayListY = 0;
    public static int watermarkX = 4;
    public static int watermarkY = 4;
    public static int targetHUDX = 100;
    public static int targetHUDY = 100;
    public static int coordsX = 4;
    public static int coordsY = 60;
    public static int keybindsX = 4;
    public static int keybindsY = 80;
    List<ModuleRenderInfo> nameList;

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (editPosition.getValue() && !(mc.currentScreen instanceof HUDEditorScreen)) {
            mc.displayGuiScreen(new HUDEditorScreen());
            editPosition.setValue(false);
        }
    };

    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat) && !(mc.currentScreen instanceof HUDEditorScreen))
            return;

        ScaledResolution sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();

        if (fr == null) return;

        int noDelayColor = colorMode.getValue().getColor(4, 0);
        float x = sr.getScaledWidth();

        renderWatermark(fr, noDelayColor);

        if (showCoords.getValue() && mc.thePlayer != null)
            renderCoords(fr, noDelayColor);

        if (showKeybinds.getValue())
            renderKeybinds(fr, noDelayColor);

        renderArrayList(fr, sr, x, noDelayColor);
    };

    private void renderWatermark(FontRendererExtension<?> fr, int color) {
        switch (watermarkMode.getValue()) {
            case TEXT:
                fr.drawString("A" + EnumChatFormatting.WHITE + "rsenic", watermarkX, watermarkY, color);
                break;
            case FPS:
                fr.drawString("Arsenic §7[" + Minecraft.getDebugFPS() + " FPS]", watermarkX, watermarkY, color);
                break;
            case COORDS:
                if (mc.thePlayer != null) {
                    String c = String.format("%.0f, %.0f, %.0f", mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
                    fr.drawString("Arsenic §7[" + c + "]", watermarkX, watermarkY, color);
                } else {
                    fr.drawString("A" + EnumChatFormatting.WHITE + "rsenic", watermarkX, watermarkY, color);
                }
                break;
            case IP:
                String ip = "Singleplayer";
                if (mc.getCurrentServerData() != null)
                    ip = mc.getCurrentServerData().serverIP;
                fr.drawString("Arsenic §7[" + ip + "]", watermarkX, watermarkY, color);
                break;
        }
    }

    private void renderCoords(FontRendererExtension<?> fr, int color) {
        String text = String.format("XYZ: %.0f / %.0f / %.0f", mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        fr.drawStringWithShadow(text, coordsX, coordsY, color);
    }

    private void renderKeybinds(FontRendererExtension<?> fr, int color) {
        List<Module> binds = Arsenic.getArsenic().getModuleManager().getModules().stream()
                .filter(m -> m.getKeybind() != 0)
                .sorted(Comparator.comparingInt(Module::getKeybind))
                .collect(Collectors.toList());

        int y = keybindsY;
        for (Module m : binds) {
            String text = m.getName() + " §7[" + GameSettings.getKeyDisplayString(m.getKeybind()) + "]";
            int c = m.isEnabled() ? color : 0x808080;
            fr.drawStringWithShadow(text, keybindsX, y, c);
            y += 10;
        }
    }

    private void renderArrayList(FontRendererExtension<?> fr, ScaledResolution sr, float x, int noDelayColor) {
        nameList = Arsenic.getArsenic().getModuleManager().getEnabledModules()
                .stream().filter(module -> !module.isHidden())
                .map(module -> new ModuleRenderInfo(fr.getWidth(module.getName()), module.getName()))
                .sorted(arraylistSort.getValue().getComparator())
                .collect(Collectors.toList());

        AtomicInteger i = new AtomicInteger();
        float startY = arrayListY;
        nameList.forEach(m -> {
            float mX = x + arrayListX - m.length;
            int color = colorMode.getValue().getColor(4, i.get() * 20);

            switch (arraylistBackground.getValue()) {
                case RECTANGLE:
                    Gui.drawRect((int) (x + arrayListX), (int) (startY + i.get()), (int) mX - 6, (int) (startY + i.get() + 10), new Color(0, 0, 0, (int) opacity).getRGB());
                    break;
                case BAR:
                case NONE:
                    break;
            }

            fr.drawStringWithShadow(m.name, mX - 3, startY + i.get() + 2, color);

            switch (arraylistBackground.getValue()) {
                case RECTANGLE:
                case BAR:
                    Gui.drawRect((int) (x + arrayListX), (int) (startY + i.get()), (int) (x + arrayListX) - 1, (int) (startY + i.get() + 10), color);
                    break;
                case NONE:
                    break;
            }

            i.addAndGet(10);
        });
    }

    @EventLink
    public final Listener<EventShader.Bloom> bloomListener = event -> {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat) && !(mc.currentScreen instanceof HUDEditorScreen))
            return;

        ScaledResolution sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();

        if (fr == null || nameList == null) return;

        float x = sr.getScaledWidth();
        AtomicInteger i = new AtomicInteger();
        float startY = arrayListY;
        nameList.forEach(m -> {
            float mX = x + arrayListX - m.length;
            int color = colorMode.getValue().getColor(4, i.get() * 20);
            Gui.drawRect((int) (x + arrayListX), (int) (startY + i.get()), (int) mX - 6, (int) (startY + i.get() + 10), RenderUtils.alpha(new Color(color), 255));
            fr.drawStringWithShadow(m.name, mX - 3, startY + i.get() + 2, RenderUtils.alpha(new Color(color), 255));
            Gui.drawRect((int) (x + arrayListX), (int) (startY + i.get()), (int) (x + arrayListX) - 1, (int) (startY + i.get() + 10), RenderUtils.alpha(new Color(color), 255));
            i.addAndGet(10);
        });
    };

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (mc.currentScreen != null && !(mc.currentScreen instanceof GuiChat) && !(mc.currentScreen instanceof HUDEditorScreen))
            return;

        ScaledResolution sr = new ScaledResolution(mc);
        FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();

        if (fr == null || nameList == null) return;

        float x = sr.getScaledWidth();
        AtomicInteger i = new AtomicInteger();
        float startY = arrayListY;
        nameList.forEach(m -> {
            float mX = x + arrayListX - m.length;
            Gui.drawRect((int) (x + arrayListX), (int) (startY + i.get()), (int) mX - 6, (int) (startY + i.get() + 10), -1);
            i.addAndGet(10);
        });
    };

    @Override
    public JsonObject saveInfoToJson(JsonObject obj) {
        JsonObject pos = new JsonObject();
        pos.addProperty("watermarkX", watermarkX);
        pos.addProperty("watermarkY", watermarkY);
        pos.addProperty("arrayListX", arrayListX);
        pos.addProperty("arrayListY", arrayListY);
        pos.addProperty("targetHUDX", targetHUDX);
        pos.addProperty("targetHUDY", targetHUDY);
        pos.addProperty("coordsX", coordsX);
        pos.addProperty("coordsY", coordsY);
        pos.addProperty("keybindsX", keybindsX);
        pos.addProperty("keybindsY", keybindsY);
        pos.addProperty("radarX", Radar.radarX);
        pos.addProperty("radarY", Radar.radarY);
        obj.add("positions", pos);

        obj.addProperty("bind", getKeybind());
        obj.addProperty("enabled", isEnabled());
        serializableProperties.forEach(property -> property.addToJson(obj));
        return obj;
    }

    @Override
    public void loadFromJson(JsonObject obj) {
        try {
            JsonObject pos = obj.getAsJsonObject("positions");
            if (pos != null) {
                watermarkX = pos.get("watermarkX").getAsInt();
                watermarkY = pos.get("watermarkY").getAsInt();
                arrayListX = pos.get("arrayListX").getAsInt();
                arrayListY = pos.get("arrayListY").getAsInt();
                targetHUDX = pos.get("targetHUDX").getAsInt();
                targetHUDY = pos.get("targetHUDY").getAsInt();
                coordsX = pos.get("coordsX").getAsInt();
                coordsY = pos.get("coordsY").getAsInt();
                keybindsX = pos.get("keybindsX").getAsInt();
                keybindsY = pos.get("keybindsY").getAsInt();
                Radar.radarX = pos.get("radarX").getAsInt();
                Radar.radarY = pos.get("radarY").getAsInt();
            }
        } catch (NullPointerException | IllegalArgumentException e) {
            Arsenic.getArsenic().getLogger().info("Error loading HUD positions (first launch or update)");
        }

        try {
            setKeybind(obj.get("bind").getAsInt());
            setEnabledSilently(obj.get("enabled").getAsBoolean());
            serializableProperties.forEach(property -> property.loadFromJson(obj.getAsJsonObject(property.getJsonKey())));
        } catch (NullPointerException | IllegalArgumentException e) {
            Arsenic.getArsenic().getLogger().info("Error loading HUD config (first launch or update)");
        }
        postApplyConfig();
    }

    private static class ModuleRenderInfo {
        public final float length;
        public final String name;

        public ModuleRenderInfo(float length, String name) {
            this.length = length;
            this.name = name;
        }
    }

    public enum hMode {
        THEME(ColorUtils::getThemeRainbowColor),
        RAINBOW(ColorUtils::getRainbow);

        private final BinaryOperator<Integer> f;

        hMode(BinaryOperator<Integer> f) {
            this.f = f;
        }

        public int getColor(int speed, int delay) {
            return f.apply(speed, delay);
        }
    }

    public enum WatermarkMode {
        TEXT, FPS, COORDS, IP
    }

    public enum ArrayListSort {
        LENGTH(Comparator.comparingDouble((ModuleRenderInfo ri) -> -ri.length)),
        ABC(Comparator.comparing((ModuleRenderInfo ri) -> ri.name.toLowerCase()));

        private final Comparator<ModuleRenderInfo> comparator;

        ArrayListSort(Comparator<ModuleRenderInfo> comparator) {
            this.comparator = comparator;
        }

        public Comparator<ModuleRenderInfo> getComparator() {
            return comparator;
        }
    }

    public enum ArrayListBackground {
        RECTANGLE, BAR, NONE
    }

    public static class HUDEditorScreen extends GuiScreen {

        private boolean draggingArrayList;
        private boolean draggingWatermark;
        private boolean draggingTargetHUD;
        private boolean draggingCoords;
        private boolean draggingKeybinds;
        private boolean draggingRadar;
        private int dragOffsetX, dragOffsetY;
        private ScaledResolution sr;

        @Override
        public void initGui() {
            sr = new ScaledResolution(mc);
            buttonList.add(new GuiButtonExt(1, width / 2 - 50, height - 30, 100, 20, "Save & Close"));
            buttonList.add(new GuiButtonExt(2, 5, height - 30, 60, 20, "Reset"));
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawRect(0, 0, width, height, new Color(0, 0, 0, 100).getRGB());

            drawRect(0, height / 2, width, height / 2 + 1, new Color(50, 50, 50, 150).getRGB());
            drawRect(width / 2, 0, width / 2 + 1, height, new Color(50, 50, 50, 150).getRGB());

            FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
            if (fr != null) {
                fr.drawString("Drag elements to reposition", width / 2f - 70, 10, 0xFFFFFFFF);

                int color = new Color(0, 255, 0, 200).getRGB();

                DrawUtils.drawBorderedRoundedRect(watermarkX - 2, watermarkY - 2, watermarkX + fr.getWidth("Arsenic [0 FPS]") + 4, watermarkY + 12, 3, 1, color, new Color(0, 0, 0, 80).getRGB());
                fr.drawString("Watermark", watermarkX, watermarkY, 0xFFFFFFFF);

                DrawUtils.drawBorderedRoundedRect(sr.getScaledWidth() + arrayListX - 6, arrayListY, sr.getScaledWidth() + arrayListX + 2, arrayListY + 20, 3, 1, color, new Color(0, 0, 0, 80).getRGB());
                fr.drawString("Module List", sr.getScaledWidth() + arrayListX - 4, arrayListY + 5, 0xFFFFFFFF);

                DrawUtils.drawBorderedRoundedRect(targetHUDX - 2, targetHUDY - 2, targetHUDX + 152, targetHUDY + 52, 3, 1, color, new Color(0, 0, 0, 80).getRGB());
                fr.drawString("TargetHUD", targetHUDX + 40, targetHUDY + 20, 0xFFFFFFFF);

                DrawUtils.drawBorderedRoundedRect(coordsX - 2, coordsY - 2, coordsX + fr.getWidth("XYZ: 0 / 0 / 0") + 4, coordsY + 12, 3, 1, color, new Color(0, 0, 0, 80).getRGB());
                fr.drawString("Coordinates", coordsX, coordsY, 0xFFFFFFFF);

                DrawUtils.drawBorderedRoundedRect(keybindsX - 2, keybindsY - 2, keybindsX + fr.getWidth("ModuleName [KEY]") + 4, keybindsY + 22, 3, 1, color, new Color(0, 0, 0, 80).getRGB());
                fr.drawString("ModuleName [KEY]", keybindsX, keybindsY, 0xFFFFFFFF);
                fr.drawString("Keybinds", keybindsX, keybindsY + 10, 0xFFAAAAAA);

                DrawUtils.drawBorderedRoundedRect(Radar.radarX - 2, Radar.radarY - 2, Radar.radarX + 124, Radar.radarY + 124, 3, 1, color, new Color(0, 0, 0, 80).getRGB());
                fr.drawString("Radar", Radar.radarX + 42, Radar.radarY + 56, 0xFFFFFFFF);
            }

            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
            super.mouseClicked(mouseX, mouseY, mouseButton);
            if (mouseButton == 0) {
                FontRendererExtension<?> fr = Arsenic.getArsenic().getClickGuiScreen().getFontRenderer();
                if (fr == null) return;

                if (mouseX >= watermarkX && mouseX <= watermarkX + fr.getWidth("Arsenic [0 FPS]") + 4
                        && mouseY >= watermarkY && mouseY <= watermarkY + 12) {
                    draggingWatermark = true;
                    dragOffsetX = mouseX - watermarkX;
                    dragOffsetY = mouseY - watermarkY;
                }

                if (mouseX >= sr.getScaledWidth() + arrayListX - 6 && mouseX <= sr.getScaledWidth() + arrayListX + 2
                        && mouseY >= arrayListY && mouseY <= arrayListY + 20) {
                    draggingArrayList = true;
                    dragOffsetX = mouseX - (int) (sr.getScaledWidth() + arrayListX);
                    dragOffsetY = mouseY - arrayListY;
                }

                if (mouseX >= targetHUDX - 2 && mouseX <= targetHUDX + 152
                        && mouseY >= targetHUDY - 2 && mouseY <= targetHUDY + 52) {
                    draggingTargetHUD = true;
                    dragOffsetX = mouseX - targetHUDX;
                    dragOffsetY = mouseY - targetHUDY;
                }

                if (mouseX >= coordsX - 2 && mouseX <= coordsX + fr.getWidth("XYZ: 0 / 0 / 0") + 4
                        && mouseY >= coordsY - 2 && mouseY <= coordsY + 12) {
                    draggingCoords = true;
                    dragOffsetX = mouseX - coordsX;
                    dragOffsetY = mouseY - coordsY;
                }

                if (mouseX >= keybindsX - 2 && mouseX <= keybindsX + fr.getWidth("ModuleName [KEY]") + 4
                        && mouseY >= keybindsY - 2 && mouseY <= keybindsY + 22) {
                    draggingKeybinds = true;
                    dragOffsetX = mouseX - keybindsX;
                    dragOffsetY = mouseY - keybindsY;
                }

                if (mouseX >= Radar.radarX - 2 && mouseX <= Radar.radarX + 124
                        && mouseY >= Radar.radarY - 2 && mouseY <= Radar.radarY + 124) {
                    draggingRadar = true;
                    dragOffsetX = mouseX - Radar.radarX;
                    dragOffsetY = mouseY - Radar.radarY;
                }
            }
        }

        @Override
        protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
            super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
            if (clickedMouseButton == 0) {
                if (draggingWatermark) {
                    watermarkX = mouseX - dragOffsetX;
                    watermarkY = mouseY - dragOffsetY;
                }
                if (draggingArrayList) {
                    arrayListX = mouseX - dragOffsetX - sr.getScaledWidth();
                    arrayListY = mouseY - dragOffsetY;
                }
                if (draggingTargetHUD) {
                    targetHUDX = mouseX - dragOffsetX;
                    targetHUDY = mouseY - dragOffsetY;
                }
                if (draggingCoords) {
                    coordsX = mouseX - dragOffsetX;
                    coordsY = mouseY - dragOffsetY;
                }
                if (draggingKeybinds) {
                    keybindsX = mouseX - dragOffsetX;
                    keybindsY = mouseY - dragOffsetY;
                }
                if (draggingRadar) {
                    Radar.radarX = mouseX - dragOffsetX;
                    Radar.radarY = mouseY - dragOffsetY;
                }
            }
        }

        @Override
        protected void mouseReleased(int mouseX, int mouseY, int state) {
            super.mouseReleased(mouseX, mouseY, state);
            draggingWatermark = false;
            draggingArrayList = false;
            draggingTargetHUD = false;
            draggingCoords = false;
            draggingKeybinds = false;
            draggingRadar = false;
        }

        @Override
        public void actionPerformed(GuiButton button) {
            if (button.id == 1) {
                Arsenic.getArsenic().getConfigManager().saveConfig();
                mc.displayGuiScreen(null);
            } else if (button.id == 2) {
                arrayListX = 0;
                arrayListY = 0;
                watermarkX = 4;
                watermarkY = 4;
                targetHUDX = 100;
                targetHUDY = 100;
                coordsX = 4;
                coordsY = 60;
                keybindsX = 4;
                keybindsY = 80;
                Radar.radarX = 4;
                Radar.radarY = 4;
            }
        }

        @Override
        public boolean doesGuiPauseGame() {
            return false;
        }
    }
}
