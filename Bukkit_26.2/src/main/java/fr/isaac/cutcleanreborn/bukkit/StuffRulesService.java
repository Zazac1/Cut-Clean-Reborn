package fr.isaac.cutcleanreborn.bukkit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Applies equipment rules only when an item reaches a player; it never scans containers. */
final class StuffRulesService implements Listener {
    private static final String ROOT = "game-rules.stuff-rules";
    private static final org.bukkit.inventory.EquipmentSlot[] ARMOR_SLOTS = {org.bukkit.inventory.EquipmentSlot.HEAD, org.bukkit.inventory.EquipmentSlot.CHEST, org.bukkit.inventory.EquipmentSlot.LEGS, org.bukkit.inventory.EquipmentSlot.FEET};
    private final CutCleanRebornPlugin plugin;
    private final NamespacedKey maceId;
    private List<ArmorRule> cachedArmor = List.of();
    private List<EnchantmentCap> cachedCaps = List.of();
    private final Set<UUID> scheduled = new java.util.HashSet<>();
    private static final Map<String, Set<Material>> ARMOR_TARGETS = new java.util.HashMap<>();
    private static final Map<String, Set<Material>> ITEM_TARGETS = new java.util.HashMap<>();

    StuffRulesService(CutCleanRebornPlugin plugin) { this.plugin = plugin; maceId = new NamespacedKey(plugin, "unique_mace_id"); reloadCache(); plugin.getServer().getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::correctOnline, 20L, 20L);
    }
    void reloadCache() { cachedArmor = List.copyOf(readArmorRules()); cachedCaps = List.copyOf(readCaps()); }
    void check(Player player) { schedule(player); }
    boolean enabled() { return plugin.getConfig().getBoolean(ROOT + ".enabled", false); }
    void setEnabled(boolean value) { plugin.setConfigValue(ROOT + ".enabled", value); if (value) correctOnline(); }
    boolean maceEnabled() { return plugin.getConfig().getBoolean(ROOT + ".unique-mace.enabled", false); }
    void setMaceEnabled(boolean value) { plugin.setConfigValue(ROOT + ".unique-mace.enabled", value); }
    boolean maceExists() { return plugin.getConfig().getBoolean(ROOT + ".unique-mace.exists", false); }
    void clearMace() { plugin.getConfig().set(ROOT + ".unique-mace.exists", false); plugin.getConfig().set(ROOT + ".unique-mace.id", null); plugin.saveConfig(); }

    List<ArmorRule> armorRules() { return cachedArmor; }
    List<EnchantmentCap> enchantmentCaps() { return cachedCaps; }
    ArmorRule addArmorRule() { ArmorRule result = new ArmorRule(UUID.randomUUID().toString(), false, "DIAMOND", 4); List<ArmorRule> rules = readArmorRules(); rules.add(result); saveArmorRules(rules); return result; }
    EnchantmentCap addEnchantmentCap() { EnchantmentCap result = new EnchantmentCap(UUID.randomUUID().toString(), false, "SWORDS", "minecraft:sharpness", 3, false, 0); List<EnchantmentCap> rules = readCaps(); rules.add(result); saveCaps(rules); return result; }
    ArmorRule copyArmorRule(ArmorRule source, String target) { ArmorRule result = ArmorRule.create(null, false, target, source.maximum()); if (result == null) return null; List<ArmorRule> rules = readArmorRules(); rules.add(result); saveArmorRules(rules); return result; }
    EnchantmentCap copyCap(EnchantmentCap source, String target) { EnchantmentCap result = EnchantmentCap.create(null, false, target, source.enchantment(), source.maximum(), source.refundXp(), source.xpPerRemovedLevel()); if (result == null) return null; List<EnchantmentCap> rules = readCaps(); rules.add(result); saveCaps(rules); return result; }
    void saveArmorRule(ArmorRule rule) { List<ArmorRule> rules = readArmorRules(); replace(rules, rule, ArmorRule::id); saveArmorRules(rules); correctOnline(); }
    void removeArmorRule(String id) { List<ArmorRule> rules = readArmorRules(); rules.removeIf(rule -> rule.id().equals(id)); saveArmorRules(rules); }
    void saveCap(EnchantmentCap cap) { List<EnchantmentCap> rules = readCaps(); replace(rules, cap, EnchantmentCap::id); saveCaps(rules); correctOnline(); }
    void removeCap(String id) { List<EnchantmentCap> rules = readCaps(); rules.removeIf(rule -> rule.id().equals(id)); saveCaps(rules); }
    private static <T> void replace(List<T> values, T value, java.util.function.Function<T, String> id) { for (int index = 0; index < values.size(); index++) if (id.apply(values.get(index)).equals(id.apply(value))) { values.set(index, value); return; } values.add(value); }

    @EventHandler public void join(PlayerJoinEvent event) { schedule(event.getPlayer()); }
    @EventHandler(ignoreCancelled = true) public void inventory(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || MenuSupport.isPluginView(event.getView()) || event.getAction() == InventoryAction.NOTHING) return;
        if (event.getInventory() instanceof AnvilInventory && event.getRawSlot() == 2) {
            int cost = event.getView() instanceof org.bukkit.inventory.view.AnvilView view ? view.getRepairCost() : 0;
            int before = player.getLevel();
            ItemStack result = event.getCurrentItem();
            CapResult removed = cap(result);
            // The result is changed before Paper transfers it, so shift-click cannot bypass this cap.
            if (!removed.isEmpty()) event.setCurrentItem(result);
            if (!removed.isEmpty()) Bukkit.getScheduler().runTask(plugin, () -> {
                if (!event.isCancelled() && player.isOnline() && player.getGameMode() != GameMode.CREATIVE) {
                    int paid = Math.min(cost, Math.max(0, before - player.getLevel()));
                    if (paid > 0) warnCap(player, removed, refund(player, result, removed, paid));
                }
            });
        }
        schedule(player);
    }
    @EventHandler(ignoreCancelled = true) public void interact(PlayerInteractEvent event) { schedule(event.getPlayer()); }
    @EventHandler(ignoreCancelled = true) public void pickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player player) schedule(player); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void craft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!enabled() || !maceEnabled() || event.getRecipe().getResult().getType() != Material.MACE) { schedule(player); return; }
        if (maceExists()) {
            event.setCancelled(true);
            player.sendMessage(CutCleanCommands.PREFIX + "§cThe unique mace already exists.");
            return;
        }
        if (event.isShiftClick() || (event.getAction() != InventoryAction.PICKUP_ALL && event.getAction() != InventoryAction.PICKUP_HALF)) {
            event.setCancelled(true);
            player.sendMessage(CutCleanCommands.PREFIX + "Craft the unique mace with a normal click and an empty cursor.");
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() != Material.MACE) return;
        String identity = UUID.randomUUID().toString();
        result = result.clone();
        org.bukkit.inventory.meta.ItemMeta maceMeta = result.getItemMeta();
        maceMeta.getPersistentDataContainer().set(maceId, PersistentDataType.STRING, identity);
        result.setItemMeta(maceMeta);
        event.setCurrentItem(result);
        // Reserve synchronously so two players cannot both claim it in the same server tick.
        plugin.setConfigValues(Map.of(ROOT + ".unique-mace.exists", true, ROOT + ".unique-mace.id", identity));
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean received = identity.equals(identity(player.getItemOnCursor()));
            for (ItemStack item : player.getInventory().getContents()) received |= identity.equals(identity(item));
            if ((event.isCancelled() || !received) && identity.equals(plugin.getConfig().getString(ROOT + ".unique-mace.id"))) clearMace();
            else if (received) player.sendMessage(CutCleanCommands.PREFIX + "§eYou crafted the unique mace!");
            schedule(player);
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && !MenuSupport.isPluginView(event.getView())) schedule(player);
    }

    @EventHandler(ignoreCancelled = true) public void dispense(BlockDispenseArmorEvent event) { if (event.getTargetEntity() instanceof Player player) schedule(player); }
    @EventHandler(ignoreCancelled = true) public void enchant(EnchantItemEvent event) {
        if (!enabled()) return;
        int before = event.getEnchanter().getLevel();
        Map<Enchantment, Integer> removed = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : event.getEnchantsToAdd().entrySet()) {
            int maximum = matchingMaximum(event.getItem().getType(), entry.getKey());
            if (maximum >= 0 && entry.getValue() > maximum) { removed.put(entry.getKey(), entry.getValue() - maximum); entry.setValue(maximum); }
        }
        CapResult result = new CapResult(removed);
        if (!result.isEmpty()) Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = event.getEnchanter();
            if (event.isCancelled() || !player.isOnline() || player.getGameMode() == GameMode.CREATIVE) return;
            int paid = Math.min(event.getExpLevelCost(), Math.max(0, before - player.getLevel()));
            if (paid > 0) warnCap(player, result, refund(player, event.getItem(), result, paid));
        });
        schedule(event.getEnchanter());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void removed(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Item item)) return;
        switch (event.getCause()) {
            case DEATH, DESPAWN, EXPLODE, HIT, OUT_OF_WORLD -> releaseMace(item.getItemStack());
            default -> { } // Pickup, merging and chunk unloads never release the unique mace.
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void broken(PlayerItemBreakEvent event) { releaseMace(event.getBrokenItem()); }

    private String identity(ItemStack stack) {
        return stack == null || !stack.hasItemMeta() ? null : stack.getItemMeta().getPersistentDataContainer().get(maceId, PersistentDataType.STRING);
    }

    private void releaseMace(ItemStack stack) {
        String identity = identity(stack);
        String active = plugin.getConfig().getString(ROOT + ".unique-mace.id");
        if (!maceExists() || identity == null || (active != null && !active.equals(identity))) return;
        clearMace();
        plugin.getServer().broadcastMessage(MenuItems.component("§6CCR §8» §eThe unique mace was destroyed. A new one can now be crafted."));
    }

    private void schedule(Player player) {
        if (!enabled() || !player.isOnline() || !scheduled.add(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            scheduled.remove(player.getUniqueId());
            if (player.isOnline()) correct(player);
        });
    }
    private void correctOnline() { Bukkit.getOnlinePlayers().forEach(this::schedule); }
    private void correct(Player player) { if (!enabled()) return; ItemStack[] contents = player.getInventory().getContents(); CapResult corrections = CapResult.EMPTY; for (ItemStack stack : contents) if (stack != null) corrections = corrections.plus(cap(stack)); if (!corrections.isEmpty()) { player.getInventory().setContents(contents); warnCap(player, corrections, 0); } enforceArmor(player); }
    /** Records every affected enchantment so XP can only be tied to that exact cap. */
    private CapResult cap(ItemStack stack) { if (!enabled() || stack == null || stack.getType().isAir()) return CapResult.EMPTY; Map<Enchantment, Integer> removed = new LinkedHashMap<>(); for (Map.Entry<Enchantment, Integer> entry : new ArrayList<>(stack.getEnchantments().entrySet())) { int maximum = matchingMaximum(stack.getType(), entry.getKey()); if (maximum >= 0 && entry.getValue() > maximum) { removed.put(entry.getKey(), entry.getValue() - maximum); stack.addUnsafeEnchantment(entry.getKey(), maximum); } } return removed.isEmpty() ? CapResult.EMPTY : new CapResult(removed); }
    int matchingMaximum(Material item, Enchantment enchantment) { int maximum = Integer.MAX_VALUE; for (EnchantmentCap cap : cachedCaps) if (cap.enabled() && cap.matches(item, enchantment)) maximum = Math.min(maximum, cap.maximum()); return maximum == Integer.MAX_VALUE ? -1 : maximum; }
    private int refund(Player player, ItemStack item, CapResult removed, int paidCost) { if (paidCost <= 0 || removed.isEmpty()) return 0; long requested = 0; for (Map.Entry<Enchantment, Integer> entry : removed.levels().entrySet()) { int maximum = matchingMaximum(item.getType(), entry.getKey()); int perLevel = 0; for (EnchantmentCap cap : readCaps()) if (cap.enabled() && cap.refundXp() && cap.maximum() == maximum && cap.matches(item.getType(), entry.getKey())) perLevel = Math.max(perLevel, cap.xpPerRemovedLevel()); requested += (long) entry.getValue() * perLevel; } int refund = (int) Math.min(paidCost, Math.min(requested, Integer.MAX_VALUE)); if (refund > 0) player.giveExpLevels(refund); return refund; }
    private void warnCap(Player player, CapResult removed, int refundedLevels) { if (removed.isEmpty()) return; String changes = removed.levels().entrySet().stream().limit(3).map(entry -> MenuItems.prettyName(entry.getKey()) + " §7(-" + entry.getValue() + ")").collect(java.util.stream.Collectors.joining("§7, §f")); if (removed.levels().size() > 3) changes += "§7, §f+" + (removed.levels().size() - 3); player.sendMessage("§6CCR §8» §eEnchantment reduced: §f" + changes); if (refundedLevels > 0) player.sendMessage("§6CCR §8» §aRefund: §e+" + refundedLevels + " XP level(s)§a."); }
    private void enforceArmor(Player player) { for (ArmorRule rule : cachedArmor) { if (!rule.enabled()) continue; int kept = 0; for (org.bukkit.inventory.EquipmentSlot slot : ARMOR_SLOTS) { ItemStack piece = player.getInventory().getItem(slot); if (piece == null || piece.getType().isAir() || !rule.matches(piece.getType())) continue; if (++kept <= rule.maximum()) continue; player.getInventory().setItem(slot, null); BukkitCompat.actionBar(player, MenuItems.component("§eArmor limit • " + rule.target() + " • maximum " + rule.maximum())); Map<Integer, ItemStack> overflow = player.getInventory().addItem(piece); overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item)); } } }

    private List<ArmorRule> readArmorRules() { List<ArmorRule> result = new ArrayList<>(); for (Map<?, ?> map : plugin.getConfig().getMapList(ROOT + ".armor-rules")) { String target = text(map.get("target")); if (target.isBlank()) target = text(map.get("material")); ArmorRule rule = ArmorRule.create(text(map.get("id")), bool(map.get("enabled")), target, number(map.get("maximum"), 4)); if (rule != null) result.add(rule); } return result; }
    private List<EnchantmentCap> readCaps() { List<EnchantmentCap> result = new ArrayList<>(); for (Map<?, ?> map : plugin.getConfig().getMapList(ROOT + ".enchantment-caps")) { String target = text(map.get("target")); if (target.isBlank()) target = text(map.get("item")); EnchantmentCap cap = EnchantmentCap.create(text(map.get("id")), bool(map.get("enabled")), target, text(map.get("enchantment")), number(map.get("maximum"), 1), bool(map.get("refund-xp")), number(map.get("xp-per-removed-level"), 0)); if (cap != null) result.add(cap); } return result; }
    private void saveArmorRules(List<ArmorRule> rules) { cachedArmor = List.copyOf(rules); plugin.setConfigValue(ROOT + ".armor-rules", rules.stream().map(ArmorRule::serialize).toList()); }
    private void saveCaps(List<EnchantmentCap> caps) { cachedCaps = List.copyOf(caps); plugin.setConfigValue(ROOT + ".enchantment-caps", caps.stream().map(EnchantmentCap::serialize).toList()); }
    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private static boolean bool(Object value) { return value instanceof Boolean b && b; }
    private static int number(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
    private record CapResult(Map<Enchantment, Integer> levels) { static final CapResult EMPTY = new CapResult(Map.of()); boolean isEmpty() { return levels.isEmpty(); } CapResult plus(CapResult other) { if (other.isEmpty()) return this; if (isEmpty()) return other; Map<Enchantment, Integer> combined = new LinkedHashMap<>(levels); other.levels.forEach((enchantment, level) -> combined.merge(enchantment, level, Integer::sum)); return new CapResult(combined); } }

    record ArmorRule(String id, boolean enabled, String target, int maximum) {
        static ArmorRule create(String id, boolean enabled, String target, int maximum) { String normalized = normalizeArmorTarget(target); return normalized == null ? null : new ArmorRule(id == null || id.isBlank() ? UUID.randomUUID().toString() : id, enabled, normalized, Math.clamp(maximum, 0, 4)); }
        boolean matches(Material material) { return ARMOR_TARGETS.computeIfAbsent(target, StuffRulesService::armorMaterials).contains(material); }
        Map<String, Object> serialize() { Map<String, Object> map = new LinkedHashMap<>(); map.put("id", id); map.put("enabled", enabled); map.put("target", target); map.put("maximum", maximum); return map; }
    }
    record EnchantmentCap(String id, boolean enabled, String target, String enchantment, int maximum, boolean refundXp, int xpPerRemovedLevel) {
        static EnchantmentCap create(String id, boolean enabled, String target, String enchantment, int maximum, boolean refundXp, int xpPerRemovedLevel) { String normalizedTarget = normalizeItemTarget(target); Enchantment found = AutoEnchantService.findEnchantment(enchantment); return normalizedTarget == null || found == null || maximum < 1 ? null : new EnchantmentCap(id == null || id.isBlank() ? UUID.randomUUID().toString() : id, enabled, normalizedTarget, found.getKey().toString(), maximum, refundXp, Math.max(0, xpPerRemovedLevel)); }
        boolean matches(Material material, Enchantment value) { return enchantment.equals(value.getKey().toString()) && matchesAny(material); }
        boolean matchesAny(Material material) { return ITEM_TARGETS.computeIfAbsent(target, StuffRulesService::itemMaterials).contains(material); }
        Map<String, Object> serialize() { Map<String, Object> map = new LinkedHashMap<>(); map.put("id", id); map.put("enabled", enabled); map.put("target", target); map.put("enchantment", enchantment); map.put("maximum", maximum); map.put("refund-xp", refundXp); map.put("xp-per-removed-level", xpPerRemovedLevel); return map; }
    }
    static String normalizeArmorTarget(String input) { if (input == null || input.isBlank()) return null; String target = input.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""); if (target.equals("ARMOR")) return target; if (!target.contains(",") && !armorMaterials(target).isEmpty()) return target; Set<Material> materials = parseMaterials(target, true); return materials.isEmpty() ? null : materials.stream().map(m -> m.getKey().toString()).sorted().collect(java.util.stream.Collectors.joining(",")); }
    static String normalizeItemTarget(String input) { if (input == null || input.isBlank()) return null; String target = input.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""); if (Set.of("SWORDS", "TOOLS", "ARMOR", "ALL").contains(target)) return target; Set<Material> materials = parseMaterials(target, false); return materials.isEmpty() ? null : materials.stream().map(m -> m.getKey().toString()).sorted().collect(java.util.stream.Collectors.joining(",")); }
    static String normalizeArmorMaterialType(String input) { if (input == null || input.isBlank()) return null; String target = input.trim().toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""); Material direct = MenuItems.findItem(target); return target.equals("ARMOR") || (!target.contains(",") && (direct == null || !isArmor(direct)) && !armorMaterials(target).isEmpty()) ? target : null; }
    static String normalizeArmorItems(String input) { return normalizeExactItems(input, true); }
    static String normalizeItemCategory(String input) { if (input == null) return null; String target = input.trim().toUpperCase(Locale.ROOT); return Set.of("SWORDS", "TOOLS", "ARMOR", "ALL").contains(target) ? target : null; }
    static String normalizeItemItems(String input) { return normalizeExactItems(input, false); }
    private static String normalizeExactItems(String input, boolean armorOnly) { if (input == null || input.isBlank()) return null; Set<Material> materials = parseMaterials(input.trim(), armorOnly); return materials.isEmpty() ? null : materials.stream().map(m -> m.getKey().toString()).sorted().collect(java.util.stream.Collectors.joining(",")); }
    private static Set<Material> armorMaterials(String target) { if (target.equals("ARMOR")) return java.util.Arrays.stream(Material.values()).filter(StuffRulesService::isArmor).collect(java.util.stream.Collectors.toSet()); Material direct = MenuItems.findItem(target); if (direct != null && isArmor(direct)) return Set.of(direct); if (!target.contains(",")) return java.util.Arrays.stream(Material.values()).filter(m -> isArmor(m) && m.name().startsWith(target + "_")).collect(java.util.stream.Collectors.toSet()); return parseMaterials(target, true); }
    private static Set<Material> itemMaterials(String target) { return switch (target) { case "ALL" -> java.util.Arrays.stream(Material.values()).filter(Material::isItem).collect(java.util.stream.Collectors.toSet()); case "SWORDS" -> java.util.Arrays.stream(Material.values()).filter(m -> m.isItem() && m.name().endsWith("_SWORD")).collect(java.util.stream.Collectors.toSet()); case "TOOLS" -> java.util.Arrays.stream(Material.values()).filter(m -> m.isItem() && (m.name().endsWith("_SWORD") || m.name().endsWith("_PICKAXE") || m.name().endsWith("_AXE") || m.name().endsWith("_SHOVEL") || m.name().endsWith("_HOE") || m == Material.MACE || m == Material.BOW || m == Material.CROSSBOW || m == Material.TRIDENT)).collect(java.util.stream.Collectors.toSet()); case "ARMOR" -> armorMaterials("ARMOR"); default -> parseMaterials(target, false); }; }
    private static Set<Material> parseMaterials(String target, boolean armorOnly) { Set<Material> result = new java.util.HashSet<>(); for (String part : target.split(",")) { Material material = MenuItems.findItem(part.trim()); if (material == null || (armorOnly && !isArmor(material))) return Set.of(); result.add(material); } return result; }
    private static boolean isArmor(Material material) { String name = material.name(); return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS"); }
}

