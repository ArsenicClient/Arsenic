package arsenic.module.impl.visual.custommainmenu;

import arsenic.utils.render.DrawUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class GuiChangelog extends GuiScreen {

    private final GuiScreen parent;
    private List<String> lines;
    private int scrollOffset;
    private final int contentStartY = 40;
    private final int lineHeight = 12;

    public GuiChangelog(GuiScreen parent) {
        this.parent = parent;
        this.lines = new ArrayList<>();
        loadChangelog();
    }

    private void loadChangelog() {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    Minecraft.getMinecraft().getResourceManager().getResource(
                            new ResourceLocation("minecraft:changelog.txt")).getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            reader.close();
        } catch (Exception e) {
            lines.add("Failed to load changelog");
        }
    }

    @Override
    public void initGui() {
        this.buttonList.add(new GuiButton(0, this.width / 2 - 50, this.height - 30, 100, 20, "Back"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        mc.fontRendererObj.drawStringWithShadow("Changelog", this.width / 2f - mc.fontRendererObj.getStringWidth("Changelog") / 2f, 10, -1);

        int totalContentHeight = lines.size() * lineHeight;
        int maxScroll = Math.max(0, totalContentHeight - (this.height - contentStartY - 40));

        for (int i = 0; i < lines.size(); i++) {
            int y = contentStartY + i * lineHeight - scrollOffset;
            if (y < contentStartY - lineHeight || y > this.height - 20) continue;

            String line = lines.get(i);
            if (line.startsWith("[-]")) {
                mc.fontRendererObj.drawStringWithShadow(line, 15, y, new Color(255, 80, 80, 200).getRGB());
            } else if (line.startsWith("[+]")) {
                mc.fontRendererObj.drawStringWithShadow(line, 15, y, new Color(80, 255, 80, 200).getRGB());
            } else if (line.startsWith("[*]")) {
                mc.fontRendererObj.drawStringWithShadow(line, 15, y, new Color(255, 255, 80, 200).getRGB());
            } else if (line.startsWith("===") || line.contains("===")) {
                mc.fontRendererObj.drawStringWithShadow(line, 15, y, new Color(255, 255, 255, 150).getRGB());
            } else {
                mc.fontRendererObj.drawStringWithShadow(line, 15, y, new Color(200, 200, 200, 180).getRGB());
            }
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int scroll = Mouse.getEventDWheel();
        if (scroll != 0) {
            int totalContentHeight = lines.size() * lineHeight;
            int maxScroll = Math.max(0, totalContentHeight - (this.height - contentStartY - 40));
            scrollOffset += scroll > 0 ? -30 : 30;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }
    }
}
