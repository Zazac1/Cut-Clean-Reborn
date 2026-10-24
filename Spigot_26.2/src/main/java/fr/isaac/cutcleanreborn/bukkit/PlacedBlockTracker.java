package fr.isaac.cutcleanreborn.bukkit;

import java.util.Arrays;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;

/** Chunk-persistent origins: player-placed blocks remain identifiable after unloads and restarts. */
final class PlacedBlockTracker {
    private final NamespacedKey key;

    PlacedBlockTracker(CutCleanRebornPlugin plugin, String name) { key = new NamespacedKey(plugin, name); }

    boolean contains(Block block) {
        int[] positions = block.getChunk().getPersistentDataContainer().get(key, PersistentDataType.INTEGER_ARRAY);
        return positions != null && Arrays.binarySearch(positions, position(block)) >= 0;
    }

    void add(Block block) {
        var data = block.getChunk().getPersistentDataContainer();
        int[] old = data.getOrDefault(key, PersistentDataType.INTEGER_ARRAY, new int[0]);
        int position = position(block);
        if (Arrays.binarySearch(old, position) >= 0) return;
        int[] updated = Arrays.copyOf(old, old.length + 1);
        updated[old.length] = position;
        Arrays.sort(updated);
        data.set(key, PersistentDataType.INTEGER_ARRAY, updated);
    }

    boolean remove(Block block) {
        var data = block.getChunk().getPersistentDataContainer();
        int[] old = data.get(key, PersistentDataType.INTEGER_ARRAY);
        if (old == null) return false;
        int position = position(block);
        if (Arrays.binarySearch(old, position) < 0) return false;
        int[] updated = Arrays.stream(old).filter(value -> value != position).toArray();
        if (updated.length == 0) data.remove(key);
        else data.set(key, PersistentDataType.INTEGER_ARRAY, updated);
        return true;
    }

    static int localPosition(int x, int y, int z, int minimumY) {
        return ((y - minimumY) << 8) | ((z & 15) << 4) | (x & 15);
    }

    private static int position(Block block) {
        return localPosition(block.getX(), block.getY(), block.getZ(), block.getWorld().getMinHeight());
    }
}

