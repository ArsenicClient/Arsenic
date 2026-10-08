import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.timer.MSTimer;
import net.minecraft.client.gui.ScaledResolution;

/**
 * Warns once each time your health drops to the limit: a short red flash across the screen, a sound and a HUD panel
 * that stays red while you are low. Rearms once health climbs back 2 points above the limit.
 */
@ModuleInfo(name = "HealthWarning", description = "Flashes the screen and plays a sound when your health drops below a limit", category = ModuleCategory.RENDER)
public class HealthWarning extends Module {

    public final DoubleProperty limit = new DoubleProperty("Limit", new DoubleValue(1, 20, 6, 1));
    public final BooleanProperty flash = new BooleanProperty("Flash", true);
    public final BooleanProperty sound = new BooleanProperty("Sound", true);
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("HealthWarning", 4, 80, 90, 16);
    private final MSTimer flashTimer = MSTimer.expired();
    private boolean warned;

    @Override
    protected void onEnable() {
        warned = false;
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        float hp = mc.thePlayer.getHealth();
        double threshold = limit.getValue().getInput();
        if (hp > 0 && hp <= threshold) {
            if (!warned) {
                warned = true;
                flashTimer.reset();
                if (sound.getValue()) mc.thePlayer.playSound("random.anvil_land", 0.6f, 1f);
            }
        } else if (hp > threshold + 2) {
            warned = false;
        }
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventRender2D> onRender = event -> {
        if (mc.thePlayer == null) return;

        if (flash.getValue() && flashTimer.getTime() < 600) {
            ScaledResolution sr = event.getSr();
            float alpha = 1f - flashTimer.getTime() / 600f;
            int color = ((int) (alpha * 90) << 24) | 0xFF2020;
            DrawUtils.drawRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), color);
        }

        if (!hud.getValue()) return;
        String text = String.format("HP %.1f", mc.thePlayer.getHealth());
        panel.setSize(Math.max(70, mc.fontRendererObj.getStringWidth(text) + 12), 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        int textColor = warned ? 0xFFFF5555 : 0xFFFFFFFF;
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, textColor);
    };
}
