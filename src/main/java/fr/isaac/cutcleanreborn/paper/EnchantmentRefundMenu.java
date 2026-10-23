package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

final class EnchantmentRefundMenu extends MenuSupport {
    private final StuffRulesService.EnchantmentCap rule;
    private final int rulePage;

    EnchantmentRefundMenu(CutCleanRebornPlugin plugin, StuffRulesService.EnchantmentCap rule, int rulePage) {
        super(plugin, 3, "§8Enchantment Cap • XP Refunds");
        this.rule = rule;
        this.rulePage = rulePage;
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return new EnchantmentCapEditorMenu(plugin, rule, rulePage); }

    @Override protected void refresh() {
        clearMenu();
        header(Material.EXPERIENCE_BOTTLE, "§6Refund Paid XP Levels",
                "§7Only enchanting/anvil costs actually paid qualify.",
                "§7Found items and ordinary corrections never refund XP.");
        inventory.setItem(11, MenuItems.toggle("Enable Refunds", rule.refundXp(), rule.enabled() && plugin.stuffRules().enabled(),
                "this rule and Stuff Rules", "§7Refund levels when this cap reduces a paid enchantment."));
        inventory.setItem(15, MenuItems.item(Material.GOLD_NUGGET, "§6Paid Levels per Removed Level §8• §e" + rule.xpPerRemovedLevel(),
                List.of("§7Example: remove 2 enchantment levels at 1 each", "§7to refund at most 2 paid XP levels.",
                        "§7The refund never exceeds the transaction cost.", "§70 means no levels are refunded.", "", "§eClick to edit.")));
        footer("Edit Rule");
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (slot == 11) save(player, !rule.refundXp(), rule.xpPerRemovedLevel());
        else if (slot == 15) new AnvilTextInput<Integer>(plugin, "§8Refund • Paid XP Levels", "XP levels per removed level",
                String.valueOf(rule.xpPerRemovedLevel()), text -> GameplayMath.integer(text, 0, Integer.MAX_VALUE),
                (target, value) -> save(target, rule.refundXp(), value)).open(player);
    }

    private void save(Player player, boolean enabled, int levels) {
        var updated = new StuffRulesService.EnchantmentCap(rule.id(), rule.enabled(), rule.target(), rule.enchantment(), rule.maximum(), enabled, levels);
        plugin.stuffRules().saveCap(updated);
        new EnchantmentRefundMenu(plugin, updated, rulePage).open(player);
    }
}
