    package arsenic.module.impl.visual.custommainmenu;

    import arsenic.main.Arsenic;
    import arsenic.utils.font.FontRendererExtension;
    import arsenic.utils.interfaces.IFontRenderer;
    import arsenic.utils.render.DrawUtils;
    import arsenic.utils.render.shader.ShaderUtil;
    import net.minecraft.client.Minecraft;
    import net.minecraft.client.gui.*;
    import net.minecraft.client.resources.I18n;
    import net.minecraft.client.shader.Framebuffer;

    import java.awt.*;
    import java.io.IOException;
    import java.util.Arrays;
    import java.util.List;


    public class Screen extends GuiScreen {

        private ShaderUtil backgroundShader;
        private int currentShaderIndex = 0;
        private final List<String> backgroundShaders = Arrays.asList("zippyZaps", "rainbowShader", "kvShader");


        public Screen() {
            backgroundShader = new ShaderUtil(backgroundShaders.get(currentShaderIndex));
        }

        @Override
        public void initGui() {
            this.buttonList.add(new GuiButton(5, 5, 5, 80, 20, "Next Shader"));
            int p_73969_1_ = this.height / 2;
            int p_73969_2_ = 24;
            this.buttonList.add(new CustomGuiButton(1, this.width / 2 - 100, p_73969_1_, I18n.format("menu.singleplayer", new Object[0])));
            this.buttonList.add(new CustomGuiButton(2, this.width / 2 - 100, p_73969_1_ + p_73969_2_ * 1, I18n.format("menu.multiplayer", new Object[0])));
            this.buttonList.add(new CustomGuiButton(3, this.width / 2 - 100, p_73969_1_ + p_73969_2_ * 2, "Mods"));
            this.buttonList.add(new CustomGuiButton(0, this.width / 2 - 100, p_73969_1_ + p_73969_2_ * 3, I18n.format("menu.options", new Object[0])));
            this.buttonList.add(new CustomGuiButton(4, this.width / 2 - 100, p_73969_1_ + p_73969_2_ * 4, I18n.format("menu.quit", new Object[0])));
        }

        @Override
        public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            backgroundShader.init();
            backgroundShader.setUniformf("time", (System.currentTimeMillis() % 1000000) / 5000f);
            ScaledResolution sr = new ScaledResolution(mc);
            backgroundShader.setUniformf("resolution", this.width * sr.getScaleFactor(), this.height * sr.getScaleFactor());
            ShaderUtil.drawQuads();
            backgroundShader.unload();

            FontRendererExtension<?> fontRenderer = ((IFontRenderer) mc.fontRendererObj).getFontRendererExtension();
            String title = "Arsenic";
            float scale = 7f;
            fontRenderer.setScale(scale);
            float x = (this.width / 2f);
            float y = (this.height / 3f);
            fontRenderer.drawStringWithShadow(title, x, y, -1, fontRenderer.CENTREY, fontRenderer.CENTREX);
            fontRenderer.resetScale();

            String moduleCount = "Modules: " + Arsenic.getArsenic().getModuleManager().getModules().size();
            long settingCount = Arsenic.getArsenic().getModuleManager().getModules().stream().mapToLong(module -> module.getProperties().size()).sum();
            String settingCountStr = "Settings: " + settingCount;

            mc.fontRendererObj.drawStringWithShadow(moduleCount, this.width - mc.fontRendererObj.getStringWidth(moduleCount) - 2, 2, -1);
            mc.fontRendererObj.drawStringWithShadow(settingCountStr, this.width - mc.fontRendererObj.getStringWidth(settingCountStr) - 2, 2 + mc.fontRendererObj.FONT_HEIGHT, -1);


            String modCount = "Mods loaded: " + net.minecraftforge.fml.common.Loader.instance().getModList().size();
            mc.fontRendererObj.drawStringWithShadow(modCount, 2, this.height - mc.fontRendererObj.FONT_HEIGHT - 2, -1);

            //This will not work because the player is currently null
            //GuiInventory.drawEntityOnScreen(this.width - 50, this.height - 20, 30, this.width - 50 - mouseX, this.height - 20 - 50 - mouseY, mc.thePlayer);

            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override
        protected void actionPerformed(GuiButton button) throws IOException {
            switch (button.id) {
                case 0:
                    this.mc.displayGuiScreen(new GuiOptions(this, this.mc.gameSettings));
                    break;
                case 1:
                    this.mc.displayGuiScreen(new GuiSelectWorld(this));
                    break;
                case 2:
                    this.mc.displayGuiScreen(new GuiMultiplayer(this));
                    break;
                case 3:
                    this.mc.displayGuiScreen(new net.minecraftforge.fml.client.GuiModList(this));
                    break;
                case 4:
                    this.mc.shutdown();
                    break;
                case 5:
                    currentShaderIndex = (currentShaderIndex + 1) % backgroundShaders.size();
                    backgroundShader = new ShaderUtil(backgroundShaders.get(currentShaderIndex));
                    break;
            }
        }

        public static class CustomGuiButton extends GuiButton{

            private Framebuffer fbo;

            public CustomGuiButton(int buttonId, int x, int y, String buttonText) {
                super(buttonId, x, y, 200, 20, buttonText);
            }

            public CustomGuiButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) {
                super(buttonId, x, y, widthIn, heightIn, buttonText);
            }
            private void initFbo() {
                if (fbo == null) {
                    fbo = new Framebuffer(width, height, true);
                } else if (fbo.framebufferWidth != width || fbo.framebufferHeight != height) {
                    fbo.createBindFramebuffer(width, height);
                }
            }

            @Override
            public void drawButton(Minecraft mc, int mouseX, int mouseY) {
                this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
                DrawUtils.drawRoundedRect(xPosition, yPosition, xPosition + width, yPosition + height, height/4.0f, new Color(255, 255, 255, 40).getRGB());
                this.mouseDragged(mc, mouseX, mouseY);
                int j = this.hovered ? 14737632 : 14737632;
                FontRendererExtension<?> fontRenderer = ((IFontRenderer) mc.fontRendererObj).getFontRendererExtension();
                fontRenderer.drawStringWithShadow(displayString, xPosition + width / 2f, yPosition + height / 2f, j, fontRenderer.CENTREX, fontRenderer.CENTREY);
            }
        }
    }