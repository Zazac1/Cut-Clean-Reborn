package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

final class ArmorRuleEditorMenu extends MenuSupport {
    private final StuffRulesService.ArmorRule rule;
    private final int page;

    ArmorRuleEditorMenu(CutCleanRebornPlugin plugin, StuffRulesService.ArmorRule rule, int page) {
        super(plugin, 4, "§8Armor Limit • Edit Rule");
        this.rule = rule;
        this.page = page;
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return new ArmorRuleListMenu(plugin, page); }

    @Override protected void refresh() {
        clearMenu();
        header(RuleTargetMenu.armorIcon(rule.target()), "§6" + RuleTargetMenu.label(rule.target()),
                "§7At most " + rule.maximum() + " matching armor pieces worn together.",
                "§7Extra pieces return to the inventory, or drop if full.");
        inventory.setItem(10, MenuItems.toggle("Enable This Rule", rule.enabled(), plugin.stuffRules().enabled(), "Stuff Rules"));
        inventory.setItem(12, MenuItems.item(Material.COMPASS, "§bTarget §8• §f" + RuleTargetMenu.label(rule.target()),
                List.of("§7Choose a material family, all armor,", "§7or an exact list of items.", "", "§eClick to choose the target.")));
        inventory.setItem(14, MenuItems.item(Material.COMPARATOR, "§6Maximum Equipped §8• §e" + rule.maximum(),
                List.of("§7Only counts matching pieces being worn.", "§70 forbids wearing them; 4 permits a full set.", "", "§eClick to edit (0-4).")));
        inventory.setItem(16, MenuItems.item(Material.WRITABLE_BOOK, "§bCopy This Rule",
                List.of("§7Choose another target for a disabled copy.", "§7The original rule stays as configured.")));
        inventory.setItem(24, MenuItems.item(Material.TNT, "§cDelete This Rule", List.of("§7Review deletion before confirming.")));
        footer("Armor Limits");
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (slot == 10) save(player, new StuffRulesService.ArmorRule(rule.id(), !rule.enabled(), rule.target(), rule.maximum()));
        else if (slot == 12) new RuleTargetMenu(plugin, true, rule.target(), "Armor Target",
                (target, value) -> save(target, new StuffRulesService.ArmorRule(rule.id(), rule.enabled(), value, rule.maximum())), this::self).open(player);
        else if (slot == 14) new AnvilTextInput<Integer>(plugin, "§8Armor • Maximum Equipped", "maximum (0-4)",
                String.valueOf(rule.maximum()), text -> GameplayMath.integer(text, 0, 4),
                (target, value) -> save(target, new StuffRulesService.ArmorRule(rule.id(), rule.enabled(), rule.target(), value))).open(player);
        else if (slot == 16) new RuleTargetMenu(plugin, true, rule.target(), "Copy Armor Rule",
                (target, value) -> {
                    var copy = plugin.stuffRules().copyArmorRule(rule, value);
                    if (copy != null) new ArmorRuleEditorMenu(plugin, copy, page).open(target);
                }, this::self).open(player);
        else if (slot == 24) new ConfirmationMenu(plugin, "Delete this armor limit?", target -> {
            plugin.stuffRules().removeArmorRule(rule.id());
            new ArmorRuleListMenu(plugin, page).open(target);
        }, target -> self().open(target)).open(player);
    }

    private MenuSupport self() { return new ArmorRuleEditorMenu(plugin, rule, page); }

    private void save(Player player, StuffRulesService.ArmorRule value) {
        plugin.stuffRules().saveArmorRule(value);
        new ArmorRuleEditorMenu(plugin, value, page).open(player);
    }
}
