package fr.isaac.cutcleanreborn.bukkit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/** Natural-tree harvesting in small batches using Bukkit's normal player-break pipeline. */
final class TreeCutterService implements Listener {
    private static final BlockFace[] FACES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
    private final CutCleanRebornPlugin plugin;
    private final PlacedBlockTracker placed;
    private final Set<UUID> harvesting = new HashSet<>();
    private final Set<UUID> internalBreak = new HashSet<>();
    private final Map<UUID, Progress> progress = new HashMap<>();
    private final Map<BlockBreakEvent, Tree> ready = new HashMap<>();

    TreeCutterService(CutCleanRebornPlugin plugin) {
        this.plugin = plugin;
        placed = new PlacedBlockTracker(plugin, "placed_timber");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    void clearProgress() { progress.clear(); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        if (Tag.LOGS.isTagged(event.getBlockPlaced().getType())) placed.add(event.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void prepare(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block first = event.getBlock();
        if (!plugin.treeCutterEnabled() || player.getGameMode() != GameMode.SURVIVAL || !event.isDropItems()
                || harvesting.contains(player.getUniqueId()) || internalBreak.contains(player.getUniqueId())
                || !Tag.LOGS.isTagged(first.getType()) || placed.contains(first)
                || (plugin.treeCutterSneakOnly() && !player.isSneaking()) || !toolAllowed(player.getInventory().getItemInMainHand())) return;
        int maximum = plugin.getBoundedInt("tree-cutter.max-logs", 256, 2, 2048);
        List<Block> logs = connectedLogs(treeBase(first), maximum);
        if (logs.size() < 2 || logs.size() > maximum) return;
        List<Block> leaves = leavesFor(logs);
        if (leaves.isEmpty()) return;
        if (plugin.treeCutterBreakTimeEnabled() && !completeBreak(player, first, logs.size())) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) player.sendBlockChange(first.getLocation(), first.getBlockData());
            });
            return;
        }
        ready.put(event, new Tree(logs, plugin.treeCutterLeavesEnabled() ? leaves : List.of()));
        plugin.getServer().getScheduler().runTask(plugin, () -> ready.remove(event));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterBreak(BlockBreakEvent event) {
        placed.remove(event.getBlock());
        Tree tree = ready.remove(event);
        if (tree == null || !event.isDropItems()) return;
        Player player = event.getPlayer();
        Material tool = player.getInventory().getItemInMainHand().getType();
        Material logType = event.getBlock().getType();
        ArrayDeque<Block> logs = new ArrayDeque<>(tree.logs().stream().filter(block -> !block.equals(event.getBlock()))
                .sorted(Comparator.comparingInt(Block::getY)).toList());
        ArrayDeque<Block> leaves = new ArrayDeque<>(tree.leaves());
        harvesting.add(player.getUniqueId());
        new BukkitRunnable() {
            private int cut = 1;
            @Override public void run() {
                if (event.isCancelled() || !event.isDropItems() || !player.isOnline() || !plugin.treeCutterEnabled() || player.getGameMode() != GameMode.SURVIVAL
                        || player.getInventory().getItemInMainHand().getType() != tool) { finish(); return; }
                int budget = plugin.getBoundedInt("tree-cutter.blocks-per-tick", 12, 1, 64);
                for (int i = 0; i < budget && (!logs.isEmpty() || !leaves.isEmpty()); i++) {
                    boolean isLog = !logs.isEmpty();
                    Block block = isLog ? logs.removeFirst() : leaves.removeFirst();
                    if (!loaded(block) || !player.getWorld().equals(block.getWorld())
                            || block.getLocation().distanceSquared(player.getLocation()) > 64 * 64) { finish(); return; }
                    if (isLog ? block.getType() != logType || placed.contains(block) : !naturalLeaf(block)) continue;
                    internalBreak.add(player.getUniqueId());
                    boolean broken;
                    try { broken = player.breakBlock(block); }
                    finally { internalBreak.remove(player.getUniqueId()); }
                    if (!broken) { finish(); return; }
                    if (isLog) cut++;
                    if (player.getInventory().getItemInMainHand().getType() != tool) { finish(); return; }
                }
                if (logs.isEmpty() && leaves.isEmpty()) finish();
            }
            private void finish() {
                harvesting.remove(player.getUniqueId());
                if (player.isOnline() && cut > 1) BukkitCompat.actionBar(player, MenuItems.component("Timber • " + cut + " logs"));
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void apples(BlockDropItemEvent event) {
        Material leaves = event.getBlockState().getType();
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!plugin.hasCustomAppleChance() || (leaves != Material.OAK_LEAVES && leaves != Material.DARK_OAK_LEAVES)
                || tool.getType() == Material.SHEARS || tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;
        event.getItems().removeIf(item -> item.getItemStack().getType() == Material.APPLE);
        if (ThreadLocalRandom.current().nextDouble(100) >= plugin.treeAppleChance()) return;
        var location = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!event.isCancelled()) location.getWorld().dropItemNaturally(location, new ItemStack(Material.APPLE));
        });
    }

