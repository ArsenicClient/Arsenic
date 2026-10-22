
import arsenic.asm.RequiresPlayer;
import arsenic.command.Command;
import arsenic.command.CommandInfo;
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
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static arsenic.utils.java.JavaUtils.autoCompleteHelper;

@ModuleInfo(name = "AutoHunt", category = ModuleCategory.PLAYER, tier = ModuleTier.BLATANT)
public class AutoHunt extends Module {

    public final BooleanProperty useKillAura = new BooleanProperty("Use KillAura", true);

    private static final double CHASE_STOP_DISTANCE = 2.5;
    private static final double LAST_SEEN_STOP_DISTANCE = 1.5;

    private static String huntName;

    {
        registerCommand(new HuntCommand());
    }

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

    @Override
    public boolean allowsTarget(Player player) {
        if (!useKillAura.getValue() || huntName == null)
            return true;
        return player.getName().getString().equalsIgnoreCase(huntName);
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
        if (mc.options != null) {
            savedPause = mc.options.pauseOnLostFocus;
            pauseSaved = true;
            mc.options.pauseOnLostFocus = false;
        }
    }

    @Override
    protected void onDisable() {
        if (mc.options != null && pauseSaved)
            mc.options.pauseOnLostFocus = savedPause;
        pauseSaved = false;
        if (mc.player != null)
            chaser.stop();
        BotDriver.forgetOwnBlocks();
        setKillAura(false);
    }

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        mc.options.pauseOnLostFocus = false;
        if (mc.gui.screen() instanceof PauseScreen) mc.gui.setScreen(null);
        setKillAura(useKillAura.getValue());

        Player target = findTarget();
        if (target != null) {
            lastSeen = new double[]{target.getX(), target.getY(), target.getZ()};
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

    private Player findTarget() {
        for (Player player : mc.level.players()) {
            if (player != mc.player && !player.isRemoved() && player.getHealth() > 0
                    && player.getName().getString().equalsIgnoreCase(huntName))
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

    /** .hunt <name> sets who to chase and turns the module on, .hunt stop turns it off. */
    @CommandInfo(name = "hunt", args = { "name/stop" }, help = "sets who AutoHunt chases and turns it on, or stops it", minArgs = 1)
    private class HuntCommand extends Command {

        @Override
        public void execute(String[] args) {
            if (args[0].equalsIgnoreCase("stop")) {
                setEnabled(false);
                PlayerUtils.addWaterMarkedMessageToChat("Stopped hunting");
                return;
            }
            setHuntName(args[0]);
            if (isEnabled())
                PlayerUtils.addWaterMarkedMessageToChat("Now hunting §c" + args[0]);
            else
                setEnabled(true);
        }

        @Override
        protected List<String> getAutoComplete(String str, int arg, List<String> list) {
            if (arg != 0)
                return list;
            List<String> names = new ArrayList<>();
            names.add("stop");
            if (mc.getConnection() != null) {
                names.addAll(mc.getConnection().getOnlinePlayers().stream()
                        .map(info -> info.getProfile().name())
                        .filter(n -> mc.player == null || !n.equalsIgnoreCase(mc.player.getName().getString()))
                        .collect(Collectors.toList()));
            }
            return autoCompleteHelper(names, str);
        }
    }
}
