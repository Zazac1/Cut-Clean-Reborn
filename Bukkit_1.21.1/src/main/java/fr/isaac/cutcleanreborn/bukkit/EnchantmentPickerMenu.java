package fr.isaac.cutcleanreborn.bukkit;

import java.util.Comparator;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

final class EnchantmentPickerMenu extends MenuSupport {
    private final StuffRulesService.EnchantmentCap rule;
    private final int rulePage;
    private final List<Enchantment> enchantments;
    private int page;

    EnchantmentPickerMenu(CutCleanRebornPlugin plugin, StuffRulesService.EnchantmentCap rule, int rulePage, int requestedPage) {
        super(plugin, 6, "§8Stuff Rules • Choose Enchantment");
        this.rule = rule;
        this.rulePage = rulePage;
        enchantments = Registry.ENCHANTMENT.stream().sorted(Comparator.comparing(value -> value.getKey().toString())).toList();
        page = Math.clamp(requestedPage, 0, Math.max(0, (enchantments.size() - 1) / 21));
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return new EnchantmentCapEditorMenu(plugin, rule, rulePage); }

    @Override protected void refresh() {
        clearMenu();
        header(Material.ENCHANTED_BOOK, "§6Choose an Enchantment to Limit",
                "§7This creates a restriction, not an automatic enchantment.",
                "§7Target: " + RuleTargetMenu.label(rule.target()));
        for (int i = 0; i < 21 && page * 21 + i < enchantments.size(); i++) {
            Enchantment enchantment = enchantments.get(page * 21 + i);
            boolean chosen = enchantment.getKey().toString().equals(rule.enchantment());
            inventory.setItem(MobListMenu.MOB_SLOTS[i], MenuItems.stateItem(Material.ENCHANTED_BOOK,
                    "§6" + MenuItems.prettyName(enchantment) + (chosen ? " §a✓" : ""),
                    List.of("§7ID: " + enchantment.getKey(), "§7Normal maximum: " + enchantment.getMaxLevel(),
                            chosen ? "§aCurrently selected." : "§eClick to select."), chosen));
        }
        inventory.setItem(40, MenuItems.item(Material.COMPASS, "§bFind by Identifier",
                List.of("§7Example: minecraft:sharpness")));
        footer("Edit Rule");
        information(49, MenuItems.pageInfo(page, Math.max(1, (enchantments.size() + 20) / 21)));
        if (page > 0) inventory.setItem(47, MenuItems.item(Material.ARROW, "§ePrevious Page", List.of()));
        if ((page + 1) * 21 < enchantments.size()) inventory.setItem(51, MenuItems.item(Material.ARROW, "§eNext Page", List.of()));
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (slot == 47 && page > 0) { page--; refresh(); return; }
        if (slot == 51 && (page + 1) * 21 < enchantments.size()) { page++; refresh(); return; }
        if (slot == 40) {
            new AnvilTextInput<Enchantment>(plugin, "§8Cap • Find Enchantment", "enchantment ID", rule.enchantment(),
                    AutoEnchantService::findEnchantment, this::select).open(player);
            return;
        }
        for (int i = 0; i < 21; i++) if (slot == MobListMenu.MOB_SLOTS[i] && page * 21 + i < enchantments.size()) {
            select(player, enchantments.get(page * 21 + i));
            return;
        }
    }

    private void select(Player player, Enchantment enchantment) {
        var updated = new StuffRulesService.EnchantmentCap(rule.id(), rule.enabled(), rule.target(), enchantment.getKey().toString(),
                rule.maximum(), rule.refundXp(), rule.xpPerRemovedLevel());
        plugin.stuffRules().saveCap(updated);
        new EnchantmentCapEditorMenu(plugin, updated, rulePage).open(player);
    }
}

