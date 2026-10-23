package fr.isaac.cutcleanreborn.paper;

import java.util.ArrayList;
import java.util.List;
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
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

/** Configurable extra sugar-cane drops for player breaks and physics/support breaks. */
final class BetterSugarCaneService implements Listener {
    enum Cause { PLAYER("player"), SUPPORT("support"); private final String path; Cause(String path) { this.path = path; } String path() { return path; } }
    private static final String ROOT = "better-sugar-cane";
    private final CutCleanRebornPlugin plugin;
    // Keep only player-placed blocks out of the bonus. World-generated and naturally grown cane stays eligible.
    private final PlacedBlockTracker playerPlaced;

    BetterSugarCaneService(CutCleanRebornPlugin plugin) {
        this.plugin = plugin;
        playerPlaced = new PlacedBlockTracker(plugin, "placed_cane");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    boolean enabled() { return plugin.getConfig().getBoolean(ROOT + ".enabled", false); }
    void setEnabled(boolean enabled) { plugin.setConfigValue(ROOT + ".enabled", enabled); }
    boolean causeEnabled(Cause cause) { return plugin.getConfig().getBoolean(path(cause) + ".enabled", true); }
    void setCauseEnabled(Cause cause, boolean enabled) { plugin.setConfigValue(path(cause) + ".enabled", enabled); }
    double chance(Cause cause) { return GameplayMath.percent(plugin.getConfig().getDouble(path(cause) + ".chance", 100.0D), 100.0D); }
    void setChance(Cause cause, double chance) { plugin.setConfigValue(path(cause) + ".chance", Math.clamp(chance, 0.0D, 100.0D)); }
    int minimum(Cause cause) { return Math.clamp(plugin.getConfig().getInt(path(cause) + ".minimum", 1), 1, 64); }
    int maximum(Cause cause) { return Math.max(minimum(cause), Math.clamp(plugin.getConfig().getInt(path(cause) + ".maximum", 3), 1, 64)); }
    void setRange(Cause cause, int minimum, int maximum) {
        plugin.setConfigValues(java.util.Map.of(path(cause) + ".minimum", Math.clamp(minimum, 1, 64),
                path(cause) + ".maximum", Math.clamp(Math.max(minimum, maximum), 1, 64)));
    }
    void reset() { plugin.setConfigValue(ROOT, null); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.SUGAR_CANE) return;
        // Also discard the origin for creative breaks and disabled bonuses, after drops were processed.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!event.isCancelled()) playerPlaced.remove(event.getBlock());
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDrops(BlockDropItemEvent event) {
        if (event.getBlockState().getType() != Material.SUGAR_CANE || !active(Cause.PLAYER)
                || event.getPlayer().getGameMode() != GameMode.SURVIVAL || playerPlaced.contains(event.getBlock())) return;
        boolean changed = false;
        var iterator = event.getItems().iterator();
        while (iterator.hasNext()) {
            var entity = iterator.next();
            if (entity.getItemStack().getType() != Material.SUGAR_CANE) continue;
            if (changed) { iterator.remove(); continue; }
            entity.setItemStack(new ItemStack(Material.SUGAR_CANE, quantity(Cause.PLAYER, true)));
            changed = true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.SUGAR_CANE) playerPlaced.add(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSupportBreak(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (!active(Cause.SUPPORT) || block.getType() != Material.SUGAR_CANE || block.canPlace(block.getBlockData())) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.getType() != Material.SUGAR_CANE || block.canPlace(block.getBlockData())) return;
            List<Block> column = new ArrayList<>();
            for (Block current = block; current.getY() < block.getWorld().getMaxHeight()
                    && current.getType() == Material.SUGAR_CANE; current = current.getRelative(0, 1, 0)) column.add(current);
            for (Block cane : column) {
                boolean naturallyGrown = !playerPlaced.remove(cane);
                cane.setType(Material.AIR, false);
                if (cane.getWorld().getGameRuleValue(org.bukkit.GameRule.DO_TILE_DROPS) != Boolean.FALSE)
                    cane.getWorld().dropItemNaturally(cane.getLocation(), new ItemStack(Material.SUGAR_CANE, quantity(Cause.SUPPORT, naturallyGrown)));
            }
        });
    }

    private boolean active(Cause cause) { return enabled() && causeEnabled(cause); }
    private int quantity(Cause cause, boolean naturallyGrown) {
        if (naturallyGrown && ThreadLocalRandom.current().nextDouble(100.0D) < chance(cause))
            return ThreadLocalRandom.current().nextInt(minimum(cause), maximum(cause) + 1);
        return 1;
    }
    private static String path(Cause cause) { return ROOT + "." + cause.path(); }
}
