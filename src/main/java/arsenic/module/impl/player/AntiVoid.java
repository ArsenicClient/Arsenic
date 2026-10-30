package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.Priorities;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventRender2D;
import arsenic.event.impl.EventRenderWorldLast;
import arsenic.event.impl.EventTick;
import arsenic.event.impl.EventUpdate;
import arsenic.gui.design.M3;
import arsenic.gui.design.M3Icons;
import arsenic.gui.hud.HudElement;
import arsenic.gui.hud.HudStyle;
import arsenic.injection.accessor.IMixinEntityPlayerSP;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.Group;
import arsenic.module.property.PropertyGroup;
import arsenic.module.property.ShowWhen;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.EnumProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import arsenic.utils.font.FontRendererExtension;
import arsenic.utils.java.ColorUtils;
import arsenic.utils.render.RenderUtils;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Keeps you out of the void: sneaks at void edges (legit) or blinks and snaps back to the last safe ground (Blink). */
@ModuleInfo(name = "AntiVoid", description = "Keeps you from falling into the void", category = ModuleCategory.PLAYER, tier = ModuleTier.LEGIT)
public class AntiVoid extends Module {

    public enum Mode { Sneak, Blink }

    public final EnumProperty<Mode> mode = new EnumProperty<>("Mode", Mode.Sneak);

    @ShowWhen(property = "Mode", is = "Sneak")
    public final DoubleProperty lookAhead = new DoubleProperty("Look Ahead", new DoubleValue(1, 6, 3, 0.5));
    @ShowWhen(property = "Mode", is = "Sneak")
    public final DoubleProperty holdTicks = new DoubleProperty("Hold Ticks", new DoubleValue(1, 10, 3, 1));

    @ShowWhen(property = "Mode", is = "Blink")
    public final DoubleProperty fallDistance = new DoubleProperty("Fall Distance", new DoubleValue(1, 8, 3, 0.5));
    @ShowWhen(property = "Mode", is = "Blink")
    public final DoubleProperty maxTicks = new DoubleProperty("Max Blink Ticks", new DoubleValue(5, 30, 15, 1));

    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty render = new BooleanProperty("Render", true);
    @PropertyGroup(Group.VISUAL)
    public final BooleanProperty hud = new BooleanProperty("HUD", true);

    private final HudElement panel = hudElement("AntiVoid", 6, 140, 110, 20);
    private final Random random = new Random();

    // Blink state
    private final List<Packet<?>> held = new ArrayList<>();
    private boolean blinking, haveSafe;
    private int blinkTicks, rescues, serverSlot = -1;
    // last state the server really saw (saved after every movement packet that went out)
    private double sX, sY, sZ, sMX, sMY, sMZ;
    private float sYaw, sPitch;
    private boolean sGround, sSprint;
    private World lastWorld;

    // Sneak state
    private int sneakHold;
    private boolean weSneak;

    @Override
    protected void onDisable() {
        stopBlink();
        sneakHold = 0;
        releaseSneak();
    }

    @Override
    public String getHudInfo() { return mode.getValue().name(); }

    // ---- per tick: world change, rescue (Blink), edge detection (Sneak) ----
    @RequiresPlayer @EventLink
    public final Listener<EventTick> onTick = event -> {
        EntityPlayerSP p = mc.thePlayer;
        if (p == null || mc.theWorld == null) return;
        if (mc.theWorld != lastWorld) {                          // never replay packets into another world
            lastWorld = mc.theWorld;
            held.clear(); blinking = false; haveSafe = false; serverSlot = -1;
        }
        if (p.capabilities.isFlying || p.capabilities.allowFlying) { stopBlink(); releaseSneak(); return; }

        if (mode.getValue() == Mode.Blink) {
            releaseSneak();
            if (blinking && (++blinkTicks >= (int) maxTicks.getValue().getInput()
                    || sY - p.posY >= fallDistance.getValue().getInput())) {
                rescue(p);                                       // before this tick's movement, so it starts from the safe state
            }
        } else {
            stopBlink();
            tickSneak(p);
        }
    };

