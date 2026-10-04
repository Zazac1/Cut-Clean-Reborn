package fr.isaac.cutcleanreborn.paper;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** Persistent configuration and exactly-once runtime application of automatic enchantments. */
final class AutoEnchantService implements Listener {
    private static final String ROOT = "automatic-enchantments";
    private final CutCleanRebornPlugin plugin;
    private final NamespacedKey appliedIdKey;
    private final NamespacedKey enchantmentStateKey;
    private final NamespacedKey refreshVersionKey;
    private final Map<Material, Map<Enchantment, Integer>> configured = new EnumMap<>(Material.class);

    AutoEnchantService(CutCleanRebornPlugin plugin) {
        this.plugin = plugin;
        this.appliedIdKey = new NamespacedKey(plugin, "auto_enchant_id");
        this.enchantmentStateKey = new NamespacedKey(plugin, "auto_enchant_state");
        this.refreshVersionKey = new NamespacedKey(plugin, "auto_enchant_refresh_version");
        reloadCache();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Covers commands, crafting, chests and trades without scanning every server tick.
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> plugin.getServer().getOnlinePlayers()
                .forEach(this::applyToInventory), 20L, 20L);
    }

    List<Material> configuredItems(AutoEnchantCategory category) {
        return configured.keySet().stream().filter(material -> category.matches(material)).sorted()
                .toList();
    }
    Map<Enchantment, Integer> enchantments(Material material) {
        return Map.copyOf(configured.getOrDefault(material, Map.of()));
    }
    boolean isConfigured(Material material) { return configured.containsKey(material); }
    boolean enabled() { return plugin.getConfig().getBoolean(ROOT + ".enabled", true); }
    void setEnabled(boolean enabled) { plugin.setConfigValue(ROOT + ".enabled", enabled); }
    void requestRefresh() {
        plugin.setConfigValue(ROOT + ".refresh-version", refreshVersion() + 1);
        plugin.getServer().getOnlinePlayers().forEach(this::applyToInventory);
    }

    void addItem(AutoEnchantCategory category, Material material) {
        if (!category.matches(material)) return;
        configured.putIfAbsent(material, new LinkedHashMap<>());
        saveMaterial(category, material);
    }
    void removeItem(AutoEnchantCategory category, Material material) {
        plugin.setConfigValue(path(category, material), null);
        configured.remove(material);
    }
    void setEnchantment(AutoEnchantCategory category, Material material, Enchantment enchantment, int level) {
        if (!category.matches(material) || !enchantment.canEnchantItem(new ItemStack(material)) || level <= 0) return;
        configured.computeIfAbsent(material, ignored -> new LinkedHashMap<>()).put(enchantment, level);
        saveMaterial(category, material);
    }
    void removeEnchantment(AutoEnchantCategory category, Material material, Enchantment enchantment) {
        Map<Enchantment, Integer> values = configured.get(material);
        if (values == null) return;
        values.remove(enchantment);
        saveMaterial(category, material);
    }
    void copySameMaterial(AutoEnchantCategory category, Material source) {
        Map<Enchantment, Integer> sourceEnchants = configured.get(source);
        if (sourceEnchants == null || sourceEnchants.isEmpty()) return;
        String prefix = category.materialPrefix(source);
        for (Material target : Material.values()) {
            if (target == source || !category.matches(target) || !category.materialPrefix(target).equals(prefix)) continue;
            Map<Enchantment, Integer> targetEnchants = configured.get(target);
            boolean changed = false;
            for (Map.Entry<Enchantment, Integer> entry : sourceEnchants.entrySet()) {
                if (!entry.getKey().canEnchantItem(new ItemStack(target))) continue;
                if (targetEnchants == null) targetEnchants = new LinkedHashMap<>();
                changed |= targetEnchants.putIfAbsent(entry.getKey(), entry.getValue()) == null;
            }
            if (changed) {
                configured.put(target, targetEnchants);
                saveMaterial(category, target);
            }
        }
    }

    @EventHandler(ignoreCancelled = true) public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player)
            plugin.getServer().getScheduler().runTask(plugin, () -> applyToInventory(player));
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> applyToInventory(event.getPlayer()));
    }

    private void applyToInventory(Player player) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stack != null && apply(stack)) player.getInventory().setItem(slot, stack);
        }
    }
    /** @return true only if the existing stack received a CCR mutation. */
    boolean apply(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        Map<Enchantment, Integer> enchants = configured.get(stack.getType());
        if (enchants == null || !enabled()) return false;
        ItemMeta meta = stack.getItemMeta();
        var data = meta.getPersistentDataContainer();
        if (data.has(appliedIdKey, PersistentDataType.STRING)) {
            Integer itemVersion = data.get(refreshVersionKey, PersistentDataType.INTEGER);
            String expectedState = data.get(enchantmentStateKey, PersistentDataType.STRING);
            // A player-enchanted item no longer matches the exact CCR snapshot and is deliberately left alone.
            if (itemVersion == null || itemVersion >= refreshVersion() || expectedState == null || !expectedState.equals(enchantmentState(meta))) return false;
            applyEnchantments(meta, enchants);
            data.set(enchantmentStateKey, PersistentDataType.STRING, enchantmentState(meta));
            data.set(refreshVersionKey, PersistentDataType.INTEGER, refreshVersion());
            stack.setItemMeta(meta);
            return true;
        }
        applyEnchantments(meta, enchants);
        // One UUID per processed ItemStack: the marker survives inventories, chests, exchanges and restarts.
        data.set(appliedIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        data.set(enchantmentStateKey, PersistentDataType.STRING, enchantmentState(meta));
        data.set(refreshVersionKey, PersistentDataType.INTEGER, refreshVersion());
        stack.setItemMeta(meta);
        return true;
    }

    private static void applyEnchantments(ItemMeta meta, Map<Enchantment, Integer> enchants) {
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) meta.addEnchant(entry.getKey(), entry.getValue(), true);
    }
    private int refreshVersion() { return Math.max(0, plugin.getConfig().getInt(ROOT + ".refresh-version", 0)); }
    private static String enchantmentState(ItemMeta meta) {
        return meta.getEnchants().entrySet().stream().sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(enchantment -> enchantment.getKey().toString())))
                .map(entry -> entry.getKey().getKey() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining(";"));
    }

    private void reloadCache() {
        configured.clear();
        for (AutoEnchantCategory category : AutoEnchantCategory.values()) {
            ConfigurationSection items = plugin.getConfig().getConfigurationSection(ROOT + "." + category.configKey());
            if (items == null) continue;
            for (String itemName : items.getKeys(false)) {
                Material material = MenuItems.findItem(itemName);
                if (material == null || !category.matches(material)) continue;
                ConfigurationSection enchantments = items.getConfigurationSection(itemName);
                if (enchantments == null) continue;
                Map<Enchantment, Integer> result = new LinkedHashMap<>();
                for (String key : enchantments.getKeys(false)) {
                    Enchantment enchantment = findEnchantment(key);
                    int level = enchantments.getInt(key);
                    if (enchantment != null && level > 0 && enchantment.canEnchantItem(new ItemStack(material))) result.put(enchantment, level);
                }
                configured.put(material, result);
            }
        }
    }
    private void saveMaterial(AutoEnchantCategory category, Material material) {
        Map<String, Integer> stored = new LinkedHashMap<>();
        for (Map.Entry<Enchantment, Integer> entry : configured.getOrDefault(material, Map.of()).entrySet())
            stored.put(entry.getKey().getKey().toString(), entry.getValue());
        plugin.setConfigValue(path(category, material), stored);
    }
    private static String path(AutoEnchantCategory category, Material material) {
        return ROOT + "." + category.configKey() + "." + material.name();
    }
    static Enchantment findEnchantment(String text) {
        try {
            String value = text.trim().toLowerCase();
            NamespacedKey key = value.contains(":") ? NamespacedKey.fromString(value) : NamespacedKey.minecraft(value);
            return key == null ? null : Registry.ENCHANTMENT.get(key);
        } catch (IllegalArgumentException ignored) { return null; }
    }
    record EnchantmentInput(Enchantment enchantment, int level) { }
    static EnchantmentInput parseInput(String text, Material material) {
        String[] fields = text.trim().split("\\s+");
        if (fields.length != 2) return null;
        try {
            Enchantment enchantment = findEnchantment(fields[0]);
            int level = Integer.parseInt(fields[1]);
            return enchantment != null && level > 0 && enchantment.canEnchantItem(new ItemStack(material))
                    ? new EnchantmentInput(enchantment, level) : null;
        } catch (NumberFormatException ignored) { return null; }
    }
}

