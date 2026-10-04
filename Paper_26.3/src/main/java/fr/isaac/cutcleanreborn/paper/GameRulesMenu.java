package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

final class GameRulesMenu extends MenuSupport {
    GameRulesMenu(CutCleanRebornPlugin plugin) {
        super(plugin, 3, "§8CCR • Game Rules");
        inventory.setItem(13, MenuItems.item(Material.IRON_CHESTPLATE, "§6Stuff Rules", List.of("§7Equipment and rare-item restrictions.", "", "§eClick to open")));
        inventory.setItem(22, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to CCR Config.")));
    }
    @Override void handleClick(Player player, int slot, ClickType click) { if (slot == 13) new StuffRulesMenu(plugin).open(player); else if (slot == 22) new ConfigMainMenu(plugin).open(player); }
}
