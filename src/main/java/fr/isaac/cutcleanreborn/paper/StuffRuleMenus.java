package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Admin menus for the persistent armor-limit and enchantment-cap rules. */
final class ArmorRuleListMenu extends MenuSupport {
    static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private final List<StuffRulesService.ArmorRule> rules;
    private final int page;
    ArmorRuleListMenu(CutCleanRebornPlugin plugin, int requestedPage) {
        super(plugin, 6, "§8CCR • Armor Limits"); rules = plugin.stuffRules().armorRules(); int pages = Math.max(1, (rules.size() + SLOTS.length - 1) / SLOTS.length); page = Math.clamp(requestedPage, 0, pages - 1); render(pages);
    }
    private void render(int pages) {
        int start = page * SLOTS.length;
        for (int i = 0; i < SLOTS.length && start + i < rules.size(); i++) { StuffRulesService.ArmorRule rule = rules.get(start + i); inventory.setItem(SLOTS[i], MenuItems.stateItem(Material.DIAMOND_CHESTPLATE, "§6" + rule.target() + " §7max §e" + rule.maximum(), List.of("§7Status: " + (rule.enabled() ? "§aEnabled" : "§cDisabled"), "§7Equipped armor only.", "", "§eLeft: edit  §bRight: toggle", "§cShift: delete"), rule.enabled())); }
        inventory.setItem(40, MenuItems.item(Material.LIME_DYE, "§aAdd Armor Limit", List.of("§7Creates a disabled rule.", "§eClick to add")));
        inventory.setItem(49, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to Stuff Rules.")));
        if (page > 0) inventory.setItem(45, MenuItems.item(Material.ARROW, "§ePrevious page", List.of()));
        if (page + 1 < pages) inventory.setItem(53, MenuItems.item(Material.ARROW, "§eNext page", List.of()));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == 49) { new StuffRulesMenu(plugin).open(player); return; }
        if (slot == 40) { StuffRulesService.ArmorRule added = plugin.stuffRules().addArmorRule(); new ArmorRuleEditorMenu(plugin, added, page).open(player); return; }
        if (slot == 45 && page > 0) { new ArmorRuleListMenu(plugin, page - 1).open(player); return; }
        if (slot == 53 && (page + 1) * SLOTS.length < rules.size()) { new ArmorRuleListMenu(plugin, page + 1).open(player); return; }
        int start = page * SLOTS.length;
        for (int i = 0; i < SLOTS.length; i++) if (slot == SLOTS[i] && start + i < rules.size()) { StuffRulesService.ArmorRule rule = rules.get(start + i); if (click.isShiftClick()) { plugin.stuffRules().removeArmorRule(rule.id()); new ArmorRuleListMenu(plugin, page).open(player); } else if (click.isRightClick()) { plugin.stuffRules().saveArmorRule(new StuffRulesService.ArmorRule(rule.id(), !rule.enabled(), rule.target(), rule.maximum())); new ArmorRuleListMenu(plugin, page).open(player); } else new ArmorRuleEditorMenu(plugin, rule, page).open(player); return; }
    }
}

