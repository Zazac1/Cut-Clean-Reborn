package fr.isaac.cutcleanreborn.bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

/** Lifecycle and backwards-compatible configuration facade for portable Bukkit implementations. */
public final class CutCleanRebornPlugin extends JavaPlugin {
    private AutoEnchantService autoEnchantService;
    private CleanStackService cleanStacks;
    private BetterSugarCaneService betterSugarCane;
    private StuffRulesService stuffRules;
    private TreeCutterService timber;

    @Override public void onEnable() {
        saveDefaultConfig();
        BukkitCompat.initialize(this);
        stuffRules = new StuffRulesService(this);
        cleanStacks = new CleanStackService(this);
        autoEnchantService = new AutoEnchantService(this);
        betterSugarCane = new BetterSugarCaneService(this);
        timber = new TreeCutterService(this);
        getServer().getPluginManager().registerEvents(new MobDropService(this), this);
        getServer().getPluginManager().registerEvents(new OreDropService(this), this);
        CutCleanCommands commands = new CutCleanCommands(this);
        getCommand("ccr").setExecutor(commands);
        getCommand("ccr").setTabCompleter(commands);
        getCommand("cleanstacks").setExecutor(commands);
        getCommand("cleanstacks").setTabCompleter(commands);
        getLogger().info("CutCleanReborn ready. Use /ccr to open the configuration.");
    }

    @Override public void onDisable() {
        getServer().getOnlinePlayers().forEach(player -> {
            if (MenuSupport.isPluginView(player.getOpenInventory())) BukkitCompat.closeInventory(player);
        });
        VanillaDropCatalog.clear();
    }

    AutoEnchantService autoEnchantments() { return autoEnchantService; }
    CleanStackService cleanStacks() { return cleanStacks; }
    BetterSugarCaneService betterSugarCane() { return betterSugarCane; }
    StuffRulesService stuffRules() { return stuffRules; }

    void refreshServices() {
        stuffRules.reloadCache();
        autoEnchantService.reloadCache();
        timber.clearProgress();
        VanillaDropCatalog.clear();
        getServer().getOnlinePlayers().forEach(player -> {
            if (MenuSupport.isPluginView(player.getOpenInventory())) BukkitCompat.closeInventory(player);
            autoEnchantService.applyToInventory(player);
            stuffRules.check(player);
        });
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
            if (material != null && minimum >= 0 && maximum >= minimum && maximum <= 64 && Double.isFinite(chance) && chance >= 0.0D && chance <= 100.0D)
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
    double treeAppleChance() { return GameplayMath.percent(decimal(getConfig().get("tree-cutter.apple-chance"), 0.5D), 0.5D); }
    boolean hasCustomAppleChance() { return getConfig().contains("tree-cutter.apple-chance"); }
    void setTreeAppleChance(double chance) { setConfigValue("tree-cutter.apple-chance", Math.clamp(chance, 0.0D, 100.0D)); }
    void resetTreeAppleChance() { setConfigValue("tree-cutter.apple-chance", null); }
    boolean treeCutterLeavesEnabled() { return getConfig().getBoolean("tree-cutter.break-leaves", false); }
    void setTreeCutterLeavesEnabled(boolean enabled) { setConfigValue("tree-cutter.break-leaves", enabled); }
    boolean treeCutterSneakOnly() { return getConfig().getBoolean("tree-cutter.sneak-only", false); }
    void setTreeCutterSneakOnly(boolean enabled) { setConfigValue("tree-cutter.sneak-only", enabled); }
    boolean treeCutterBreakTimeEnabled() { return getConfig().getBoolean("tree-cutter.break-time.enabled", false); }
    void setTreeCutterBreakTimeEnabled(boolean enabled) { setConfigValue("tree-cutter.break-time.enabled", enabled); }
    double treeCutterBreakTimePercent() { return GameplayMath.percent(decimal(getConfig().get("tree-cutter.break-time.percent"), 100.0D), 100.0D); }
    void setTreeCutterBreakTimePercent(double percent) { setConfigValue("tree-cutter.break-time.percent", Math.clamp(percent, 0.0D, 100.0D)); }
    void copyOreSettings(Material source, Material target) {
        saveOreDrops(target, getOreDrops(source));
        saveOreVanillaDropOverrides(target, getOreVanillaDropOverrides(source));
        setOreXpBonus(target, getOreXpBonus(source));
    }
    boolean hasOreCustomisation(Material ore) {
        return getOreXpBonus(ore) > 0 || !getOreDrops(ore).isEmpty() || !getOreVanillaDropOverrides(ore).isEmpty();
    }
    // Each configured ore currently has one vanilla loot-table output. The override stores
    // its replacement item, so it must not be looked up by that replacement's material.
    MobDrop getOreVanillaOverride(Material ore) {
        return getOreVanillaDropOverrides(ore).stream().findFirst().orElse(null);
    }
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

    private static boolean isAnimal(EntityType type) { return switch (type) {
        case COW, MOOSHROOM, CHICKEN, PIG, SHEEP, RABBIT, COD, SALMON -> true; default -> false; }; }
    private static int number(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
    private static double decimal(Object value, double fallback) { return value instanceof Number n ? n.doubleValue() : fallback; }
    int getBoundedInt(String path, int fallback, int minimum, int maximum) { return Math.clamp(getConfig().getInt(path, fallback), minimum, maximum); }
    void setConfigValue(String path, Object value) { getConfig().set(path, value); saveConfig(); }
    void setConfigValues(Map<String, ?> values) { values.forEach((path, value) -> getConfig().set(path, value)); saveConfig(); }
    private static String mobPath(EntityType mob, String property) { return "mobs." + mob.name() + "." + property; }
    private static String orePath(Material ore, String property) { return "ore-drops." + ore.getKey().getKey() + "." + property; }
    record MobDrop(Material material, int minimum, int maximum, double chance) { }
}

