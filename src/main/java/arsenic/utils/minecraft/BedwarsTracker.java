package arsenic.utils.minecraft;

import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import net.minecraft.block.BlockBed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Follows a Hypixel BedWars game from chat and works out which bed is yours, so addons do not have to guess.
 *
 * The chat lines it reads (checked against a live game):
 * <pre>
 * Protect your bed and destroy the enemy beds.          the game has started (you are on your island)
 * You have respawned!                                    you are back on your island
 * You can't destroy your own bed!                        the bed you just tried to mine is yours
 * BED DESTRUCTION > Your Bed was destroyed by NAME!      your bed is gone
 * BED DESTRUCTION > Red Bed was destroyed by NAME!       another team's bed is gone
 * TEAM ELIMINATED > Red Team has been eliminated!        a team is out
 * </pre>
 * Your bed is first taken as the bed nearest to where you stand when the game starts or you respawn (searched for a few
 * seconds while the island loads). "You can't destroy your own bed!" overrides that with the bed you were mining.
 * Everything resets when the world changes (a new game, the lobby, another server).
 */
public final class BedwarsTracker {

    private static final Pattern BED_DESTROYED = Pattern.compile("^BED DESTRUCTION > (\\w+) Bed was destroyed by (\\w+)!?$");
    private static final Pattern TEAM_ELIMINATED = Pattern.compile("^TEAM ELIMINATED > (\\w+) Team has been eliminated!?$");
    private static final int SEARCH_RADIUS = 20;
    private static final int SEARCH_TICKS = 200;

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ConcurrentLinkedQueue<String> chat = new ConcurrentLinkedQueue<>();

    private static WorldClient world;
    private static boolean inGame;
    private static int gameId;
    private static final List<BlockPos> ownBed = new ArrayList<>();
    private static boolean ownBedConfirmed;
    private static long ownBedDestroyedAt;
    private static final Set<String> destroyedBeds = new LinkedHashSet<>();
    private static final Set<String> eliminatedTeams = new LinkedHashSet<>();
    private static int searchTicks;
    private static volatile BlockPos lastDig;

    /** True from the start of a BedWars game (or the first respawn or bed message in it) until the world changes. */
    public static boolean inGame() {
        return inGame;
    }

    /** Changes every time a new game starts, so addons can reset per-game counters. */
    public static int gameId() {
        return gameId;
    }

    /** Both halves of your bed, empty while it is not known or after it was destroyed. */
    public static List<BlockPos> ownBed() {
        return Collections.unmodifiableList(ownBed);
    }

    /** Whether the position is a half of your bed. */
    public static boolean isOwnBed(BlockPos pos) {
        return pos != null && ownBed.contains(pos);
    }

    /** True once the server said "You can't destroy your own bed!" about it, rather than it being the nearest bed. */
    public static boolean isOwnBedConfirmed() {
        return ownBedConfirmed;
    }

    /** True after "BED DESTRUCTION > Your Bed was destroyed". */
    public static boolean ownBedDestroyed() {
        return ownBedDestroyedAt != 0;
    }

    /** System time of "Your Bed was destroyed", or 0. */
    public static long ownBedDestroyedAt() {
        return ownBedDestroyedAt;
    }

    /** Colour names of the other teams whose bed is gone this game, e.g. "Red". */
    public static Set<String> destroyedBeds() {
        return Collections.unmodifiableSet(destroyedBeds);
    }

    /** Colour names of the teams eliminated this game. */
    public static Set<String> eliminatedTeams() {
        return Collections.unmodifiableSet(eliminatedTeams);
    }

    @EventLink
    public final Listener<EventPacket.Incoming.Post> onPacket = event -> {
        if (event.getPacket() instanceof S02PacketChat) {
            S02PacketChat packet = (S02PacketChat) event.getPacket();
            if (packet.getType() != 2) // 2 is the action bar
                chat.add(packet.getChatComponent().getUnformattedText().replaceAll("§.", "").trim());
        }
    };

    @EventLink
    public final Listener<EventPacket.OutGoing> onSend = event -> {
        if (event.getPacket() instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging dig = (C07PacketPlayerDigging) event.getPacket();
            if (dig.getStatus() == C07PacketPlayerDigging.Action.START_DESTROY_BLOCK)
                lastDig = dig.getPosition();
        }
    };

    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (mc.theWorld != world) {
            world = mc.theWorld;
            reset(false);
        }
        String line;
        while ((line = chat.poll()) != null)
            read(line);
        if (world == null || mc.thePlayer == null)
            return;
        if (searchTicks > 0) {
            searchTicks--;
            if (searchTicks % 10 == 0 && ownBed.isEmpty())
                findNearestBed();
        }
        // a known bed that is gone from a loaded chunk no longer counts as your bed
        if (!ownBed.isEmpty() && mc.thePlayer.ticksExisted % 10 == 0)
            ownBed.removeIf(p -> world.isBlockLoaded(p) && !(world.getBlockState(p).getBlock() instanceof BlockBed));
    };

    private static void read(String line) {
        if (line.equals("Protect your bed and destroy the enemy beds.")) {
            reset(true);
            searchTicks = SEARCH_TICKS;
            return;
        }
        if (line.equals("You have respawned!")) {
            inGame = true;
            if (ownBed.isEmpty() && !ownBedDestroyed())
                searchTicks = SEARCH_TICKS;
            return;
        }
        if (line.equals("You can't destroy your own bed!")) {
            inGame = true;
            BlockPos dug = lastDig;
            if (dug != null && world != null && world.getBlockState(dug).getBlock() instanceof BlockBed) {
                setOwnBed(dug);
                ownBedConfirmed = true;
            }
            return;
        }
        Matcher m = BED_DESTROYED.matcher(line);
        if (m.matches()) {
            inGame = true;
            if (m.group(1).equals("Your")) {
                ownBedDestroyedAt = System.currentTimeMillis();
                ownBed.clear();
                searchTicks = 0;
            } else {
                destroyedBeds.add(m.group(1));
            }
            return;
        }
        m = TEAM_ELIMINATED.matcher(line);
        if (m.matches()) {
            inGame = true;
            eliminatedTeams.add(m.group(1));
        }
    }

    private static void reset(boolean gameStart) {
        inGame = gameStart;
        if (gameStart)
            gameId++;
        ownBed.clear();
        ownBedConfirmed = false;
        ownBedDestroyedAt = 0;
        destroyedBeds.clear();
        eliminatedTeams.clear();
        searchTicks = 0;
        lastDig = null;
    }

    private static void findNearestBed() {
        BlockPos origin = new BlockPos(mc.thePlayer);
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++)
            for (int y = -8; y <= 8; y++)
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    BlockPos p = origin.add(x, y, z);
                    if (!world.isBlockLoaded(p) || !(world.getBlockState(p).getBlock() instanceof BlockBed))
                        continue;
                    double d = origin.distanceSq(p);
                    if (d < best) {
                        best = d;
                        nearest = p;
                    }
                }
        if (nearest != null) {
            setOwnBed(nearest);
            searchTicks = 0;
        }
    }

    /** Your bed is the given bed block plus the bed block next to it. */
    private static void setOwnBed(BlockPos half) {
        ownBed.clear();
        ownBed.add(half);
        for (net.minecraft.util.EnumFacing f : net.minecraft.util.EnumFacing.Plane.HORIZONTAL.facings()) {
            BlockPos other = half.offset(f);
            if (world.getBlockState(other).getBlock() instanceof BlockBed) {
                ownBed.add(other);
                break;
            }
        }
    }
}
