package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

final class TreeHarvestMenu extends MenuSupport {
    TreeHarvestMenu(CutCleanRebornPlugin plugin) {
        super(plugin, 4, "§8Timber • Leaves & Apples");
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return new TreeCutterMenu(plugin); }

    @Override protected void refresh() {
        clearMenu();
        header(Material.OAK_LEAVES, "§6Leaves & Apples",
                "§7Leaf harvesting belongs to Timber.", "§7Apple chances also apply when leaves are broken normally.");
        inventory.setItem(11, MenuItems.toggle("Harvest Tree Leaves", plugin.treeCutterLeavesEnabled(), plugin.treeCutterEnabled(), "Timber",
                "§7Harvest this tree's leaves after its logs.", "§7Saplings, sticks and other loot are preserved."));
        inventory.setItem(15, MenuItems.item(Material.APPLE, "§6Apple Chance §8• §e" + MenuItems.number(plugin.treeAppleChance()) + "%",
                List.of(plugin.hasCustomAppleChance() ? "§7Custom override is active." : "§7Using the vanilla 0.5% chance.",
                        "§7Oak and dark oak leaves only.", "§7Shears and Silk Touch keep their normal drops.",
                        "§7Independent of the Timber master switch.", "", "§eClick to edit (0-100%).")));
        inventory.setItem(22, MenuItems.item(Material.BRUSH, "§eRestore Vanilla Apple Chance",
                List.of("§7Remove the override and restore 0.5%.", "§7Other Timber settings stay as configured.")));
        footer("Timber");
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (slot == 11) { plugin.setTreeCutterLeavesEnabled(!plugin.treeCutterLeavesEnabled()); refresh(); }
        else if (slot == 15) new AnvilTextInput<Double>(plugin, "§8Timber • Apple Chance", "apple chance (0-100%)",
                MenuItems.number(plugin.treeAppleChance()), GameplayMath::percentage,
                (target, value) -> { plugin.setTreeAppleChance(value); new TreeHarvestMenu(plugin).open(target); }).open(player);
        else if (slot == 22) { plugin.resetTreeAppleChance(); refresh(); }
    }
}
