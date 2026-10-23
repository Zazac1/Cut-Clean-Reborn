package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

final class EnchantmentCapListMenu extends MenuSupport {
    private List<StuffRulesService.EnchantmentCap> rules = List.of();
    private int page;

    EnchantmentCapListMenu(CutCleanRebornPlugin plugin, int requestedPage) {
        super(plugin, 6, "§8Stuff Rules • Enchantment Caps");
        page = requestedPage;
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return new StuffRulesMenu(plugin); }

    @Override protected void refresh() {
        clearMenu();
        rules = plugin.stuffRules().enchantmentCaps();
        int pages = Math.max(1, (rules.size() + 20) / 21);
        page = Math.clamp(page, 0, pages - 1);
        header(Material.ENCHANTING_TABLE, "§6Enchantment Level Caps",
                "§7Choose an item target, an enchantment and its maximum.",
                "§7Overlapping caps use the lowest enabled maximum.");
        for (int i = 0; i < 21 && page * 21 + i < rules.size(); i++) {
            var rule = rules.get(page * 21 + i);
            var enchantment = AutoEnchantService.findEnchantment(rule.enchantment());
            String name = enchantment == null ? rule.enchantment() : MenuItems.prettyName(enchantment);
            inventory.setItem(MobListMenu.MOB_SLOTS[i], MenuItems.stateItem(Material.ENCHANTED_BOOK,
                    "§6" + name + " ≤ " + rule.maximum() + " §8• " + MenuItems.effectiveState(rule.enabled(), plugin.stuffRules().enabled()),
                    List.of("§7Target: §f" + RuleTargetMenu.label(rule.target()),
                            "§7Refund option: " + CutCleanCommands.state(rule.refundXp()),
                            plugin.stuffRules().enabled() ? "§7Stuff Rules is enabled." : "§ePaused while Stuff Rules is OFF.",
                            "", "§eClick: edit this rule.", "§7Right click: quick toggle.", "§7Shift click: review deletion."), rule.enabled() && plugin.stuffRules().enabled()));
        }
        if (rules.isEmpty()) information(22, MenuItems.item(Material.GRAY_DYE, "§7No Enchantment Caps Yet",
                List.of("§7Use Add Enchantment Cap below to create one.")));
        inventory.setItem(40, MenuItems.item(Material.LIME_DYE, "§aAdd Enchantment Cap",
                List.of("§7New rules start OFF while you configure them.")));
        footer("Stuff Rules");
        information(49, MenuItems.pageInfo(page, pages));
        if (page > 0) inventory.setItem(47, MenuItems.item(Material.ARROW, "§ePrevious Page", List.of()));
        if (page + 1 < pages) inventory.setItem(51, MenuItems.item(Material.ARROW, "§eNext Page", List.of()));
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (slot == 40) { new EnchantmentCapEditorMenu(plugin, plugin.stuffRules().addEnchantmentCap(), page).open(player); return; }
        if (slot == 47 && page > 0) { page--; refresh(); return; }
        if (slot == 51 && (page + 1) * 21 < rules.size()) { page++; refresh(); return; }
        for (int i = 0; i < 21; i++) {
            if (slot != MobListMenu.MOB_SLOTS[i] || page * 21 + i >= rules.size()) continue;
            var rule = rules.get(page * 21 + i);
            if (click.isShiftClick()) confirm(player, "Delete this enchantment cap?", () -> plugin.stuffRules().removeCap(rule.id()),
                    target -> new EnchantmentCapListMenu(plugin, page).open(target));
            else if (click.isRightClick()) {
                plugin.stuffRules().saveCap(new StuffRulesService.EnchantmentCap(rule.id(), !rule.enabled(), rule.target(), rule.enchantment(), rule.maximum(), rule.refundXp(), rule.xpPerRemovedLevel()));
                refresh();
            } else new EnchantmentCapEditorMenu(plugin, rule, page).open(player);
            return;
        }
    }
}