final class ArmorRuleEditorMenu extends MenuSupport {
    private StuffRulesService.ArmorRule rule; private final int page;
    ArmorRuleEditorMenu(CutCleanRebornPlugin plugin, StuffRulesService.ArmorRule rule, int page) { super(plugin, 3, "§8CCR • Edit Armor Limit"); this.rule = rule; this.page = page; render(); }
    private void render() {
        inventory.setItem(10, MenuItems.item(Material.DIAMOND, "§6Set Material Type", List.of("§7Current target: §e" + rule.target(), "§7Example: DIAMOND or ARMOR.", "§7Targets every matching armor piece.", "", "§eClick to edit")));
        inventory.setItem(12, MenuItems.item(Material.IRON_CHESTPLATE, "§6Set Armor Item(s)", List.of("§7Current target: §e" + rule.target(), "§7One or comma-separated item IDs.", "§7Example: diamond_helmet,diamond_boots", "", "§eClick to edit")));
        inventory.setItem(14, MenuItems.item(Material.COMPARATOR, "§6Maximum: §e" + rule.maximum(), List.of("§7Number of matching equipped pieces.", "§7Valid range: 0 to 4.", "", "§eClick to edit")));
        inventory.setItem(16, MenuItems.stateItem(Material.LEVER, "§6Rule: " + (rule.enabled() ? "§aON" : "§cOFF"), List.of("§7New rules are disabled by default.", "§eClick to toggle"), rule.enabled()));
        inventory.setItem(20, MenuItems.item(Material.WRITABLE_BOOK, "§bCopy to Another Armor Target", List.of("§7Copies maximum and target-independent settings.", "§7The copied rule starts §cdisabled§7.", "", "§eClick to choose target")));
        inventory.setItem(22, MenuItems.item(Material.TNT, "§cDelete Rule", List.of("§cClick to delete immediately.")));
        inventory.setItem(24, MenuItems.item(Material.BARRIER, "§cBack", List.of()));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == 10) new AnvilTextInput<String>(plugin, "§8CCR • Armor Material", "material type", "DIAMOND", StuffRulesService::normalizeArmorMaterialType, (p, value) -> save(p, new StuffRulesService.ArmorRule(rule.id(), rule.enabled(), value, rule.maximum()))).open(player);
        else if (slot == 12) new AnvilTextInput<String>(plugin, "§8CCR • Armor Items", "armor item IDs", rule.target(), StuffRulesService::normalizeArmorItems, (p, value) -> save(p, new StuffRulesService.ArmorRule(rule.id(), rule.enabled(), value, rule.maximum()))).open(player);
        else if (slot == 14) new AnvilTextInput<Integer>(plugin, "§8CCR • Armor Maximum", "maximum (0-4)", String.valueOf(rule.maximum()), ArmorRuleEditorMenu::armorMaximum, (p, value) -> save(p, new StuffRulesService.ArmorRule(rule.id(), rule.enabled(), rule.target(), value))).open(player);
        else if (slot == 16) save(player, new StuffRulesService.ArmorRule(rule.id(), !rule.enabled(), rule.target(), rule.maximum()));
        else if (slot == 20) new AnvilTextInput<String>(plugin, "§8CCR • Copy Armor Rule", "new target", rule.target(), StuffRulesService::normalizeArmorTarget, (p, value) -> { StuffRulesService.ArmorRule copy = plugin.stuffRules().copyArmorRule(rule, value); if (copy != null) new ArmorRuleEditorMenu(plugin, copy, page).open(p); }).open(player);
        else if (slot == 22) { plugin.stuffRules().removeArmorRule(rule.id()); new ArmorRuleListMenu(plugin, page).open(player); }
        else if (slot == 24) new ArmorRuleListMenu(plugin, page).open(player);
    }
    private void save(Player player, StuffRulesService.ArmorRule value) { rule = value; plugin.stuffRules().saveArmorRule(value); new ArmorRuleEditorMenu(plugin, value, page).open(player); }
    private static Integer armorMaximum(String text) { try { int value = Integer.parseInt(text.trim()); return value >= 0 && value <= 4 ? value : null; } catch (NumberFormatException ignored) { return null; } }
}

