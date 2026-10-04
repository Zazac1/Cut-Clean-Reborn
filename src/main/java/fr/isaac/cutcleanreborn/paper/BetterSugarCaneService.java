package fr.isaac.cutcleanreborn.paper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.inventory.ItemStack;

/** Configurable extra sugar-cane drops for player breaks and physics/support breaks. */
final class BetterSugarCaneService implements Listener {
    enum Cause { PLAYER("player"), SUPPORT("support"); private final String path; Cause(String path) { this.path = path; } String path() { return path; } }
    private static final String ROOT = "better-sugar-cane";
    private final CutCleanRebornPlugin plugin;
    // Keep only player-placed blocks out of the bonus. World-generated and naturally grown cane stays eligible.
    private final Set<BlockKey> playerPlaced = new HashSet<>();

    BetterSugarCaneService(CutCleanRebornPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    boolean enabled() { return plugin.getConfig().getBoolean(ROOT + ".enabled", false); }
    void setEnabled(boolean enabled) { plugin.setConfigValue(ROOT + ".enabled", enabled); }
    boolean causeEnabled(Cause cause) { return plugin.getConfig().getBoolean(path(cause) + ".enabled", true); }
    void setCauseEnabled(Cause cause, boolean enabled) { plugin.setConfigValue(path(cause) + ".enabled", enabled); }
    double chance(Cause cause) { return Math.clamp(plugin.getConfig().getDouble(path(cause) + ".chance", 100.0D), 0.0D, 100.0D); }
    void setChance(Cause cause, double chance) { plugin.setConfigValue(path(cause) + ".chance", Math.clamp(chance, 0.0D, 100.0D)); }
    int minimum(Cause cause) { return Math.clamp(plugin.getConfig().getInt(path(cause) + ".minimum", 1), 1, 64); }
    int maximum(Cause cause) { return Math.max(minimum(cause), Math.clamp(plugin.getConfig().getInt(path(cause) + ".maximum", 3), 1, 64)); }
    void setRange(Cause cause, int minimum, int maximum) {
        plugin.setConfigValue(path(cause) + ".minimum", Math.clamp(minimum, 1, 64));
        plugin.setConfigValue(path(cause) + ".maximum", Math.clamp(Math.max(minimum, maximum), 1, 64));
    }
    void reset() { plugin.setConfigValue(ROOT, null); }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerBreak(BlockBreakEvent event) {
        if (!active(Cause.PLAYER) || event.getBlock().getType() != Material.SUGAR_CANE) return;
        if (consumePlayerPlacement(event.getBlock())) return; // A player-placed cane keeps its vanilla drop.
        event.setDropItems(false);
        drop(event.getBlock(), Cause.PLAYER, true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.SUGAR_CANE) playerPlaced.add(key(event.getBlockPlaced()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSupportBreak(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (!active(Cause.SUPPORT) || block.getType() != Material.SUGAR_CANE || block.canPlace(block.getBlockData())) return;
        // Replace the vanilla physics break so its drop follows the configured roll exactly once.
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.getType() != Material.SUGAR_CANE || block.canPlace(block.getBlockData())) return;
            // A no-physics replacement does not reliably notify every upper cane block.
            // Collect and handle the complete column ourselves, including columns taller than two.
            List<Block> column = new ArrayList<>();
            for (Block current = block; current.getType() == Material.SUGAR_CANE; current = current.getRelative(0, 1, 0)) column.add(current);
            for (Block cane : column) cane.setType(Material.AIR, false);
            for (Block cane : column) drop(cane, Cause.SUPPORT, !consumePlayerPlacement(cane));
        });
    }

    private boolean active(Cause cause) { return enabled() && causeEnabled(cause); }
    private void drop(Block block, Cause cause, boolean grownNaturally) {
        int amount = 1;
        if (grownNaturally && ThreadLocalRandom.current().nextDouble(100.0D) < chance(cause))
            amount = ThreadLocalRandom.current().nextInt(minimum(cause), maximum(cause) + 1);
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.SUGAR_CANE, amount));
    }
    private boolean consumePlayerPlacement(Block block) { return playerPlaced.remove(key(block)); }
    private static BlockKey key(Block block) { return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
    private static String path(Cause cause) { return ROOT + "." + cause.path(); }
    private record BlockKey(java.util.UUID world, int x, int y, int z) { }
}