    // ---- Blink: start/stop right before the movement packet, after this tick's move is known ----
    @RequiresPlayer @EventLink
    public final Listener<EventUpdate.Pre> onPre = event -> {
        EntityPlayerSP p = mc.thePlayer;
        if (mode.getValue() != Mode.Blink || p == null || mc.theWorld == null) return;
        boolean overVoid = overVoid(p.getEntityBoundingBox());
        if (blinking) {
            if (p.onGround || !overVoid) stopBlink();            // landed / back over ground: send the held path, in order
        } else if (haveSafe && sGround && !p.onGround && overVoid && !p.capabilities.isFlying) {
            blinking = true;                                     // this tick's packet is the first one held
            blinkTicks = 0;
        }
    };

    @RequiresPlayer @EventLink
    public final Listener<EventUpdate.Post> onPost = event -> {
        EntityPlayerSP p = mc.thePlayer;
        if (blinking || p == null) return;
        sX = p.posX; sY = p.posY; sZ = p.posZ;
        sMX = p.motionX; sMY = p.motionY; sMZ = p.motionZ;
        sYaw = p.rotationYaw; sPitch = p.rotationPitch;
        sGround = p.onGround;
        sSprint = ((IMixinEntityPlayerSP) p).getServerSprintState();
        haveSafe = true;
    };

    @EventLink(Priorities.VERY_LOW)                              // run last, so we hold the packet other modules settled on
    public final Listener<EventPacket.OutGoing> onSend = event -> {
        if (event.isCancelled()) return;
        Packet<?> pk = event.getPacket();
        if (blinking) { held.add(pk); event.cancel(); return; }
        if (pk instanceof C09PacketHeldItemChange) serverSlot = ((C09PacketHeldItemChange) pk).getSlotId();
    };

    private void stopBlink() {
        blinking = false;
        if (held.isEmpty() || mc.getNetHandler() == null) { held.clear(); return; }
        List<Packet<?>> out = new ArrayList<>(held);
        held.clear();
        for (Packet<?> pk : out) mc.getNetHandler().addToSendQueue(pk);
    }

    private void rescue(EntityPlayerSP p) {
        blinking = false;
        boolean droppedSlot = false;
        List<Packet<?>> keep = new ArrayList<>();
        for (Packet<?> pk : held) {
            // transactions and keep-alives must still be answered, or Grim/the server times you out
            if (pk instanceof C0FPacketConfirmTransaction || pk instanceof C00PacketKeepAlive) keep.add(pk);
            else if (pk instanceof C09PacketHeldItemChange) droppedSlot = true;
        }
        held.clear();
        if (mc.getNetHandler() != null) {
            for (Packet<?> pk : keep) mc.getNetHandler().addToSendQueue(pk);
            // a dropped slot change leaves the server on the old slot: resync, but never send a duplicate slot
            if (droppedSlot && serverSlot != -1 && serverSlot != p.inventory.currentItem)
                mc.getNetHandler().addToSendQueue(new C09PacketHeldItemChange(p.inventory.currentItem));
        }
        // restore exactly the state the server last saw, so its simulation continues from it
        p.setPosition(sX, sY, sZ);
        p.prevPosX = p.lastTickPosX = sX;
        p.prevPosY = p.lastTickPosY = sY;
        p.prevPosZ = p.lastTickPosZ = sZ;
        p.motionX = sMX; p.motionY = sMY; p.motionZ = sMZ;
        p.fallDistance = 0;
        p.onGround = sGround;
        IMixinEntityPlayerSP acc = (IMixinEntityPlayerSP) p;
        acc.setLastReportedYaw(sYaw);                            // the next packet resends the rotation if it differs
        acc.setLastReportedPitch(sPitch);
        acc.setServerSprintState(sSprint);
        rescues++;
    }

    // ---- Sneak: hold the real sneak key when the ground ahead is void ----
    private void tickSneak(EntityPlayerSP p) {
        boolean danger = mc.currentScreen == null && p.onGround && voidAhead(p);
        if (danger) {
            sneakHold = (int) holdTicks.getValue().getInput() + random.nextInt(2);   // small jitter on release
            holdSneak();
        } else if (sneakHold > 0) {
            sneakHold--;
        } else {
            releaseSneak();
        }
    }

