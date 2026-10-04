package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Root category menu; new configuration areas can be added here without changing mob menus. */
final class ConfigMainMenu extends MenuSupport {
    private static final int MOB_CUSTOMISATION_SLOT = 10;
    private static final int ORE_DROPS_SLOT = 12;
    private static final int TREE_CUTTER_SLOT = 14;
    private static final int AUTO_ENCHANTMENTS_SLOT = 16;
    private static final int CLEAN_STACKS_SLOT = 20;
    private static final int BETTER_SUGAR_CANE_SLOT = 22;
    private static final int STUFF_RULES_SLOT = 24;
    private static final int GAME_RULES_SLOT = 24;
    private static final int EXIT_SLOT = 31;

    ConfigMainMenu(CutCleanRebornPlugin plugin) {
        super(plugin, 4, "§8CCR Config");
        inventory.setItem(MOB_CUSTOMISATION_SLOT, MenuItems.item(Material.SPAWNER, "§6Mob Customisation",
                List.of("§7Configure drops, XP and cooked food.", "", "§eClick to open")));
        inventory.setItem(ORE_DROPS_SLOT, MenuItems.item(Material.DIAMOND_ORE, "§6Ore Drops",
                List.of("§7Configure vanilla and extra ore drops.", "", "§eClick to open")));
        inventory.setItem(TREE_CUTTER_SLOT, MenuItems.item(Material.DIAMOND_AXE, "§6Timber",
                List.of("§7Configure whole-tree cutting and leaf apples.", "", "§eClick to open")));
        inventory.setItem(AUTO_ENCHANTMENTS_SLOT, MenuItems.item(Material.ENCHANTING_TABLE, "§6Automatic Enchantments",
                List.of("§7Apply configured enchantments once", "§7when a new item reaches a player.", "", "§eClick to open")));
        inventory.setItem(CLEAN_STACKS_SLOT, MenuItems.item(Material.CHEST, "§6Clean Stacks",
                List.of("§7Unify wood and stone stacks for", "§7players who enable /cleanstacks.", "", "§eClick to open")));
        inventory.setItem(BETTER_SUGAR_CANE_SLOT, MenuItems.item(Material.SUGAR_CANE, "§6Better Sugar Cane",
                List.of("§7Configure extra sugar-cane drops", "§7for player and support breaks.", "", "§eClick to open")));
        inventory.setItem(STUFF_RULES_SLOT, MenuItems.item(Material.CHEST, "§6Game Rules • Stuff Rules", List.of("§7Equipment and rare-item rules.", "", "§eClick to open")));
        inventory.setItem(GAME_RULES_SLOT, MenuItems.item(Material.IRON_CHESTPLATE, "§6Game Rules",
                List.of("§7Configure server-side game rules.", "", "§eClick to open")));
        inventory.setItem(EXIT_SLOT, MenuItems.item(Material.BARRIER, "§cExit", List.of("§7Close the configuration.")));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == MOB_CUSTOMISATION_SLOT) new MobListMenu(plugin, 0).open(player);
        else if (slot == ORE_DROPS_SLOT) new OreListMenu(plugin, 0).open(player);
        else if (slot == TREE_CUTTER_SLOT) new TreeCutterMenu(plugin).open(player);
        else if (slot == AUTO_ENCHANTMENTS_SLOT) new AutoEnchantCategoryMenu(plugin).open(player);
        else if (slot == CLEAN_STACKS_SLOT) new CleanStackMenu(plugin).open(player);
        else if (slot == BETTER_SUGAR_CANE_SLOT) new BetterSugarCaneMenu(plugin).open(player);
        else if (slot == STUFF_RULES_SLOT) new StuffRulesMenu(plugin).open(player);
        else if (slot == GAME_RULES_SLOT) new GameRulesMenu(plugin).open(player);
        else if (slot == EXIT_SLOT) player.closeInventory();
    }
}
