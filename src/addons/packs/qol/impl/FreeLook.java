import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventSilentRotation;
import arsenic.event.impl.EventTick;
import arsenic.gui.hud.HudElement;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.render.DrawUtils;
import arsenic.utils.rotations.RotationUtils;
import arsenic.utils.rotations.SilentRotationManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** FreeLook façon Lunar : maintiens la touche, la caméra tourne autour de toi, ton corps et ta direction ne bougent pas. */
@ModuleInfo(name = "FreeLook", description = "Regarde autour de toi sans tourner ton personnage",
        category = ModuleCategory.RENDER, keybind = Keyboard.KEY_LMENU, tier = ModuleTier.QOL)
public class FreeLook extends Module {

    public final BooleanProperty hold = new BooleanProperty("Hold", true);   // actif seulement tant que la touche est maintenue
    public final BooleanProperty hud  = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("FreeLook", 4, 160, 70, 16);

    private boolean active;
    private float yaw, pitch;      // direction du corps, figée
    private int prevView = -1;

    @Override
    protected void onEnable() {
        active = false;
        if (mc.thePlayer == null || mc.gameSettings == null) return;
        yaw = mc.thePlayer.rotationYaw;
        pitch = mc.thePlayer.rotationPitch;
        prevView = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 1;          // 3e personne arrière
        active = true;
    }

    @Override
    protected void onDisable() {
        if (active && mc.thePlayer != null) {         // la caméra revient où elle était
            mc.thePlayer.rotationYaw = mc.thePlayer.prevRotationYaw = yaw;
            mc.thePlayer.rotationPitch = mc.thePlayer.prevRotationPitch = pitch;
        }
        if (prevView != -1 && mc.gameSettings != null) mc.gameSettings.thirdPersonView = prevView;
        prevView = -1;
        active = false;
    }

    @Override
    public String getHudInfo() { return null; }

    // ---- touche relâchée = on coupe (mode Hold) ----
    @RequiresPlayer @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (!hold.getValue()) return;
        int key = getKeybind();
        if (key == 0) return;
        boolean down = key < 0 ? Mouse.isButtonDown(key + 100) : Keyboard.isKeyDown(key);
        if (!down) setEnabled(false);
    };

    // ---- le serveur et ton corps gardent la direction figée ----
    @RequiresPlayer @EventLink
    public final Listener<EventSilentRotation> onRotation = event -> {
        if (!active) return;
        boolean differs = Math.abs(RotationUtils.getYawDifference(event.getYaw(), yaw)) > 0.5f
                || Math.abs(event.getPitch() - pitch) > 0.5f;
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setSpeed(180f);
        event.setSmoothing(false);
        event.setBlockUserInput(differs);                                  // pas de clic dans la mauvaise direction
        event.setMovementFix(SilentRotationManager.MovementFix.STRICT);    // W = direction du corps, comme Lunar
    };

    // ---- petit HUD ----
    @EventLink
    public final Listener<EventRender2D> onHud = event -> {
        if (!hud.getValue() || !active || mc.thePlayer == null) return;
        String text = "FreeLook";
        panel.setSize(mc.fontRendererObj.getStringWidth(text) + 12, 16);
        DrawUtils.drawRoundedRect(panel.x, panel.y, panel.x + panel.width, panel.y + panel.height, 5, 0x96121212);
        mc.fontRendererObj.drawStringWithShadow(text, panel.x + 6, panel.y + 4, 0xFFFFFFFF);
    };
}
