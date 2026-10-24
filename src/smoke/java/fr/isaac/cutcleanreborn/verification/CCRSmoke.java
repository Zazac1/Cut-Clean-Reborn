package fr.isaac.cutcleanreborn.verification;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Native Bukkit checks kept out of the release JAR. Reflection crosses plugin classloader boundaries. */
public final class CCRSmoke extends JavaPlugin {
    private JavaPlugin ccr;
    private int checks;

    @Override public void onEnable() {
        getServer().getScheduler().runTaskLater(this, () -> {
            try {
                ccr = (JavaPlugin) getServer().getPluginManager().getPlugin("CutCleanReborn");
                require(ccr != null && ccr.isEnabled(), "main plugin enabled");
                blockDropsRule();
                enchantments();
                cleanStacks();
                mobDrops();
                persistenceAndMace();
                menus();
                new MenuNavigationSmoke(this, ccr).run(this::finish);
            } catch (Throwable failure) { finish(failure); }
        }, 10L);
    }

    private void finish(Throwable failure) {
        try {
            if (failure != null) throw failure;
            getServer().dispatchCommand(getServer().getConsoleSender(), "ccr status");
            getServer().dispatchCommand(getServer().getConsoleSender(), "ccr reload");
            Files.writeString(Path.of("smoke-result.txt"), "PASS: " + checks + " native checks\n");
            getLogger().info("PASS: " + checks + " native checks");
        } catch (Throwable problem) {
            getLogger().log(java.util.logging.Level.SEVERE, "Native regression failed", problem);
            try { Files.writeString(Path.of("smoke-result.txt"), "FAIL: " + problem); }
            catch (Exception ignored) { }
        } finally { getServer().shutdown(); }
    }

    private void enchantments() throws Exception {
        Object service = call(ccr, "autoEnchantments");
        Object tools = enumValue("AutoEnchantCategory", "TOOLS");
        call(service, "setEnchantment", tools, Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 3);
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        require((Boolean) call(service, "apply", item), "new tool enchanted");
        require(item.getEnchantmentLevel(Enchantment.EFFICIENCY) == 3, "configured efficiency applied");
        require(item.getEnchantmentLevel(Enchantment.UNBREAKING) == 1, "original enchantment preserved");
        require(!(Boolean) call(service, "apply", item), "same item not processed twice");
        call(service, "setEnchantment", tools, Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 4);
        require(!(Boolean) call(service, "apply", item), "configuration does not silently rewrite old items");
        call(service, "requestRefresh");
        require((Boolean) call(service, "apply", item), "explicit refresh updates unchanged CCR tool");
        require(item.getEnchantmentLevel(Enchantment.EFFICIENCY) == 4, "refresh uses current rule");
        require(item.getEnchantmentLevel(Enchantment.UNBREAKING) == 1, "refresh preserves original enchantment");
        item.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        call(service, "requestRefresh");
        require(!(Boolean) call(service, "apply", item), "player-enchanted item protected from refresh");

        Object rules = call(ccr, "stuffRules");
        ccr.getConfig().set("game-rules.stuff-rules.enabled", true);
        ccr.getConfig().set("game-rules.stuff-rules.enchantment-caps", List.of(java.util.Map.of(
                "id", "smoke", "enabled", true, "target", "TOOLS", "enchantment", "minecraft:efficiency", "maximum", 2)));
        call(rules, "reloadCache");
        ItemStack capped = new ItemStack(Material.DIAMOND_PICKAXE);
        call(service, "apply", capped);
        require(capped.getEnchantmentLevel(Enchantment.EFFICIENCY) == 2, "automatic enchantments obey equipment caps");
        call(rules, "setEnabled", false);
    }

    @SuppressWarnings("unchecked")
    private void blockDropsRule() throws Exception {
        var world = getServer().getWorlds().getFirst();
        org.bukkit.GameRule<?> found = org.bukkit.GameRule.getByName("block_drops");
        if (found == null) found = org.bukkit.GameRule.getByName("doTileDrops");
        require(found != null, "correct version-specific block-drop rule is registered");
        var rule = (org.bukkit.GameRule<Boolean>) found;
        Boolean previous = world.getGameRuleValue(rule);
        Method enabled = type("BukkitCompat").getDeclaredMethod("blockDrops", org.bukkit.World.class);
        enabled.setAccessible(true);
        try {
            world.setGameRule(rule, false);
            require(!(Boolean) enabled.invoke(null, world), "support-break cane obeys disabled vanilla block drops");
            world.setGameRule(rule, true);
            require((Boolean) enabled.invoke(null, world), "support-break cane obeys enabled vanilla block drops");
        } finally { if (previous != null) world.setGameRule(rule, previous); }
    }