final class EnchantmentCapListMenu extends MenuSupport {
    private static final int[] SLOTS = ArmorRuleListMenu.SLOTS;
    private final List<StuffRulesService.EnchantmentCap> rules; private final int page;
    EnchantmentCapListMenu(CutCleanRebornPlugin plugin, int requestedPage) { super(plugin, 6, "§8CCR • Enchantment Caps"); rules = plugin.stuffRules().enchantmentCaps(); int pages = Math.max(1, (rules.size() + SLOTS.length - 1) / SLOTS.length); page = Math.clamp(requestedPage, 0, pages - 1); render(pages); }
    private void render(int pages) {
        int start = page * SLOTS.length;
        for (int i = 0; i < SLOTS.length && start + i < rules.size(); i++) { StuffRulesService.EnchantmentCap rule = rules.get(start + i); Enchantment enchantment = AutoEnchantService.findEnchantment(rule.enchantment()); inventory.setItem(SLOTS[i], MenuItems.stateItem(Material.ENCHANTED_BOOK, "§6" + (enchantment == null ? rule.enchantment() : MenuItems.prettyName(enchantment)) + " §7max §e" + rule.maximum(), List.of("§7Target: §e" + rule.target(), "§7Status: " + (rule.enabled() ? "§aEnabled" : "§cDisabled"), "§7Refund XP: " + (rule.refundXp() ? "§aON (§e" + rule.xpPerRemovedLevel() + "§a/level)" : "§cOFF"), "", "§eLeft: edit  §bRight: toggle", "§cShift: delete"), rule.enabled())); }
        inventory.setItem(40, MenuItems.item(Material.LIME_DYE, "§aAdd Enchantment Cap", List.of("§7Creates a disabled rule.", "§eClick to add")));
        inventory.setItem(49, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to Stuff Rules.")));
        if (page > 0) inventory.setItem(45, MenuItems.item(Material.ARROW, "§ePrevious page", List.of()));
        if (page + 1 < pages) inventory.setItem(53, MenuItems.item(Material.ARROW, "§eNext page", List.of()));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == 49) { new StuffRulesMenu(plugin).open(player); return; }
        if (slot == 40) { StuffRulesService.EnchantmentCap added = plugin.stuffRules().addEnchantmentCap(); new EnchantmentCapEditorMenu(plugin, added, page).open(player); return; }
        if (slot == 45 && page > 0) { new EnchantmentCapListMenu(plugin, page - 1).open(player); return; }
        if (slot == 53 && (page + 1) * SLOTS.length < rules.size()) { new EnchantmentCapListMenu(plugin, page + 1).open(player); return; }
        int start = page * SLOTS.length;
        for (int i = 0; i < SLOTS.length; i++) if (slot == SLOTS[i] && start + i < rules.size()) { StuffRulesService.EnchantmentCap rule = rules.get(start + i); if (click.isShiftClick()) { plugin.stuffRules().removeCap(rule.id()); new EnchantmentCapListMenu(plugin, page).open(player); } else if (click.isRightClick()) { plugin.stuffRules().saveCap(new StuffRulesService.EnchantmentCap(rule.id(), !rule.enabled(), rule.target(), rule.enchantment(), rule.maximum(), rule.refundXp(), rule.xpPerRemovedLevel())); new EnchantmentCapListMenu(plugin, page).open(player); } else new EnchantmentCapEditorMenu(plugin, rule, page).open(player); return; }
    }
}

