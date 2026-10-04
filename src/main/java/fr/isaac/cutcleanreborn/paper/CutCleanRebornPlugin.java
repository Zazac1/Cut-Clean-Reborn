package fr.isaac.cutcleanreborn.paper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Entry point and server-side application of the Cut Clean mob rules. */
public final class CutCleanRebornPlugin extends JavaPlugin implements Listener {
    private static final Map<Material, Material> COOKED_DROPS = Map.of(
            Material.BEEF, Material.COOKED_BEEF, Material.CHICKEN, Material.COOKED_CHICKEN,
            Material.MUTTON, Material.COOKED_MUTTON, Material.PORKCHOP, Material.COOKED_PORKCHOP,
            Material.RABBIT, Material.COOKED_RABBIT, Material.COD, Material.COOKED_COD,
            Material.SALMON, Material.COOKED_SALMON);
    private AutoEnchantService autoEnchantService;
    private CleanStackService cleanStacks;
    private BetterSugarCaneService betterSugarCane;
    private StuffRulesService stuffRules;
    private final Map<java.util.UUID, Map<Block, TreeBreakProgress>> treeBreakProgress = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        autoEnchantService = new AutoEnchantService(this);
        cleanStacks = new CleanStackService(this);
        betterSugarCane = new BetterSugarCaneService(this);
        stuffRules = new StuffRulesService(this);
        getLogger().info("CutCleanReborn Paper loaded. Use /ccr config to configure mobs.");
    }

    AutoEnchantService autoEnchantments() { return autoEnchantService; }
    CleanStackService cleanStacks() { return cleanStacks; }
    BetterSugarCaneService betterSugarCane() { return betterSugarCane; }
    StuffRulesService stuffRules() { return stuffRules; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("cleanstacks")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("§cThis command must be used in-game."); return true; }
            if (args.length != 1 || (!args[0].equalsIgnoreCase("true") && !args[0].equalsIgnoreCase("false"))) {
                player.sendMessage("§eUsage: /cleanstacks <true|false>"); return true;
            }
            boolean enabled = Boolean.parseBoolean(args[0]);
            cleanStacks.setEnabled(player, enabled);
            player.sendMessage("§aCCR §8» §7Clean Stacks: " + (enabled ? "§aON" : "§cOFF"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("ping")) {
            sender.sendMessage("§a[CCR] Pong!");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("config")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cThis command must be used in-game.");
            } else if (!player.hasPermission("cutcleanreborn.config")) {
                player.sendMessage("§cYou do not have permission to change the configuration.");
            } else {
                new ConfigMainMenu(this).open(player);
            }
            return true;
        }
        sender.sendMessage("§eUsage: /" + label + " <config|ping>");
        return true;
    }

    /** Minecraft's Tab key exposes the valid command arguments without sending a command. */
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> choices = command.getName().equalsIgnoreCase("cleanstacks")
                ? List.of("true", "false")
                : List.of("config", "ping");
        String typed = args[0].toLowerCase(java.util.Locale.ROOT);
        return choices.stream().filter(choice -> choice.startsWith(typed)).toList();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof LivingEntity) || event.getEntityType() == EntityType.PLAYER) return;
        EntityType mob = event.getEntityType();
        int lootingLevel = getLootingLevel(event.getEntity().getKiller());
        List<ItemStack> adjustedDrops = new ArrayList<>();
        boolean cook = getMobCookedFood(mob);
        for (ItemStack original : event.getDrops()) {
            int multiplier = isAnimal(mob) ? getBoundedInt("animal-drops.multiplier", 1, 1, 5) : 1;
            // Vanilla entries are identified before cooking so an edited Raw Beef entry still works when cooking is on.
            MobDrop override = getVanillaDropOverride(mob, original.getType());
            if (override != null) {
                if (ThreadLocalRandom.current().nextDouble(100.0D) < override.chance()) {
                    int amount = ThreadLocalRandom.current().nextInt(override.minimum(), override.maximum() + 1)
                            * (mobLootingVanillaEnabled() ? lootMultiplier(lootingLevel) : 1);
                    if (amount > 0) {
                        ItemStack replacement = new ItemStack(override.material(), amount);
                        Material cookedReplacement = cook ? COOKED_DROPS.get(replacement.getType()) : null;
                        if (cookedReplacement != null) replacement.setType(cookedReplacement);
                        addStacks(adjustedDrops, replacement, multiplier);
                    }
                }
                continue;
            }
            ItemStack adjusted = original.clone();
            Material cooked = cook ? COOKED_DROPS.get(adjusted.getType()) : null;
            if (cooked != null) adjusted.setType(cooked);
            Material replacement = getLegacyPrimaryFoodOverride(mob, adjusted.getType());
            if (replacement != null) adjusted.setType(replacement);
            addStacks(adjustedDrops, adjusted, multiplier);
        }
        for (MobDrop drop : getMobDrops(mob)) {
            if (ThreadLocalRandom.current().nextDouble(100.0D) < drop.chance()) {
                int amount = ThreadLocalRandom.current().nextInt(drop.minimum(), drop.maximum() + 1)
                        * (mobLootingCustomEnabled() ? lootMultiplier(lootingLevel) : 1);
                if (amount > 0) addStacks(adjustedDrops, new ItemStack(drop.material(), amount), 1);
            }
        }
        event.getDrops().clear();
        event.getDrops().addAll(adjustedDrops);
        event.setDroppedExp(event.getDroppedExp() + getMobXpBonus(mob));
        collectForKillerInRange(event, getBoundedInt("animal-drops.collection-range", 0, 0, 32));
    }

    /** Applies configured ore overrides after Paper has evaluated the real block loot table. */
    @EventHandler(ignoreCancelled = true)
    public void onOreBreak(BlockBreakEvent event) {
        Material ore = event.getBlock().getType();
        OreDefinition definition = OreCatalog.find(ore);
        boolean autoSmelt = oreAutoSmeltEnabled();
        int fortuneLevel = getFortuneLevel(event.getPlayer());
        boolean removeFortune = !oreFortuneVanillaEnabled() && fortuneLevel > 0;
        if (definition == null || (!hasOreCustomisation(ore) && !autoSmelt && !removeFortune)) return;
        ItemStack miningTool = event.getPlayer().getInventory().getItemInMainHand();
        if (removeFortune) {
            miningTool = miningTool.clone();
            miningTool.removeEnchantment(org.bukkit.enchantments.Enchantment.FORTUNE);
        }
        List<ItemStack> vanilla = new ArrayList<>(event.getBlock().getDrops(miningTool, event.getPlayer()));
        // Preserve silk touch and incorrect-tool behaviour exactly as vanilla does.
        if (vanilla.stream().noneMatch(item -> item.getType() == definition.normalDrop())) return;
        event.setExpToDrop(event.getExpToDrop() + getOreXpBonus(ore));
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack original : vanilla) {
            MobDrop override = getOreVanillaOverride(ore);
            if (override == null) {
                ItemStack output = autoSmelt ? smeltOreDrop(original) : original;
                result.add(output);
                if (autoSmelt && output.getType() != original.getType()) event.setExpToDrop(event.getExpToDrop() + furnaceExperience(original));
            }
            else if (ThreadLocalRandom.current().nextDouble(100.0D) < override.chance()) {
                int amount = ThreadLocalRandom.current().nextInt(override.minimum(), override.maximum() + 1)
                        * (oreFortuneVanillaEnabled() ? lootMultiplier(fortuneLevel) : 1);
                if (amount > 0) {
                    ItemStack output = new ItemStack(override.material(), amount);
                    ItemStack smelted = autoSmelt ? smeltOreDrop(output) : output;
                    result.add(smelted);
                    if (autoSmelt && smelted.getType() != output.getType()) event.setExpToDrop(event.getExpToDrop() + furnaceExperience(output));
                }
            }
        }
        for (MobDrop drop : getOreDrops(ore)) if (ThreadLocalRandom.current().nextDouble(100.0D) < drop.chance()) {
            int amount = ThreadLocalRandom.current().nextInt(drop.minimum(), drop.maximum() + 1)
                    * (oreFortuneCustomEnabled() ? lootMultiplier(fortuneLevel) : 1);
            if (amount > 0) {
                ItemStack output = new ItemStack(drop.material(), amount);
                ItemStack smelted = autoSmelt ? smeltOreDrop(output) : output;
                result.add(smelted);
                if (autoSmelt && smelted.getType() != output.getType()) event.setExpToDrop(event.getExpToDrop() + furnaceExperience(output));
            }
        }
        event.setDropItems(false);
        for (ItemStack item : result) event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);
    }

    /** Breaks a connected natural tree after its first log is cut. */
    @EventHandler(ignoreCancelled = true)
    public void onTreeLogBreak(BlockBreakEvent event) {
        Block first = event.getBlock();
        if (!treeCutterEnabled() || (treeCutterSneakOnly() && !event.getPlayer().isSneaking())
                || !Tag.LOGS.isTagged(first.getType()) || !treeCutterToolAllowed(event.getPlayer().getInventory().getItemInMainHand())) return;
        // A player can start anywhere on the trunk. Descend first so a mid- or upper-log
        // break still discovers the roots and every branch above them.
        List<Block> logs = connectedLogs(treeBase(first));
        List<Block> associatedLeaves = connectedTreeLeaves(logs);
        if (logs.size() < 2 || associatedLeaves.isEmpty()) return;
        if (treeCutterBreakTimeEnabled() && !completeTimedTreeBreak(event.getPlayer(), first, logs.size())) {
            event.setCancelled(true);
            // Re-send the unchanged log on the next tick so the client starts a new break cycle.
            getServer().getScheduler().runTask(this, () -> {
                if (first.getType() != Material.AIR) event.getPlayer().sendBlockChange(first.getLocation(), first.getBlockData());
            });
            return;
        }
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        List<Block> leaves = treeCutterLeavesEnabled() ? associatedLeaves : List.of();
        // The final successful hit is handled by Paper; remove the rest immediately.
        harvestTreeBlocks(first, logs, leaves, tool, event.getPlayer());
    }

    /** Replaces only apple rolls; saplings, sticks and all other vanilla leaf loot are kept. */
    @EventHandler(ignoreCancelled = true)
    public void onLeafDrops(BlockDropItemEvent event) {
        if (!hasCustomAppleChance() || !isAppleLeaf(event.getBlockState().getType())) return;
        event.getItems().removeIf(item -> item.getItemStack().getType() == Material.APPLE);
        if (ThreadLocalRandom.current().nextDouble(100.0D) < treeAppleChance())
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.APPLE));
    }

    boolean getMobCookedFood(EntityType mob) {
        String path = mobPath(mob, "cooked-food");
        return getConfig().contains(path) ? getConfig().getBoolean(path)
                : isAnimal(mob) && getConfig().getBoolean("animal-drops.cook", true);
    }
    void setMobCookedFood(EntityType mob, boolean value) {
        String path = mobPath(mob, "cooked-food");
        // Returning to the inherited value is not an active mob customization.
        getConfig().set(path, value == defaultMobCookedFood(mob) ? null : value);
        touchMob(mob);
    }
    int getMobXpBonus(EntityType mob) {
        String path = mobPath(mob, "xp-bonus");
        return getConfig().contains(path) ? getBoundedInt(path, 0, 0, 1_000_000)
                : isAnimal(mob) ? getBoundedInt("animal-drops.xp-bonus", 0, 0, 50) : 0;
    }
    void setMobXpBonus(EntityType mob, int value) {
        String path = mobPath(mob, "xp-bonus");
        int bounded = Math.clamp(value, 0, 1_000_000);
        getConfig().set(path, bounded == defaultMobXpBonus(mob) ? null : bounded);
        touchMob(mob);
    }

    List<MobDrop> getMobDrops(EntityType mob) {
        return getDrops(mobPath(mob, "drops"));
    }
    List<MobDrop> getVanillaDropOverrides(EntityType mob) {
        return getDrops(mobPath(mob, "vanilla-overrides"));
    }
    private List<MobDrop> getDrops(String path) {
        List<MobDrop> drops = new ArrayList<>();
        for (Map<?, ?> entry : getConfig().getMapList(path)) {
            Material material = entry.get("item") instanceof String id ? MenuItems.findItem(id) : null;
            int minimum = number(entry.get("minimum"), 1), maximum = number(entry.get("maximum"), minimum);
            double chance = decimal(entry.get("chance"), 100.0D);
            if (material != null && minimum >= 0 && maximum >= minimum && chance >= 0.0D && chance <= 100.0D)
                drops.add(new MobDrop(material, minimum, maximum, chance));
        }
        return drops;
    }
    void saveMobDrops(EntityType mob, List<MobDrop> drops) {
        saveDrops(mobPath(mob, "drops"), drops);
        touchMob(mob);
    }
    void saveVanillaDropOverrides(EntityType mob, List<MobDrop> drops) {
        saveDrops(mobPath(mob, "vanilla-overrides"), drops);
        touchMob(mob);
    }
    List<MobDrop> getOreDrops(Material ore) { return getDrops(orePath(ore, "drops")); }
    List<MobDrop> getOreVanillaDropOverrides(Material ore) { return getDrops(orePath(ore, "vanilla-overrides")); }
    void saveOreDrops(Material ore, List<MobDrop> drops) {
        saveDrops(orePath(ore, "drops"), drops);
        saveConfig();
    }
    void saveOreVanillaDropOverrides(Material ore, List<MobDrop> drops) {
        saveDrops(orePath(ore, "vanilla-overrides"), drops);
        saveConfig();
    }
    int getOreXpBonus(Material ore) { return getBoundedInt(orePath(ore, "xp-bonus"), 0, 0, 1_000_000); }
    void setOreXpBonus(Material ore, int value) {
        getConfig().set(orePath(ore, "xp-bonus"), Math.clamp(value, 0, 1_000_000));
        saveConfig();
    }
    boolean oreAutoSmeltEnabled() { return getConfig().getBoolean("ore-drops.auto-smelt", false); }
    void setOreAutoSmeltEnabled(boolean enabled) { setConfigValue("ore-drops.auto-smelt", enabled); }
    boolean oreFortuneVanillaEnabled() { return getConfig().getBoolean("ore-drops.fortune-vanilla", true); }
    void setOreFortuneVanillaEnabled(boolean enabled) { setConfigValue("ore-drops.fortune-vanilla", enabled); }
    boolean oreFortuneCustomEnabled() { return getConfig().getBoolean("ore-drops.fortune-custom", false); }
    void setOreFortuneCustomEnabled(boolean enabled) { setConfigValue("ore-drops.fortune-custom", enabled); }
    boolean mobLootingVanillaEnabled() { return getConfig().getBoolean("mob-drops.looting-vanilla", true); }
    void setMobLootingVanillaEnabled(boolean enabled) { setConfigValue("mob-drops.looting-vanilla", enabled); }
    boolean mobLootingCustomEnabled() { return getConfig().getBoolean("mob-drops.looting-custom", false); }
    void setMobLootingCustomEnabled(boolean enabled) { setConfigValue("mob-drops.looting-custom", enabled); }
    boolean treeCutterEnabled() { return getConfig().getBoolean("tree-cutter.enabled", true); }
    void setTreeCutterEnabled(boolean enabled) { setConfigValue("tree-cutter.enabled", enabled); }
    boolean treeCutterHandEnabled() { return getConfig().getBoolean("tree-cutter.hand", false); }
    void setTreeCutterHandEnabled(boolean enabled) { setConfigValue("tree-cutter.hand", enabled); }
    boolean treeCutterAxeEnabled(Material axe) { return getConfig().getBoolean("tree-cutter.axes." + axe.name(), true); }
    void setTreeCutterAxeEnabled(Material axe, boolean enabled) { setConfigValue("tree-cutter.axes." + axe.name(), enabled); }
    double treeAppleChance() { return decimal(getConfig().get("tree-cutter.apple-chance"), 0.5D); }
    boolean hasCustomAppleChance() { return getConfig().contains("tree-cutter.apple-chance"); }
    void setTreeAppleChance(double chance) { setConfigValue("tree-cutter.apple-chance", Math.clamp(chance, 0.0D, 100.0D)); }
    void resetTreeAppleChance() { setConfigValue("tree-cutter.apple-chance", null); }
    boolean treeCutterLeavesEnabled() { return getConfig().getBoolean("tree-cutter.break-leaves", false); }
    void setTreeCutterLeavesEnabled(boolean enabled) { setConfigValue("tree-cutter.break-leaves", enabled); }
    boolean treeCutterSneakOnly() { return getConfig().getBoolean("tree-cutter.sneak-only", false); }
    void setTreeCutterSneakOnly(boolean enabled) { setConfigValue("tree-cutter.sneak-only", enabled); }
    boolean treeCutterBreakTimeEnabled() { return getConfig().getBoolean("tree-cutter.break-time.enabled", false); }
    void setTreeCutterBreakTimeEnabled(boolean enabled) { setConfigValue("tree-cutter.break-time.enabled", enabled); }
    double treeCutterBreakTimePercent() { return decimal(getConfig().get("tree-cutter.break-time.percent"), 100.0D); }
    void setTreeCutterBreakTimePercent(double percent) { setConfigValue("tree-cutter.break-time.percent", Math.clamp(percent, 0.0D, 100.0D)); }
    void copyOreSettings(Material source, Material target) {
        saveOreDrops(target, getOreDrops(source));
        saveOreVanillaDropOverrides(target, getOreVanillaDropOverrides(source));
        setOreXpBonus(target, getOreXpBonus(source));
    }
    private boolean hasOreCustomisation(Material ore) {
        return getOreXpBonus(ore) > 0 || !getOreDrops(ore).isEmpty() || !getOreVanillaDropOverrides(ore).isEmpty();
    }
    // Each configured ore currently has one vanilla loot-table output. The override stores
    // its replacement item, so it must not be looked up by that replacement's material.
    private MobDrop getOreVanillaOverride(Material ore) {
        return getOreVanillaDropOverrides(ore).stream().findFirst().orElse(null);
    }
    private static ItemStack smeltOreDrop(ItemStack input) {
        Material cooked = switch (input.getType()) {
            case RAW_IRON -> Material.IRON_INGOT;
            case RAW_GOLD -> Material.GOLD_INGOT;
            case RAW_COPPER -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            default -> null;
        };
        return cooked == null ? input : new ItemStack(cooked, input.getAmount());
    }
    private static int furnaceExperience(ItemStack input) {
        double perItem = switch (input.getType()) {
            case RAW_IRON, RAW_COPPER -> 0.7D;
            case RAW_GOLD -> 1.0D;
            case ANCIENT_DEBRIS -> 2.0D;
            default -> 0.0D;
        };
        double total = perItem * input.getAmount();
        int whole = (int) total;
        return whole + (ThreadLocalRandom.current().nextDouble() < total - whole ? 1 : 0);
    }
    private static int getFortuneLevel(Player player) { return player.getInventory().getItemInMainHand().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FORTUNE); }
    private static int getLootingLevel(Player player) { return player == null ? 0 : player.getInventory().getItemInMainHand().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LOOTING); }
    private static int lootMultiplier(int level) {
        if (level <= 0) return 1;
        return Math.max(0, ThreadLocalRandom.current().nextInt(level + 1) - 1) + 1;
    }
    private boolean treeCutterToolAllowed(ItemStack tool) {
        if (tool == null || !tool.getType().name().endsWith("_AXE")) return treeCutterHandEnabled();
        // An explicitly disabled axe must remain disabled rather than falling back to this option.
        return treeCutterAxeEnabled(tool.getType());
    }
    private static Block treeBase(Block first) {
        Block base = first;
        for (int depth = 0; depth < 64; depth++) {
            Block below = base.getRelative(BlockFace.DOWN);
            if (below.getType() != first.getType()) break;
            base = below;
        }
        return base;
    }
    private static List<Block> connectedLogs(Block first) {
        List<Block> result = new ArrayList<>(); java.util.ArrayDeque<Block> pending = new java.util.ArrayDeque<>();
        java.util.HashSet<Block> seen = new java.util.HashSet<>(); pending.add(first); seen.add(first);
        while (!pending.isEmpty() && result.size() < 2048) {
            Block current = pending.removeFirst(); result.add(current);
            // Giant trees can have diagonal branches or 2x2 trunks; scan the full local cube.
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                if (x == 0 && y == 0 && z == 0) continue;
                Block next = current.getRelative(x, y, z);
                if (next.getType() == first.getType() && seen.add(next)) pending.addLast(next);
            }
        }
        return result;
    }
    private static boolean isNaturalLeaf(Block block) {
        return block.getBlockData() instanceof Leaves leaves && !leaves.isPersistent();
    }
    private static boolean isAppleLeaf(Material material) { return material == Material.OAK_LEAVES || material == Material.DARK_OAK_LEAVES; }
    private boolean completeTimedTreeBreak(Player player, Block first, int logCount) {
        if (treeCutterBreakTimePercent() <= 0.0D) return true;
        Map<Block, TreeBreakProgress> byBlock = treeBreakProgress.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        TreeBreakProgress previous = byBlock.get(first);
        int completed = previous != null && System.currentTimeMillis() - previous.lastBreakAt() < 15_000L ? previous.completedBreaks() + 1 : 1;
        int required = Math.max(1, (int) Math.ceil(1.0D + (logCount - 1) * treeCutterBreakTimePercent() / 100.0D));
        if (completed >= required) {
            byBlock.remove(first);
            player.sendActionBar(net.kyori.adventure.text.Component.text("Timber: " + required + "/" + required));
            return true;
        }
        byBlock.put(first, new TreeBreakProgress(completed, System.currentTimeMillis()));
        player.sendActionBar(net.kyori.adventure.text.Component.text("Timber: " + completed + "/" + required));
        return false;
    }
    private void harvestTreeBlocks(Block first, List<Block> logs, List<Block> leaves, ItemStack tool, Player player) {
        for (Block log : logs) {
            if (log.equals(first) || !Tag.LOGS.isTagged(log.getType())) continue;
            for (ItemStack drop : log.getDrops(tool, player)) log.getWorld().dropItemNaturally(log.getLocation(), drop);
            log.setType(Material.AIR, true);
        }
        for (Block leaf : leaves) {
            if (!Tag.LEAVES.isTagged(leaf.getType())) continue;
            for (ItemStack drop : leaf.getDrops(tool, player)) {
                if (hasCustomAppleChance() && drop.getType() == Material.APPLE) continue;
                leaf.getWorld().dropItemNaturally(leaf.getLocation(), drop);
            }
            if (hasCustomAppleChance() && isAppleLeaf(leaf.getType()) && ThreadLocalRandom.current().nextDouble(100.0D) < treeAppleChance())
                leaf.getWorld().dropItemNaturally(leaf.getLocation(), new ItemStack(Material.APPLE));
            leaf.setType(Material.AIR, true);
        }
    }
    private static List<Block> connectedTreeLeaves(List<Block> logs) {
        java.util.Set<Block> treeLogs = new java.util.HashSet<>(logs);
        java.util.LinkedHashSet<Block> candidates = new java.util.LinkedHashSet<>();
        java.util.ArrayDeque<Block> pending = new java.util.ArrayDeque<>();
        BlockFace[] faces = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        // Start at leaf blocks touching this tree, then walk the complete leaf component.
        for (Block log : logs) for (BlockFace face : faces) {
            Block leaf = log.getRelative(face);
            if (isNaturalLeaf(leaf) && candidates.add(leaf)) pending.add(leaf);
        }
        while (!pending.isEmpty() && candidates.size() < 8192) {
            Block leaf = pending.removeFirst();
            for (BlockFace face : faces) {
                Block next = leaf.getRelative(face);
                if (isNaturalLeaf(next) && candidates.add(next)) pending.addLast(next);
            }
        }
        // A connected leaf component can touch two nearby trees. Keep a leaf only when its
        // vanilla distance gradient can actually lead back to one of this tree's logs.
        return candidates.stream().filter(leaf -> belongsToTree(leaf, treeLogs, faces)).toList();
    }
    private static boolean belongsToTree(Block start, java.util.Set<Block> treeLogs, BlockFace[] faces) {
        java.util.ArrayDeque<Block> pending = new java.util.ArrayDeque<>();
        java.util.HashSet<Block> seen = new java.util.HashSet<>(); pending.add(start); seen.add(start);
        while (!pending.isEmpty()) {
            Block leaf = pending.removeFirst();
            Leaves data = (Leaves) leaf.getBlockData();
            int distance = data.getDistance();
            if (distance <= 1) for (BlockFace face : faces) if (treeLogs.contains(leaf.getRelative(face))) return true;
            if (distance <= 1) continue;
            for (BlockFace face : faces) {
                Block next = leaf.getRelative(face);
                if (next.getBlockData() instanceof Leaves nextData && !nextData.isPersistent()
                        && nextData.getDistance() == distance - 1 && seen.add(next)) pending.addLast(next);
            }
        }
        return false;
    }
    private record TreeBreakProgress(int completedBreaks, long lastBreakAt) { }
    private void saveDrops(String path, List<MobDrop> drops) {
        List<Map<String, Object>> serialised = drops.stream().map(drop -> Map.<String, Object>of(
                "item", drop.material().getKey().toString(), "minimum", drop.minimum(),
                "maximum", drop.maximum(), "chance", drop.chance())).toList();
        getConfig().set(path, serialised);
    }

    /** Counts active differences from the vanilla/inherited values, not every past click. */
    int mobEditScore(EntityType mob) {
        int configuredParts = 0;
        String cookedPath = mobPath(mob, "cooked-food");
        String xpPath = mobPath(mob, "xp-bonus");
        if (getConfig().contains(cookedPath) && getConfig().getBoolean(cookedPath) != defaultMobCookedFood(mob)) configuredParts++;
        if (getConfig().contains(xpPath) && getConfig().getInt(xpPath) != defaultMobXpBonus(mob)) configuredParts++;
        configuredParts += getMobDrops(mob).size() + getVanillaDropOverrides(mob).size();
        return configuredParts;
    }
    long mobLastEdited(EntityType mob) { return getConfig().getLong(mobPath(mob, "last-edited"), 0L); }
    private void touchMob(EntityType mob) {
        String basePath = "mobs." + mob.name();
        getConfig().set(basePath + ".last-edited", System.currentTimeMillis());
        // Remove the old click-history field: it was misleading after a reset.
        getConfig().set(basePath + ".edit-count", null);
        saveConfig();
    }
    private boolean defaultMobCookedFood(EntityType mob) {
        return isAnimal(mob) && getConfig().getBoolean("animal-drops.cook", true);
    }
    private int defaultMobXpBonus(EntityType mob) {
        return isAnimal(mob) ? getBoundedInt("animal-drops.xp-bonus", 0, 0, 50) : 0;
    }

    private MobDrop getVanillaDropOverride(EntityType mob, Material material) {
        return getVanillaDropOverrides(mob).stream().filter(drop -> drop.material() == material).findFirst().orElse(null);
    }

    private Material getLegacyPrimaryFoodOverride(EntityType animal, Material currentDrop) {
        if (!isPrimaryFood(animal, currentDrop)) return null;
        String configured = getConfig().getString("animal-drops.item-overrides." + animal.name());
        return configured == null ? null : MenuItems.findItem(configured);
    }
    private static boolean isPrimaryFood(EntityType animal, Material drop) {
        return switch (animal) {
            case COW, MOOSHROOM -> drop == Material.BEEF || drop == Material.COOKED_BEEF;
            case CHICKEN -> drop == Material.CHICKEN || drop == Material.COOKED_CHICKEN;
            case PIG -> drop == Material.PORKCHOP || drop == Material.COOKED_PORKCHOP;
            case SHEEP -> drop == Material.MUTTON || drop == Material.COOKED_MUTTON;
            case RABBIT -> drop == Material.RABBIT || drop == Material.COOKED_RABBIT;
            case COD -> drop == Material.COD || drop == Material.COOKED_COD;
            case SALMON -> drop == Material.SALMON || drop == Material.COOKED_SALMON;
            default -> false;
        };
    }
    private void collectForKillerInRange(EntityDeathEvent event, int range) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || range == 0 || !killer.getWorld().equals(event.getEntity().getWorld())
                || killer.getLocation().distanceSquared(event.getEntity().getLocation()) > (double) range * range) return;
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack drop : event.getDrops()) remaining.addAll(killer.getInventory().addItem(drop).values());
        event.getDrops().clear(); event.getDrops().addAll(remaining);
    }
    private static boolean isAnimal(EntityType type) { return switch (type) {
        case COW, MOOSHROOM, CHICKEN, PIG, SHEEP, RABBIT, COD, SALMON -> true; default -> false; }; }
    private static void addStacks(List<ItemStack> output, ItemStack item, int multiplier) {
        for (int remaining = item.getAmount() * multiplier; remaining > 0;) {
            ItemStack stack = item.clone(); int amount = Math.min(remaining, item.getMaxStackSize());
            stack.setAmount(amount); output.add(stack); remaining -= amount;
        }
    }
    private static int number(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
    private static double decimal(Object value, double fallback) { return value instanceof Number n ? n.doubleValue() : fallback; }
    int getBoundedInt(String path, int fallback, int minimum, int maximum) { return Math.clamp(getConfig().getInt(path, fallback), minimum, maximum); }
    void setConfigValue(String path, Object value) { getConfig().set(path, value); saveConfig(); }
    private static String mobPath(EntityType mob, String property) { return "mobs." + mob.name() + "." + property; }
    private static String orePath(Material ore, String property) { return "ore-drops." + ore.getKey().getKey() + "." + property; }
    record MobDrop(Material material, int minimum, int maximum, double chance) { }
}
