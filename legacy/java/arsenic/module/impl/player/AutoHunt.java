package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventTick;
import arsenic.main.Arsenic;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.impl.blatant.KillAura;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.utils.bot.BotDriver;
import arsenic.utils.bot.Chaser;
import arsenic.utils.minecraft.PlayerUtils;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.entity.player.EntityPlayer;

@ModuleInfo(name = "AutoHunt", category = ModuleCategory.PLAYER, tier = ModuleTier.EXTRA)
public class AutoHunt extends Module {

    public final BooleanProperty useKillAura = new BooleanProperty("Use KillAura", true);

    private static final double CHASE_STOP_DISTANCE = 2.5;
    private static final double LAST_SEEN_STOP_DISTANCE = 1.5;

    private static String huntName;

    private final Chaser chaser = new Chaser();
    private double[] lastSeen;
    private boolean announcedLost;
    private boolean savedPause;
    private boolean pauseSaved;
    private boolean enabledKillAura;

    public static String getHuntName() {
        return huntName;
    }

    public static void setHuntName(String name) {
        huntName = name;
    }

    public static boolean allowsTarget(EntityPlayer player) {
        AutoHunt hunt = Arsenic.getArsenic().getModuleManager().getModuleByClass(AutoHunt.class);
        if (hunt == null || !hunt.isEnabled() || !hunt.useKillAura.getValue() || huntName == null)
            return true;
        return player.getName().equalsIgnoreCase(huntName);
    }

    @Override
    protected void onEnable() {
        lastSeen = null;
        announcedLost = false;
        if (huntName == null) {
            PlayerUtils.addWaterMarkedMessageToChat("No one to hunt - use .hunt <name>");
            setEnabled(false);
            return;
        }
        PlayerUtils.addWaterMarkedMessageToChat("Hunting §c" + huntName);
        if (mc.gameSettings != null) {
            savedPause = mc.gameSettings.pauseOnLostFocus;
            pauseSaved = true;
            mc.gameSettings.pauseOnLostFocus = false;
        }
    }

    @Override
    protected void onDisable() {
        if (mc.gameSettings != null && pauseSaved)
            mc.gameSettings.pauseOnLostFocus = savedPause;
        pauseSaved = false;
        if (mc.thePlayer != null)
            chaser.stop();
        BotDriver.forgetOwnBlocks();
        setKillAura(false);
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        mc.gameSettings.pauseOnLostFocus = false;
        if (mc.currentScreen instanceof GuiIngameMenu) mc.displayGuiScreen(null);
        setKillAura(useKillAura.getValue());

        EntityPlayer target = findTarget();
        if (target != null) {
            lastSeen = new double[]{target.posX, target.posY, target.posZ};
            announcedLost = false;
            chaser.chase(target, CHASE_STOP_DISTANCE);
            return;
        }

        if (lastSeen != null) {
            if (chaser.goTo(lastSeen[0], lastSeen[1], lastSeen[2], LAST_SEEN_STOP_DISTANCE))
                lastSeen = null;
            return;
        }

        chaser.stop();
        if (!announcedLost) {
            PlayerUtils.addWaterMarkedMessageToChat("Lost §c" + huntName + "§r - waiting for them to come into view");
            announcedLost = true;
        }
    };

    private EntityPlayer findTarget() {
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player != mc.thePlayer && !player.isDead && player.getHealth() > 0
                    && player.getName().equalsIgnoreCase(huntName))
                return player;
        }
        return null;
    }

    private void setKillAura(boolean on) {
        KillAura aura = Arsenic.getArsenic().getModuleManager().getModuleByClass(KillAura.class);
        if (aura == null) return;
        if (on) {
            if (!aura.isEnabled()) {
                aura.setEnabled(true);
                enabledKillAura = true;
            }
        } else if (enabledKillAura) {
            aura.setEnabled(false);
            enabledKillAura = false;
        }
    }

    @Override
    public String getHudInfo() {
        return huntName == null ? "none" : huntName;
    }
}
