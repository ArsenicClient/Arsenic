package arsenic.utils.minecraft;

import arsenic.utils.java.UtilityClass;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

public class WorldUtils extends UtilityClass {

    /** Every entity the client has loaded, as a list - 1.8's World#loadedEntityList. */
    public static java.util.List<net.minecraft.world.entity.Entity> entities() {
        java.util.List<net.minecraft.world.entity.Entity> list = new java.util.ArrayList<>();
        if (mc.level != null)
            mc.level.entitiesForRendering().forEach(list::add);
        return list;
    }

    /** 1.8's World#getPlayerEntityByName. */
    public static net.minecraft.world.entity.player.Player getPlayerByName(String name) {
        if (mc.level == null || name == null)
            return null;
        for (net.minecraft.world.entity.player.Player player : mc.level.players())
            if (name.equals(player.getGameProfile().name()))
                return player;
        return null;
    }

    /**
     * Every block entity in the chunks the client has loaded around the player - 1.8's
     * {@code World#loadedTileEntityList}, which no longer exists as one list.
     */
    public static List<BlockEntity> loadedBlockEntities() {
        List<BlockEntity> result = new ArrayList<>();
        if (mc.level == null || mc.player == null)
            return result;
        int radius = mc.options.getEffectiveRenderDistance();
        int cx = SectionPos.blockToSectionCoord(mc.player.getBlockX());
        int cz = SectionPos.blockToSectionCoord(mc.player.getBlockZ());
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(x, z);
                if (chunk != null)
                    result.addAll(chunk.getBlockEntities().values());
            }
        }
        return result;
    }
}
