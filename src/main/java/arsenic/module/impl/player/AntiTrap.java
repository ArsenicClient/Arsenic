package arsenic.module.impl.player;

import arsenic.asm.RequiresPlayer;
import arsenic.event.bus.Listener;
import arsenic.event.bus.annotations.EventLink;
import arsenic.event.impl.EventPacket;
import arsenic.event.impl.EventTick;
import arsenic.module.Module;
import arsenic.module.ModuleCategory;
import arsenic.module.ModuleInfo;
import arsenic.module.ModuleTier;
import arsenic.module.property.impl.BooleanProperty;
import arsenic.module.property.impl.FolderProperty;
import arsenic.module.property.impl.doubleproperty.DoubleProperty;
import arsenic.module.property.impl.doubleproperty.DoubleValue;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Set;

/**
 * Hides armor stands, minecarts and the other entities used to bait trap teleports, and keeps a sign placed in your
 * face from opening its edit screen. Matching entities are dropped as they spawn and removed if already loaded.
 */
@ModuleInfo(name = "AntiTrap", description = "Hides entities used to bait trap-TPAs", category = ModuleCategory.PLAYER, tier = ModuleTier.QOL)
public class AntiTrap extends Module {

    private static final Set<EntityType<?>> MINECARTS = Set.of(
            EntityTypes.MINECART, EntityTypes.CHEST_MINECART, EntityTypes.COMMAND_BLOCK_MINECART,
            EntityTypes.FURNACE_MINECART, EntityTypes.HOPPER_MINECART, EntityTypes.SPAWNER_MINECART,
            EntityTypes.TNT_MINECART);
    private static final Set<EntityType<?>> BOATS = Set.of(
            EntityTypes.OAK_BOAT, EntityTypes.SPRUCE_BOAT, EntityTypes.BIRCH_BOAT, EntityTypes.JUNGLE_BOAT,
            EntityTypes.ACACIA_BOAT, EntityTypes.DARK_OAK_BOAT, EntityTypes.MANGROVE_BOAT, EntityTypes.CHERRY_BOAT,
            EntityTypes.PALE_OAK_BOAT, EntityTypes.BAMBOO_RAFT,
            EntityTypes.OAK_CHEST_BOAT, EntityTypes.SPRUCE_CHEST_BOAT, EntityTypes.BIRCH_CHEST_BOAT,
            EntityTypes.JUNGLE_CHEST_BOAT, EntityTypes.ACACIA_CHEST_BOAT, EntityTypes.DARK_OAK_CHEST_BOAT,
            EntityTypes.MANGROVE_CHEST_BOAT, EntityTypes.CHERRY_CHEST_BOAT, EntityTypes.PALE_OAK_CHEST_BOAT,
            EntityTypes.BAMBOO_CHEST_RAFT);

    private final BooleanProperty armorStands = new BooleanProperty("Armor Stands", true);
    private final BooleanProperty minecarts = new BooleanProperty("Minecarts", true);
    private final BooleanProperty itemFrames = new BooleanProperty("Item Frames", false);
    private final BooleanProperty boats = new BooleanProperty("Boats", false);
    private final BooleanProperty endCrystals = new BooleanProperty("End Crystals", false);
    public final FolderProperty entities = new FolderProperty("Entities", armorStands, minecarts, itemFrames, boats, endCrystals);

    public final DoubleProperty range = new DoubleProperty("Range", new DoubleValue(0, 256, 128, 1));
    public final BooleanProperty cancelSpawn = new BooleanProperty("Cancel Spawn Packets", true);
    public final BooleanProperty discardExisting = new BooleanProperty("Discard Existing", true);
    public final BooleanProperty antiSign = new BooleanProperty("Anti Sign", true);

    @EventLink
    public final Listener<EventPacket.Incoming.Pre> onIncoming = event -> {
        if (antiSign.getValue() && event.getPacket() instanceof ClientboundOpenSignEditorPacket) {
            event.cancel();
            return;
        }
        if (!cancelSpawn.getValue() || !(event.getPacket() instanceof ClientboundAddEntityPacket spawn))
            return;
        var player = mc.player;
        if (player == null || !matches(spawn.getType()))
            return;
        double dx = spawn.getX() - player.getX(), dy = spawn.getY() - player.getY(), dz = spawn.getZ() - player.getZ();
        double r = range.getValue().getInput();
        if (dx * dx + dy * dy + dz * dz <= r * r)
            event.cancel();
    };

    @RequiresPlayer
    @EventLink
    public final Listener<EventTick> onTick = event -> {
        if (antiSign.getValue() && mc.gui.screen() instanceof AbstractSignEditScreen)
            mc.gui.setScreen(null);

        if (!discardExisting.getValue() || mc.level == null)
            return;
        double r = range.getValue().getInput();
        double rSq = r * r;
        Entity vehicle = mc.player.getVehicle();
        List<Entity> found = mc.level.getEntities((Entity) null, mc.player.getBoundingBox().inflate(r), this::matches);
        for (Entity e : found) {
            // never pull the boat or cart we are riding out from under us
            if (e == vehicle || e.hasPassenger(mc.player))
                continue;
            if (e.distanceToSqr(mc.player) <= rSq)
                e.discard();
        }
    };

    private boolean matches(Entity e) {
        return (armorStands.getValue() && e instanceof ArmorStand)
                || (minecarts.getValue() && e instanceof AbstractMinecart)
                || (itemFrames.getValue() && e instanceof ItemFrame)
                || (boats.getValue() && e instanceof AbstractBoat)
                || (endCrystals.getValue() && e instanceof EndCrystal);
    }

    private boolean matches(EntityType<?> type) {
        return (armorStands.getValue() && type == EntityTypes.ARMOR_STAND)
                || (minecarts.getValue() && MINECARTS.contains(type))
                || (itemFrames.getValue() && (type == EntityTypes.ITEM_FRAME || type == EntityTypes.GLOW_ITEM_FRAME))
                || (boats.getValue() && BOATS.contains(type))
                || (endCrystals.getValue() && type == EntityTypes.END_CRYSTAL);
    }
}