enum AutoEnchantCategory {
    ARMOR("armor", "Armor", Material.DIAMOND_CHESTPLATE, new String[] {"_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS"}),
    TOOLS("tools", "Tools", Material.DIAMOND_SWORD, new String[] {"_SWORD", "_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "_SHEARS", "_FISHING_ROD", "_TRIDENT", "_BOW", "_CROSSBOW", "_MACE", "_BRUSH", "_FLINT_AND_STEEL"});
    private final String key, title; private final Material icon; private final String[] suffixes;
    AutoEnchantCategory(String key, String title, Material icon, String[] suffixes) { this.key = key; this.title = title; this.icon = icon; this.suffixes = suffixes; }
    String configKey() { return key; } String title() { return title; } Material icon() { return icon; }
    boolean matches(Material material) { for (String suffix : suffixes) if (material.name().endsWith(suffix)) return material.isItem(); return false; }
    String materialPrefix(Material material) { for (String suffix : suffixes) if (material.name().endsWith(suffix)) return material.name().substring(0, material.name().length() - suffix.length()); return material.name(); }
    Comparator<Material> comparator() {
        return Comparator.comparingInt((Material material) -> powerRank(materialPrefix(material)))
                .thenComparingInt(this::typeRank).thenComparing(Material::name);
    }
    private int typeRank(Material material) {
        String name = material.name();
        for (int index = 0; index < suffixes.length; index++) if (name.endsWith(suffixes[index])) return index;
        return suffixes.length;
    }
    private int powerRank(String prefix) {
        return switch (prefix) {
            case "WOODEN", "LEATHER" -> 0;
            case "STONE", "CHAINMAIL" -> 1;
            case "IRON" -> 2;
            case "GOLDEN" -> 3;
            case "DIAMOND" -> 4;
            case "NETHERITE" -> 5;
            default -> 6;
        };
    }
}
