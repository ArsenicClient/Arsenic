package arsenic.module.impl.visual;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventShader;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Icons;
import arsenic.gui.hud.HudEditorScreen;
import arsenic.gui.hud.HudElement;
import arsenic.gui.hud.HudStyle;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.world.Scaffold;
import arsenic.module.impl.world.TellyScaffold;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.MathUtils;
import arsenic.utils.timer.FrameClock;
import arsenic.utils.render.DrawUtils;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

@ModuleInfo(name = "BlockCounter", category = ModuleCategory.RENDER, hidden = true, tier = ModuleTier.VISUAL,
        enabled = true, description = "Blocks left, shown for Scaffold and TellyScaffold")
public class BlockCounter extends Module {

    private static final float HEIGHT = 36f;

    private final HudElement hud = hudElement("Block Counter", 4, 320, 134, (int) HEIGHT);
    private final FrameClock clock = new FrameClock();

    private int lastCount;
    private int maxCount;
    private float animatedCount;
    private long flashUntil;

    private float presence;
    private long presenceNanos;

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender2D = event -> {
        if (!isEnabled())
            return;

        boolean editor = mc.currentScreen instanceof HudEditorScreen;
        boolean allowedScreen = mc.currentScreen == null || mc.currentScreen instanceof GuiChat || editor;
        if (!allowedScreen) {
            presence = 0f;
            presenceNanos = 0L;
            return;
        }

        boolean target = editor || scaffoldEnabled();
        updatePresence(target);
        if (presence <= 0.004f)
            return;

        int count = blockCount();
        long now = System.currentTimeMillis();
        if (count < lastCount && lastCount - count <= 64)
            flashUntil = now + 260L;
        lastCount = count;
        if (count > maxCount)
            maxCount = count;
        if (count == 0 && now - flashUntil > 1200L)
            maxCount = 0;

        animatedCount = MathUtils.clamp(
                FrameClock.approach(animatedCount, count, 14f, clock.tick()), 0f, 2304f);
        int shown = Math.round(animatedCount);
        float flash = Math.max(0f, Math.min(1f, (flashUntil - now) / 260f));

        FontRendererExtension<?> label = HudStyle.label();
        FontRendererExtension<?> title = HudStyle.title();
        float pad = 7f;
        float icon = 22f;
        float textX = hud.x + pad + icon + 9f;
        String countText = String.valueOf(shown);
        float w = Math.max(134f, pad + icon + 9f + label.getWidth("Blocks")
                + 24f + title.getWidth(countText) + pad);
        hud.width = (int) Math.ceil(w);
        hud.height = (int) HEIGHT;

        float shownScale = hud.scale * presence;
        HudStyle.pushScale(hud.x, hud.y, shownScale);
        int fill = M3.alpha(M3.surfaceContainerHigh(), 242);
        if (flash > 0.01f)
            fill = M3.mix(fill, M3.alpha(M3.primaryContainer(), 242), flash * 0.45f);
        HudStyle.card(hud.x, hud.y, hud.x + w, hud.y + HEIGHT, HEIGHT / 2f, fill,
                M3.alpha(M3.outlineVariant(), 105));

        float iconCx = hud.x + pad + icon / 2f;
        float iconCy = hud.y + HEIGHT / 2f;
        HudStyle.iconBadge(M3Icons.CUBE, iconCx, iconCy, icon / 2f,
                M3.primary(), M3.onPrimary());

        label.drawString("Blocks", textX, hud.y + 13.5f, M3.onSurfaceVariant(), label.CENTREY);
        title.drawString(countText, hud.x + w - pad - title.getWidth(countText), hud.y + 13.5f,
                M3.primary(), title.CENTREY);

        float pct = maxCount <= 0 ? 0f : Math.min(1f, animatedCount / maxCount);
        HudStyle.progress(textX, hud.y + 26f, hud.x + w - pad, hud.y + 29f, 1.5f, pct,
                M3.alpha(M3.surfaceContainerHighest(), 220), M3.primary());

        HudStyle.resetColor();
        HudStyle.popScale();
    };

    @EventLink
    public final Listener<EventShader.Blur> blurListener = event -> {
        if (!isEnabled() || presence <= 0.004f)
            return;
        boolean editor = mc.currentScreen instanceof HudEditorScreen;
        if (!editor && !scaffoldEnabled())
            return;
        HudStyle.pushScale(hud.x, hud.y, hud.scale * presence);
        DrawUtils.drawRoundedRect(hud.x, hud.y, hud.x + hud.width, hud.y + HEIGHT, HEIGHT / 2f, 0xFFFFFFFF);
        HudStyle.popScale();
    };

    private void updatePresence(boolean target) {
        long now = System.nanoTime();
        float dt = presenceNanos == 0L ? 0f : Math.min(0.1f, (now - presenceNanos) / 1_000_000_000f);
        presenceNanos = now;
        float wanted = target ? 1f : 0f;
        presence += (wanted - presence) * Math.min(1f, dt * 16f);
        if (Math.abs(presence - wanted) < 0.002f)
            presence = wanted;
    }

    private boolean scaffoldEnabled() {
        Scaffold scaffold = Arsenic.getArsenic().getModuleManager().getModuleByClass(Scaffold.class);
        TellyScaffold telly = Arsenic.getArsenic().getModuleManager().getModuleByClass(TellyScaffold.class);
        return (scaffold != null && scaffold.isEnabled()) || (telly != null && telly.isEnabled());
    }

    private int blockCount() {
        if (mc.thePlayer == null)
            return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof ItemBlock && stack.stackSize > 0)
                count += stack.stackSize;
        }
        return count;
    }
}