    @EventHandler public void quit(PlayerQuitEvent event) { progress.remove(event.getPlayer().getUniqueId()); }

    private boolean toolAllowed(ItemStack tool) {
        return tool.getType().name().endsWith("_AXE") ? plugin.treeCutterAxeEnabled(tool.getType()) : plugin.treeCutterHandEnabled();
    }

    private Block treeBase(Block block) {
        Block base = block;
        for (int depth = 0; depth < 64; depth++) {
            Block below = base.getRelative(BlockFace.DOWN);
            if (below.getType() != block.getType() || placed.contains(below)) break;
            base = below;
        }
        return base;
    }

    private List<Block> connectedLogs(Block first, int maximum) {
        List<Block> result = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> pending = new ArrayDeque<>();
        pending.add(first); seen.add(first);
        while (!pending.isEmpty() && result.size() <= maximum) {
            Block current = pending.removeFirst();
            result.add(current);
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                Block next = current.getRelative(x, y, z);
                if (loaded(next) && next.getType() == first.getType() && seen.add(next) && !placed.contains(next)) pending.addLast(next);
            }
        }
        return result;
    }

    private static List<Block> leavesFor(List<Block> logs) {
        // Follow the vanilla distance gradient outwards once, rather than searching a forest for every leaf.
        Set<Block> leaves = new HashSet<>();
        ArrayDeque<Block> pending = new ArrayDeque<>();
        for (Block log : logs) for (BlockFace face : FACES) {
            Block leaf = log.getRelative(face);
            if (loaded(leaf) && naturalLeaf(leaf) && ((Leaves) leaf.getBlockData()).getDistance() == 1 && leaves.add(leaf)) pending.add(leaf);
        }
        while (!pending.isEmpty() && leaves.size() < 8192) {
            Block current = pending.removeFirst();
            int distance = ((Leaves) current.getBlockData()).getDistance();
            if (distance >= 6) continue;
            for (BlockFace face : FACES) {
                Block next = current.getRelative(face);
                if (loaded(next) && naturalLeaf(next) && ((Leaves) next.getBlockData()).getDistance() == distance + 1 && leaves.add(next)) pending.addLast(next);
            }
        }
        return List.copyOf(leaves);
    }

    private boolean completeBreak(Player player, Block first, int count) {
        long now = System.currentTimeMillis();
        Progress old = progress.get(player.getUniqueId());
        int completed = old != null && old.block().equals(first) && now - old.lastBreakAt() < 15_000 ? old.completed() + 1 : 1;
        int required = Math.max(1, (int) Math.ceil(1 + (count - 1) * plugin.treeCutterBreakTimePercent() / 100));
        BukkitCompat.actionBar(player, MenuItems.component("Timber • " + Math.min(completed, required) + "/" + required));
        if (completed >= required) { progress.remove(player.getUniqueId()); return true; }
        progress.put(player.getUniqueId(), new Progress(first, completed, now));
        return false;
    }

    private static boolean naturalLeaf(Block block) { return block.getBlockData() instanceof Leaves leaves && !leaves.isPersistent(); }
    private static boolean loaded(Block block) { return block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4); }
    private record Tree(List<Block> logs, List<Block> leaves) { }
    private record Progress(Block block, int completed, long lastBreakAt) { }
}

