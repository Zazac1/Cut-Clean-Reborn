package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Admin categories for Clean Stacks material mappings. */
final class CleanStackMenu extends MenuSupport {
    @Override protected MenuSupport parentMenu() { return new ConfigMainMenu(plugin); }

    private static final int WOOD_SLOT = 11, STONE_SLOT = 15;
    CleanStackMenu(CutCleanRebornPlugin plugin) {
        super(plugin, 3, "§8CCR • Clean Stacks");
        inventory.setItem(13, MenuItems.item(Material.LEVER, "§bToggle My Clean Stacks", List.of("§7Same as /cleanstacks.", "§7Only converts blocks you break.")));
        inventory.setItem(WOOD_SLOT, MenuItems.item(Material.OAK_LOG, "§6Wood", List.of("§7Choose the output of each log", "§7and plank type.", "", "§eClick to configure")));
        inventory.setItem(STONE_SLOT, MenuItems.item(Material.STONE, "§6Stone", List.of("§7Choose the output of each", "§7stone-family block.", "", "§eClick to configure")));
        header(Material.CHEST, "§6Clean Stacks");
        footer("CCR Config");
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (slot == 13) { plugin.cleanStacks().setEnabled(player, !plugin.cleanStacks().enabled(player)); player.sendMessage(CutCleanCommands.PREFIX + "Clean Stacks: " + CutCleanCommands.state(plugin.cleanStacks().enabled(player))); }
        else if (slot == WOOD_SLOT) new CleanStackMaterialMenu(plugin, CleanStackService.Category.WOOD, 0).open(player);
        else if (slot == STONE_SLOT) new CleanStackMaterialMenu(plugin, CleanStackService.Category.STONE, 0).open(player);
    }
}