    private boolean voidAhead(EntityPlayerSP p) {
        double k = lookAhead.getValue().getInput();
        double dx = p.motionX * k, dz = p.motionZ * k;
        float fwd = p.movementInput.moveForward, str = p.movementInput.moveStrafe;
        if (fwd != 0 || str != 0) {                              // also look where the keys push, even when standing still at the edge
            double yaw = Math.toRadians(p.rotationYaw);
            double ix = str * Math.cos(yaw) - fwd * Math.sin(yaw);
            double iz = fwd * Math.cos(yaw) + str * Math.sin(yaw);
            double len = Math.sqrt(ix * ix + iz * iz);
            dx += ix / len * 0.3;
            dz += iz / len * 0.3;
        }
        return overVoid(p.getEntityBoundingBox().offset(dx, 0, dz));
    }

    /** True when nothing solid exists anywhere below the box, down to y = 0. */
    private boolean overVoid(AxisAlignedBB bb) {
        if (bb.minY <= 0) return true;
        AxisAlignedBB below = new AxisAlignedBB(bb.minX, 0, bb.minZ, bb.maxX, bb.minY + 0.001, bb.maxZ);
        return mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, below).isEmpty();
    }

    private void holdSneak() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
        weSneak = true;
    }

    private void releaseSneak() {
        if (!weSneak) return;
        int code = mc.gameSettings.keyBindSneak.getKeyCode();
        boolean userHolds = code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
        KeyBinding.setKeyBindState(code, userHolds);             // never override the user's own sneak
        weSneak = false;
    }

    // ---- visuals ----
    @EventLink
    public final Listener<EventRenderWorldLast> onWorld = event -> {
        if (!render.getValue() || mc.thePlayer == null || mc.theWorld == null) return;
        int main = Arsenic.getArsenic().getThemeManager().getCurrentTheme().getMainColor();
        BlockPos pos = null;
        boolean strong = false;
        if (mode.getValue() == Mode.Blink && blinking && haveSafe) {
            pos = new BlockPos(sX, sY - 0.5, sZ);                // where you will be put back
            strong = true;
        } else if (mode.getValue() == Mode.Sneak && weSneak) {
            pos = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY - 0.5, mc.thePlayer.posZ);
        }
        if (pos == null) return;
        RenderUtils.renderBlock(pos, ColorUtils.withAlpha(main, strong ? 110 : 60), false, true);
        RenderUtils.renderBlock(pos, ColorUtils.withAlpha(main, 230), true, false);
    };

    @EventLink
    public final Listener<EventRender2D> onHud = event -> {
        if (!hud.getValue() || mc.thePlayer == null || mc.theWorld == null) return;
        FontRendererExtension<?> fr = HudStyle.label();
        if (fr == null) return;
        boolean active = blinking || weSneak;
        String title = "AntiVoid", state = stateText();
        float h = 20f, pad = 6f, chipPad = 4.5f;
        float w = pad + 12.5f + fr.getWidth(title) + 10f + fr.getWidth(state) + chipPad * 2 + pad;
        panel.setSize((int) Math.ceil(w), (int) h);
        float x = HudStyle.snap(panel.x), y = HudStyle.snap(panel.y), cy = y + h / 2f;

        HudStyle.card(x, y, x + w, y + h, HudStyle.RADIUS_CARD);
        M3Icons.drawLeft(M3Icons.SHIELD, x + pad, cy, active ? M3.primary() : M3.onSurfaceVariant(), 0.85f);
        fr.drawString(title, x + pad + 12.5f, cy, M3.onSurface(), fr.CENTREY);
        HudStyle.rightChip(fr, state, x + w - pad, cy, 11.5f, chipPad,
                active ? M3.primaryContainer() : M3.secondaryContainer(),
                active ? M3.onPrimaryContainer() : M3.onSecondaryContainer());
        HudStyle.resetGl();
    };

    private String stateText() {
        if (mode.getValue() == Mode.Blink)
            return blinking ? "Holding " + blinkTicks + "t" : (rescues > 0 ? rescues + " saved" : "Ready");
        return weSneak ? "Edge" : "Safe";
    }
}
