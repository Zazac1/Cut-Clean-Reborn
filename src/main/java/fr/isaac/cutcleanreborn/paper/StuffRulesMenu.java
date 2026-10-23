package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Direct entry point to all equipment rules, without an extra one-button Game Rules screen. */
final class StuffRulesMenu extends MenuSupport {
    StuffRulesMenu(CutCleanRebornPlugin plugin) {
        super(plugin, 4, "§8CCR • Stuff Rules");
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return new ConfigMainMenu(plugin); }

    @Override protected void refresh() {
        clearMenu();
        StuffRulesService service = plugin.stuffRules();
        long armor = service.armorRules().stream().filter(StuffRulesService.ArmorRule::enabled).count();
        long caps = service.enchantmentCaps().stream().filter(StuffRulesService.EnchantmentCap::enabled).count();
        header(Material.IRON_CHESTPLATE, "§6Stuff Rules • Equipment",
                "§7Each category below has its own rules.",
                "§7Switching this feature OFF keeps your saved settings.");
        inventory.setItem(13, MenuItems.toggle("Enable Stuff Rules", service.enabled(), true, "",
                "§7Master switch for armor, enchantment caps and unique mace."));
        inventory.setItem(20, MenuItems.item(Material.DIAMOND_CHESTPLATE, "§bArmor Limits",
                List.of("§7Limit how many matching pieces can be worn.",
                        "§7Enabled rules: §e" + armor + " / " + service.armorRules().size(),
                        service.enabled() ? "§7Enabled rules apply immediately." : "§eAll armor limits are currently paused.",
                        "", "§eClick to manage armor rules.")));
        inventory.setItem(22, MenuItems.item(Material.ENCHANTING_TABLE, "§bEnchantment Caps",
                List.of("§7Limit a named enchantment on selected items.",
                        "§7Enabled rules: §e" + caps + " / " + service.enchantmentCaps().size(),
                        service.enabled() ? "§7Enabled rules apply immediately." : "§eAll enchantment caps are currently paused.",
                        "", "§eClick to manage caps and XP refunds.")));
        inventory.setItem(24, MenuItems.item(Material.MACE, "§bUnique Mace",
                List.of("§7One CCR-crafted mace for the whole server.",
                        "§7Status: " + MenuItems.effectiveState(service.maceEnabled(), service.enabled()),
                        service.maceExists() ? "§7Tracked mace: §ealready exists" : "§7Tracked mace: §anone",
                        "", "§eClick to configure and view availability.")));
        footer("CCR Config");
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (slot == 13) { plugin.stuffRules().setEnabled(!plugin.stuffRules().enabled()); refresh(); }
        else if (slot == 20) new ArmorRuleListMenu(plugin, 0).open(player);
        else if (slot == 22) new EnchantmentCapListMenu(plugin, 0).open(player);
        else if (slot == 24) new UniqueMaceMenu(plugin).open(player);
    }
}
