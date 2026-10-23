package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

final class EnchantmentCapEditorMenu extends MenuSupport {
    private final StuffRulesService.EnchantmentCap rule;
    private final int page;

    EnchantmentCapEditorMenu(CutCleanRebornPlugin plugin, StuffRulesService.EnchantmentCap rule, int page) {
        super(plugin, 5, "§8Enchantment Cap • Edit Rule");
        this.rule = rule;
        this.page = page;
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return new EnchantmentCapListMenu(plugin, page); }

    @Override protected void refresh() {
        clearMenu();
        var enchantment = AutoEnchantService.findEnchantment(rule.enchantment());
        String name = enchantment == null ? rule.enchantment() : MenuItems.prettyName(enchantment);
        header(Material.ENCHANTED_BOOK, "§6" + name + " ≤ " + rule.maximum(),
                "§7Target: " + RuleTargetMenu.label(rule.target()), "§7Only this enchantment is limited; others are preserved.");
        inventory.setItem(10, MenuItems.toggle("Enable This Rule", rule.enabled(), plugin.stuffRules().enabled(), "Stuff Rules"));
        inventory.setItem(12, MenuItems.item(Material.COMPASS, "§bTarget §8• §f" + RuleTargetMenu.label(rule.target()),
                List.of("§7Choose a category or specific items.", "", "§eClick to choose.")));
        inventory.setItem(14, MenuItems.item(Material.ENCHANTED_BOOK, "§bEnchantment §8• §f" + name,
                List.of("§7Select the enchantment to restrict.", "", "§eClick to browse enchantments.")));
        inventory.setItem(16, MenuItems.item(Material.COMPARATOR, "§6Maximum Level §8• §e" + rule.maximum(),
                List.of("§7Higher levels are reduced to this value.", "", "§eClick to edit (positive whole number).")));
        inventory.setItem(20, MenuItems.item(Material.EXPERIENCE_BOTTLE, "§bXP Refunds §8• " + CutCleanCommands.state(rule.refundXp()),
                List.of("§7Refund up to " + rule.xpPerRemovedLevel() + " paid level(s) per removed level.",
                        "§7Only real enchanting and anvil costs qualify.", "", "§eClick to configure refunds.")));
        inventory.setItem(22, MenuItems.item(Material.WRITABLE_BOOK, "§bCopy This Rule",
                List.of("§7Choose another target for a disabled copy.", "§7Copies the cap and refund settings.")));
        inventory.setItem(24, MenuItems.item(Material.TNT, "§cDelete This Rule", List.of("§7Review deletion before confirming.")));
        footer("Enchantment Caps");
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (slot == 10) save(player, new StuffRulesService.EnchantmentCap(rule.id(), !rule.enabled(), rule.target(), rule.enchantment(), rule.maximum(), rule.refundXp(), rule.xpPerRemovedLevel()));
        else if (slot == 12) new RuleTargetMenu(plugin, false, rule.target(), "Enchantment Target",
                (target, value) -> save(target, new StuffRulesService.EnchantmentCap(rule.id(), rule.enabled(), value, rule.enchantment(), rule.maximum(), rule.refundXp(), rule.xpPerRemovedLevel())), this::self).open(player);
        else if (slot == 14) new EnchantmentPickerMenu(plugin, rule, page, 0).open(player);
        else if (slot == 16) new AnvilTextInput<Integer>(plugin, "§8Cap • Maximum Level", "positive maximum level", String.valueOf(rule.maximum()),
                text -> GameplayMath.integer(text, 1, Integer.MAX_VALUE),
                (target, value) -> save(target, new StuffRulesService.EnchantmentCap(rule.id(), rule.enabled(), rule.target(), rule.enchantment(), value, rule.refundXp(), rule.xpPerRemovedLevel()))).open(player);
        else if (slot == 20) new EnchantmentRefundMenu(plugin, rule, page).open(player);
        else if (slot == 22) new RuleTargetMenu(plugin, false, rule.target(), "Copy Enchantment Cap", (target, value) -> {
            var copy = plugin.stuffRules().copyCap(rule, value);
            if (copy != null) new EnchantmentCapEditorMenu(plugin, copy, page).open(target);
        }, this::self).open(player);
        else if (slot == 24) new ConfirmationMenu(plugin, "Delete this enchantment cap?", target -> {
            plugin.stuffRules().removeCap(rule.id());
            new EnchantmentCapListMenu(plugin, page).open(target);
        }, target -> self().open(target)).open(player);
    }

    private MenuSupport self() { return new EnchantmentCapEditorMenu(plugin, rule, page); }

    private void save(Player player, StuffRulesService.EnchantmentCap value) {
        plugin.stuffRules().saveCap(value);
        new EnchantmentCapEditorMenu(plugin, value, page).open(player);
    }
}
