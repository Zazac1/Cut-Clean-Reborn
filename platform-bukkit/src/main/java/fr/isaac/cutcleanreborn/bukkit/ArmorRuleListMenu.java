package fr.isaac.cutcleanreborn.bukkit;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

final class ArmorRuleListMenu extends MenuSupport {
    private List<StuffRulesService.ArmorRule> rules = List.of();
    private int page;

    ArmorRuleListMenu(CutCleanRebornPlugin plugin, int requestedPage) {
        super(plugin, 6, "§8Stuff Rules • Armor Limits");
        page = requestedPage;
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return new StuffRulesMenu(plugin); }

    @Override protected void refresh() {
        clearMenu();
        rules = plugin.stuffRules().armorRules();
        int pages = Math.max(1, (rules.size() + 20) / 21);
        page = Math.clamp(page, 0, pages - 1);
        header(Material.DIAMOND_CHESTPLATE, "§6Equipped Armor Limits",
                "§7Limit pieces worn at the same time, not items carried.",
                "§7Create a rule, choose its target, then enable it.");
        for (int i = 0; i < 21 && page * 21 + i < rules.size(); i++) {
            var rule = rules.get(page * 21 + i);
            inventory.setItem(MobListMenu.MOB_SLOTS[i], MenuItems.stateItem(RuleTargetMenu.armorIcon(rule.target()),
                    "§6" + RuleTargetMenu.label(rule.target()) + " §8• " + MenuItems.effectiveState(rule.enabled(), plugin.stuffRules().enabled()),
                    List.of("§7Maximum worn together: §e" + rule.maximum() + " / 4",
                            "§7Saved rule: " + CutCleanCommands.state(rule.enabled()),
                            plugin.stuffRules().enabled() ? "§7Stuff Rules is enabled." : "§ePaused while Stuff Rules is OFF.",
                            "", "§eClick: edit this rule.", "§7Right click: quick toggle.", "§7Shift click: review deletion."), rule.enabled() && plugin.stuffRules().enabled()));
        }
        if (rules.isEmpty()) information(22, MenuItems.item(Material.GRAY_DYE, "§7No Armor Limits Yet",
                List.of("§7Use Add Armor Limit below to create one.")));
        inventory.setItem(40, MenuItems.item(Material.LIME_DYE, "§aAdd Armor Limit",
                List.of("§7New rules start OFF while you configure them.")));
        footer("Stuff Rules");
        information(49, MenuItems.pageInfo(page, pages));
        if (page > 0) inventory.setItem(47, MenuItems.item(Material.ARROW, "§ePrevious Page", List.of()));
        if (page + 1 < pages) inventory.setItem(51, MenuItems.item(Material.ARROW, "§eNext Page", List.of()));
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (slot == 40) { new ArmorRuleEditorMenu(plugin, plugin.stuffRules().addArmorRule(), page).open(player); return; }
        if (slot == 47 && page > 0) { page--; refresh(); return; }
        if (slot == 51 && (page + 1) * 21 < rules.size()) { page++; refresh(); return; }
        for (int i = 0; i < 21; i++) {
            if (slot != MobListMenu.MOB_SLOTS[i] || page * 21 + i >= rules.size()) continue;
            var rule = rules.get(page * 21 + i);
            if (click.isShiftClick()) confirm(player, "Delete this armor limit?", () -> plugin.stuffRules().removeArmorRule(rule.id()),
                    target -> new ArmorRuleListMenu(plugin, page).open(target));
            else if (click.isRightClick()) {
                plugin.stuffRules().saveArmorRule(new StuffRulesService.ArmorRule(rule.id(), !rule.enabled(), rule.target(), rule.maximum()));
                refresh();
            } else new ArmorRuleEditorMenu(plugin, rule, page).open(player);
            return;
        }
    }
}

