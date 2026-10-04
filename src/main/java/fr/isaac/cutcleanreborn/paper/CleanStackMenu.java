package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Admin categories for Clean Stacks material mappings. */
final class CleanStackMenu extends MenuSupport {
    private static final int WOOD_SLOT = 11, STONE_SLOT = 15, BACK_SLOT = 22;
    CleanStackMenu(CutCleanRebornPlugin plugin) {
        super(plugin, 3, "§8CCR • Clean Stacks");
        inventory.setItem(WOOD_SLOT, MenuItems.item(Material.OAK_LOG, "§6Wood", List.of("§7Choose the output of each log", "§7and plank type.", "", "§eClick to configure")));
        inventory.setItem(STONE_SLOT, MenuItems.item(Material.STONE, "§6Stone", List.of("§7Choose the output of each", "§7stone-family block.", "", "§eClick to configure")));
        inventory.setItem(BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to CCR Config.")));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == WOOD_SLOT) new CleanStackMaterialMenu(plugin, CleanStackService.Category.WOOD, 0).open(player);
        else if (slot == STONE_SLOT) new CleanStackMaterialMenu(plugin, CleanStackService.Category.STONE, 0).open(player);
        else if (slot == BACK_SLOT) new ConfigMainMenu(plugin).open(player);
    }
}