    private void cleanStacks() throws Exception {
        Object service = call(ccr, "cleanStacks");
        ItemStack converted = (ItemStack) call(service, "normalise", new ItemStack(Material.BIRCH_LOG, 32));
        require(converted.getType() == Material.OAK_LOG && converted.getAmount() == 32, "wood converted without quantity loss");
        ccr.getConfig().set("clean-stacks.wood.BIRCH_LOG", "minecraft:spruce_log");
        ccr.getConfig().set("clean-stacks.wood.SPRUCE_LOG", "minecraft:birch_log");
        ItemStack cycle = (ItemStack) call(service, "normalise", new ItemStack(Material.SPRUCE_LOG, 7));
        ItemStack again = (ItemStack) call(service, "normalise", cycle);
        require(cycle.isSimilar(again) && again.getAmount() == 7, "cyclic mappings have stable output");
        ItemStack named = new ItemStack(Material.BIRCH_LOG);
        var namedMeta = named.getItemMeta();
        namedMeta.setDisplayName("Custom log");
        named.setItemMeta(namedMeta);
        require(((ItemStack) call(service, "normalise", named)).getType() == Material.BIRCH_LOG, "custom item identity preserved");
    }

    private void mobDrops() throws Exception {
        Object rule = create("CutCleanRebornPlugin$MobDrop", Material.LEATHER, 2, 2, 100.0);
        call(ccr, "saveVanillaDropOverrides", EntityType.COW, List.of(rule));
        Cow cow = getServer().getWorlds().getFirst().createEntity(getServer().getWorlds().getFirst().getSpawnLocation(), Cow.class);
        EntityDeathEvent event = new EntityDeathEvent(cow, DamageSource.builder(DamageType.GENERIC).build(), new ArrayList<>(), 0);
        Object service = create("MobDropService", ccr);
        call(service, "onDeath", event);
        require(event.getDrops().stream().anyMatch(item -> item.getType() == Material.LEATHER && item.getAmount() == 2),
                "100% override works even when original drop list is empty");
    }

    private void menus() throws Exception {
        int entitiesBefore = getServer().getWorlds().getFirst().getEntities().size();
        Method mobItem = type("MenuItems").getDeclaredMethod("mobItem", EntityType.class, List.class);
        mobItem.setAccessible(true);
        ItemStack translated = (ItemStack) mobItem.invoke(null, EntityType.COW, List.of("§7Native lore"));
        require(translated.getItemMeta().serialize().get("display-name").toString().contains("entity.minecraft.cow"),
                "Bukkit mob icon keeps a client-translatable entity name");
        for (String menu : new String[] {"ConfigMainMenu", "StuffRulesMenu", "UniqueMaceMenu", "TreeCutterMenu", "TreeAxeMenu", "TreeHarvestMenu", "TreeBreakTimeMenu", "AutoEnchantCategoryMenu", "CleanStackMenu", "BetterSugarCaneMenu"})
            require(create(menu, ccr) instanceof InventoryHolder, menu + " renders");
        for (String cause : new String[] {"PLAYER", "SUPPORT"})
            require(create("SugarCaneCauseMenu", ccr, enumValue("BetterSugarCaneService$Cause", cause)) instanceof InventoryHolder, cause + " sugar cane page renders");
        create("MobListMenu", ccr, 0);
        create("MobGlobalSettingsMenu", ccr, 0);
        create("OreListMenu", ccr, 0);
        create("ArmorRuleListMenu", ccr, 0);
        create("EnchantmentCapListMenu", ccr, 0);
        for (String category : new String[] {"ARMOR", "TOOLS"}) create("AutoEnchantItemListMenu", ccr, enumValue("AutoEnchantCategory", category), 0);
        Object tools = enumValue("AutoEnchantCategory", "TOOLS");
        create("AutoEnchantItemMenu", ccr, tools, Material.DIAMOND_PICKAXE, 0);
        create("AutoEnchantmentsListMenu", ccr, tools, Material.DIAMOND_PICKAXE, 0, 0);
        for (String category : new String[] {"WOOD", "STONE"}) create("CleanStackMaterialMenu", ccr, enumValue("CleanStackService$Category", category), 0);
        var oreField = type("OreCatalog").getDeclaredField("ORES");
        oreField.setAccessible(true);
        for (Object ore : (List<?>) oreField.get(null)) {
            Material stone = (Material) call(ore, "stone");
            create("OreConfigurationMenu", ccr, ore, 0);
            create("OreDropListMenu", ccr, stone, ore, 0);
            Object rule = create("CutCleanRebornPlugin$MobDrop", Material.DIAMOND, 1, 1, 100.0);
            Object entry = create("OreEditableDrop", rule, false, rule);
            create("OreDropEditorMenu", ccr, stone, ore, 0, 0, entry, 0);
            checks++;
        }
        Object rulesService = call(ccr, "stuffRules");
        create("ArmorRuleEditorMenu", ccr, call(rulesService, "addArmorRule"), 0);
        create("EnchantmentCapEditorMenu", ccr, call(rulesService, "addEnchantmentCap"), 0);
        for (EntityType mob : EntityType.values()) {
            if (mob == EntityType.PLAYER || mob.getEntityClass() == null || !org.bukkit.entity.LivingEntity.class.isAssignableFrom(mob.getEntityClass())) continue;
            Object context = create("MobMenuContext", mob, 0);
            create("MobConfigurationMenu", ccr, context);
            create("DropListMenu", ccr, context);
            Object rule = create("CutCleanRebornPlugin$MobDrop", Material.DIAMOND, 1, 1, 100.0);
            Object entry = create("EditableDrop", rule, false, rule);
            create("DropEditorMenu", ccr, context, 0, entry, 0);
            checks++;
        }
        require(getServer().getWorlds().getFirst().getEntities().size() == entitiesBefore, "loot previews did not spawn real entities");
        Object main = create("ConfigMainMenu", ccr);
        ItemStack rules = ((InventoryHolder) main).getInventory().getItem(24);
        require(rules != null && rules.getType() == Material.IRON_CHESTPLATE, "Stuff Rules has a unique direct main-menu slot");
    }

