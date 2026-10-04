package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Focused configuration menu for one automatically enchanted item type. */
final class AutoEnchantItemMenu extends MenuSupport {
    private static final int ENCHANTMENTS_SLOT = 11;
    private static final int ITEM_SLOT = 13;
    private static final int COPY_SLOT = 15;
    private static final int BACK_SLOT = 22;
    private final AutoEnchantCategory category;
    private final Material material;
    private final int itemPage;
    AutoEnchantItemMenu(CutCleanRebornPlugin plugin, AutoEnchantCategory category, Material material, int itemPage) {
        super(plugin, 3, "§8CCR • " + MenuItems.prettyName(material));
        this.category = category; this.material = material; this.itemPage = itemPage;
        render();
    }
    private void render() {
        inventory.setItem(ITEM_SLOT, MenuItems.item(material, "§6" + MenuItems.prettyName(material),
                List.of("§7Configured enchantments: §e" + plugin.autoEnchantments().enchantments(material).size())));
        inventory.setItem(ENCHANTMENTS_SLOT, MenuItems.item(Material.ENCHANTING_TABLE, "§6Enchantments",
                List.of("§7Add or edit enchantments.", "", "§eClick to open")));
        inventory.setItem(COPY_SLOT, MenuItems.item(Material.WRITABLE_BOOK, "§bCopy Same Material Enchants",
                List.of("§7Merge compatible enchantments into", "§7other " + category.title().toLowerCase() + " of the same material.", "", "§eClick to copy")));
        inventory.setItem(BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to " + category.title() + ".")));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == ENCHANTMENTS_SLOT) new AutoEnchantmentsListMenu(plugin, category, material, itemPage, 0).open(player);
        else if (slot == COPY_SLOT) { plugin.autoEnchantments().copySameMaterial(category, material); render(); player.sendMessage("§a[CCR] Compatible enchantments copied."); }
        else if (slot == BACK_SLOT) new AutoEnchantItemListMenu(plugin, category, itemPage).open(player);
    }
}
