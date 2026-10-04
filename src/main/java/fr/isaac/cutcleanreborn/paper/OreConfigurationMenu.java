package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Chooses a block variant and copies its full drop configuration to its pair. */
final class OreConfigurationMenu extends MenuSupport {
    private static final int STONE_SLOT = 11, COPY_SLOT = 13, DEEPSLATE_SLOT = 15, BACK_SLOT = 22;
    private final OreDefinition ore;
    private final int listPage;
    OreConfigurationMenu(CutCleanRebornPlugin plugin, OreDefinition ore, int listPage) {
        super(plugin, 3, "§8CCR • " + ore.name()); this.ore = ore; this.listPage = listPage;
        if (ore.deepslate() == null) inventory.setItem(COPY_SLOT, blockItem(ore.stone(), "§6Configure " + MenuItems.prettyName(ore.stone())));
        else {
            inventory.setItem(STONE_SLOT, blockItem(ore.stone(), "§6Stone version"));
            inventory.setItem(DEEPSLATE_SLOT, blockItem(ore.deepslate(), "§6Deepslate version"));
            inventory.setItem(COPY_SLOT, MenuItems.item(Material.WRITABLE_BOOK, "§bCopy settings",
                    List.of("§eLeft click: §7Stone → Deepslate", "§eRight click: §7Deepslate → Stone")));
        }
        inventory.setItem(BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to ores.")));
    }
    private static org.bukkit.inventory.ItemStack blockItem(Material block, String title) {
        return MenuItems.item(block, title, List.of("§7Configure vanilla and extra drops.", "", "§eClick to open"));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == BACK_SLOT) new OreListMenu(plugin, listPage).open(player);
        else if (ore.deepslate() == null && slot == COPY_SLOT) new OreDropListMenu(plugin, ore.stone(), ore, listPage).open(player);
        else if (slot == STONE_SLOT) new OreDropListMenu(plugin, ore.stone(), ore, listPage).open(player);
        else if (slot == DEEPSLATE_SLOT) new OreDropListMenu(plugin, ore.deepslate(), ore, listPage).open(player);
        else if (slot == COPY_SLOT) {
            if (click.isRightClick()) plugin.copyOreSettings(ore.deepslate(), ore.stone());
            else plugin.copyOreSettings(ore.stone(), ore.deepslate());
            player.sendMessage("§aCCR §8» §7Ore drop settings copied.");
        }
    }
}