    private void persistenceAndMace() throws Exception {
        var world = getServer().getWorlds().getFirst();
        var block = world.getSpawnLocation().getBlock();
        Object tracker = create("PlacedBlockTracker", ccr, "smoke_origin");
        call(tracker, "add", block);
        require((Boolean) call(tracker, "contains", block), "placed-block origin stored");
        Object reloaded = create("PlacedBlockTracker", ccr, "smoke_origin");
        require((Boolean) call(reloaded, "contains", block), "new tracker reads chunk-persistent origin");
        call(reloaded, "remove", block);
        require(!(Boolean) call(tracker, "contains", block), "consumed origin removed");

        Object rules = call(ccr, "stuffRules");
        ItemStack mace = new ItemStack(Material.MACE);
        String identity = "native-smoke-mace";
        var maceMeta = mace.getItemMeta();
        maceMeta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(ccr, "unique_mace_id"),
                org.bukkit.persistence.PersistentDataType.STRING, identity);
        mace.setItemMeta(maceMeta);
        ccr.getConfig().set("game-rules.stuff-rules.unique-mace.exists", true);
        ccr.getConfig().set("game-rules.stuff-rules.unique-mace.id", identity);
        var item = world.dropItem(world.getSpawnLocation(), mace);
        call(rules, "removed", new org.bukkit.event.entity.EntityRemoveEvent(item, org.bukkit.event.entity.EntityRemoveEvent.Cause.UNLOAD));
        require((Boolean) call(rules, "maceExists"), "chunk unload does not release unique mace");
        call(rules, "removed", new org.bukkit.event.entity.EntityRemoveEvent(item, org.bukkit.event.entity.EntityRemoveEvent.Cause.PICKUP));
        require((Boolean) call(rules, "maceExists"), "pickup does not release unique mace");
        call(rules, "removed", new org.bukkit.event.entity.EntityRemoveEvent(item, org.bukkit.event.entity.EntityRemoveEvent.Cause.DEATH));
        require(!(Boolean) call(rules, "maceExists"), "real destruction releases unique mace");
        item.remove();
    }

    Object enumValue(String name, String value) throws Exception {
        for (Object constant : type(name).getEnumConstants()) if (((Enum<?>) constant).name().equals(value)) return constant;
        throw new IllegalArgumentException(value);
    }

    Class<?> type(String simple) throws ClassNotFoundException {
        return Class.forName("fr.isaac.cutcleanreborn.bukkit." + simple, true, ccr.getClass().getClassLoader());
    }

    Object create(String name, Object... args) throws Exception {
        for (Constructor<?> constructor : type(name).getDeclaredConstructors()) {
            if (!matches(constructor.getParameterTypes(), args)) continue;
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        }
        throw new NoSuchMethodException(name);
    }

    Object call(Object target, String name, Object... args) throws Exception {
        for (Class<?> owner = target.getClass(); owner != null; owner = owner.getSuperclass()) {
            for (Method method : owner.getDeclaredMethods()) {
                if (!method.getName().equals(name) || !matches(method.getParameterTypes(), args)) continue;
                method.setAccessible(true);
                return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    private static boolean matches(Class<?>[] types, Object[] args) {
        if (types.length != args.length) return false;
        for (int i = 0; i < types.length; i++) {
            Class<?> expected = types[i] == int.class ? Integer.class : types[i] == double.class ? Double.class : types[i] == boolean.class ? Boolean.class : types[i];
            if (!expected.isInstance(args[i])) return false;
        }
        return true;
    }

    void require(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}

