package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Category split mandated for automatic enchantments. */
final class AutoEnchantCategoryMenu extends MenuSupport {
    private static final int REFRESH_SLOT = 4;
    private static final int ARMOR_SLOT = 11;
    private static final int ENABLE_SLOT = 13;
    private static final int TOOLS_SLOT = 15;
    private static final int BACK_SLOT = 22;
    AutoEnchantCategoryMenu(CutCleanRebornPlugin plugin) {
        super(plugin, 3, "§8CCR • Automatic Enchantments");
        boolean enabled = plugin.autoEnchantments().enabled();
        inventory.setItem(REFRESH_SLOT, MenuItems.item(Material.NAME_TAG, "§bRefresh auto-enchantments",
                List.of("§7Apply the current configuration to CCR items", "§7whose enchantments were not changed by a player.",
                        "", "§eClick to refresh")));
        inventory.setItem(ARMOR_SLOT, MenuItems.item(Material.DIAMOND_CHESTPLATE, "§6Armor",
                List.of("§7Configure armor auto-enchantments.", "", "§eClick to open")));
        inventory.setItem(ENABLE_SLOT, MenuItems.stateItem(Material.ENCHANTING_TABLE,
                enabled ? "§aAutomatic enchantments: ON" : "§cAutomatic enchantments: OFF",
                List.of("§7Status: " + (enabled ? "§aEnabled" : "§cDisabled"), "",
                        enabled ? "§eClick to disable" : "§eClick to enable"), enabled));
        inventory.setItem(TOOLS_SLOT, MenuItems.item(Material.DIAMOND_SWORD, "§6Tools",
                List.of("§7Configure tool auto-enchantments.", "", "§eClick to open")));
        inventory.setItem(BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to CCR Config.")));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == REFRESH_SLOT) {
            plugin.autoEnchantments().requestRefresh();
            player.sendMessage("§aCCR §8» §7Auto-enchantments refreshed for unchanged CCR items.");
        } else if (slot == ARMOR_SLOT) new AutoEnchantItemListMenu(plugin, AutoEnchantCategory.ARMOR, 0).open(player);
        else if (slot == ENABLE_SLOT) {
            boolean enabled = !plugin.autoEnchantments().enabled();
            plugin.autoEnchantments().setEnabled(enabled);
            player.sendMessage("§aCCR §8» §7Automatic enchantments are now " + (enabled ? "§aenabled" : "§cdisabled") + "§7.");
            new AutoEnchantCategoryMenu(plugin).open(player);
        }
        else if (slot == TOOLS_SLOT) new AutoEnchantItemListMenu(plugin, AutoEnchantCategory.TOOLS, 0).open(player);
        else if (slot == BACK_SLOT) new ConfigMainMenu(plugin).open(player);
    }
}