final class EnchantmentCapEditorMenu extends MenuSupport {
    private StuffRulesService.EnchantmentCap rule; private final int page;
    EnchantmentCapEditorMenu(CutCleanRebornPlugin plugin, StuffRulesService.EnchantmentCap rule, int page) { super(plugin, 4, "§8CCR • Edit Enchantment Cap"); this.rule = rule; this.page = page; render(); }
    private void render() {
        inventory.setItem(10, MenuItems.item(Material.BOOKSHELF, "§6Set Item Category", List.of("§7Current target: §e" + rule.target(), "§7SWORDS, TOOLS, ARMOR or ALL.", "", "§eClick to edit")));
        inventory.setItem(12, MenuItems.item(Material.IRON_SWORD, "§6Set Precise Item(s)", List.of("§7Current target: §e" + rule.target(), "§7One or comma-separated item IDs.", "§7Example: diamond_sword,netherite_sword", "", "§eClick to edit")));
        inventory.setItem(14, MenuItems.item(Material.ENCHANTED_BOOK, "§6Enchantment: §e" + rule.enchantment(), List.of("§7A valid namespaced identifier.", "§7Example: minecraft:sharpness", "", "§eClick to edit")));
        inventory.setItem(16, MenuItems.item(Material.COMPARATOR, "§6Maximum Level: §e" + rule.maximum(), List.of("§7Positive whole number.", "§eClick to edit")));
        inventory.setItem(20, MenuItems.stateItem(Material.LEVER, "§6Rule: " + (rule.enabled() ? "§aON" : "§cOFF"), List.of("§7New rules are disabled by default.", "§eClick to toggle"), rule.enabled()));
        inventory.setItem(22, MenuItems.stateItem(Material.EXPERIENCE_BOTTLE, "§6Refund XP: " + (rule.refundXp() ? "§aON" : "§cOFF"), List.of("§7Only verified enchanting/anvil costs.", "§7Never refunds corrected or found items.", "§eClick to toggle"), rule.refundXp()));
        inventory.setItem(24, MenuItems.item(Material.GOLD_NUGGET, "§6XP per Removed Level: §e" + rule.xpPerRemovedLevel(), List.of("§7Whole number; capped by paid cost.", "§eClick to edit")));
        inventory.setItem(29, MenuItems.item(Material.WRITABLE_BOOK, "§bCopy to Another Target", List.of("§7Copies cap, enchantment and XP options.", "§7The copied rule starts §cdisabled§7.", "", "§eClick to choose target")));
        inventory.setItem(30, MenuItems.item(Material.TNT, "§cDelete Rule", List.of("§cClick to delete immediately.")));
        inventory.setItem(32, MenuItems.item(Material.BARRIER, "§cBack", List.of()));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == 10) new AnvilTextInput<String>(plugin, "§8CCR • Item Category", "SWORDS/TOOLS/ARMOR/ALL", "SWORDS", StuffRulesService::normalizeItemCategory, (p, value) -> save(p, new StuffRulesService.EnchantmentCap(rule.id(), rule.enabled(), value, rule.enchantment(), rule.maximum(), rule.refundXp(), rule.xpPerRemovedLevel()))).open(player);
        else if (slot == 12) new AnvilTextInput<String>(plugin, "§8CCR • Precise Items", "item IDs", rule.target(), StuffRulesService::normalizeItemItems, (p, value) -> save(p, new StuffRulesService.EnchantmentCap(rule.id(), rule.enabled(), value, rule.enchantment(), rule.maximum(), rule.refundXp(), rule.xpPerRemovedLevel()))).open(player);
        else if (slot == 14) new AnvilTextInput<Enchantment>(plugin, "§8CCR • Enchantment", "enchantment identifier", rule.enchantment(), AutoEnchantService::findEnchantment, (p, value) -> save(p, new StuffRulesService.EnchantmentCap(rule.id(), rule.enabled(), rule.target(), value.getKey().toString(), rule.maximum(), rule.refundXp(), rule.xpPerRemovedLevel()))).open(player);
        else if (slot == 16) new AnvilTextInput<Integer>(plugin, "§8CCR • Cap Maximum", "positive level", String.valueOf(rule.maximum()), EnchantmentCapEditorMenu::positive, (p, value) -> save(p, new StuffRulesService.EnchantmentCap(rule.id(), rule.enabled(), rule.target(), rule.enchantment(), value, rule.refundXp(), rule.xpPerRemovedLevel()))).open(player);
        else if (slot == 20) save(player, new StuffRulesService.EnchantmentCap(rule.id(), !rule.enabled(), rule.target(), rule.enchantment(), rule.maximum(), rule.refundXp(), rule.xpPerRemovedLevel()));
        else if (slot == 22) save(player, new StuffRulesService.EnchantmentCap(rule.id(), rule.enabled(), rule.target(), rule.enchantment(), rule.maximum(), !rule.refundXp(), rule.xpPerRemovedLevel()));
        else if (slot == 24) new AnvilTextInput<Integer>(plugin, "§8CCR • XP Refund", "XP per removed level", String.valueOf(rule.xpPerRemovedLevel()), EnchantmentCapEditorMenu::nonNegative, (p, value) -> save(p, new StuffRulesService.EnchantmentCap(rule.id(), rule.enabled(), rule.target(), rule.enchantment(), rule.maximum(), rule.refundXp(), value))).open(player);
        else if (slot == 29) new AnvilTextInput<String>(plugin, "§8CCR • Copy Enchantment Cap", "new target", rule.target(), StuffRulesService::normalizeItemTarget, (p, value) -> { StuffRulesService.EnchantmentCap copy = plugin.stuffRules().copyCap(rule, value); if (copy != null) new EnchantmentCapEditorMenu(plugin, copy, page).open(p); }).open(player);
        else if (slot == 30) { plugin.stuffRules().removeCap(rule.id()); new EnchantmentCapListMenu(plugin, page).open(player); }
        else if (slot == 32) new EnchantmentCapListMenu(plugin, page).open(player);
    }
    private void save(Player player, StuffRulesService.EnchantmentCap value) { rule = value; plugin.stuffRules().saveCap(value); new EnchantmentCapEditorMenu(plugin, value, page).open(player); }
    private static Integer positive(String text) { try { int result = Integer.parseInt(text.trim()); return result > 0 ? result : null; } catch (NumberFormatException ignored) { return null; } }
    private static Integer nonNegative(String text) { try { int result = Integer.parseInt(text.trim()); return result >= 0 ? result : null; } catch (NumberFormatException ignored) { return null; } }
}
