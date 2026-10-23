package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Timber's entry point separates eligibility, harvesting and pacing. */
final class TreeCutterMenu extends MenuSupport {
    TreeCutterMenu(CutCleanRebornPlugin plugin) {
        super(plugin, 4, "§8CCR • Timber");
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return new ConfigMainMenu(plugin); }

    @Override protected void refresh() {
        clearMenu();
        header(Material.OAK_LOG, "§6Timber • Natural Trees",
                "§7Cut the first log to harvest its tree.",
                "§7Placed logs, tool durability and protection rules are respected.");
        inventory.setItem(13, MenuItems.toggle("Enable Timber", plugin.treeCutterEnabled(), true, "",
                "§7Master switch for whole-tree cutting.",
                "§7Leaf apple overrides remain an independent setting."));
        inventory.setItem(20, MenuItems.item(Material.DIAMOND_AXE, "§bTools & Activation",
                List.of("§7Choose allowed axes and other tools.",
                        "§7Without an axe: " + CutCleanCommands.state(plugin.treeCutterHandEnabled()),
                        "§7Sneak required: " + CutCleanCommands.state(plugin.treeCutterSneakOnly()), "", "§eClick to configure.")));
        inventory.setItem(22, MenuItems.item(Material.OAK_LEAVES, "§bLeaves & Apples",
                List.of("§7Harvest leaves with the tree.",
                        "§7Leaf harvesting: " + MenuItems.effectiveState(plugin.treeCutterLeavesEnabled(), plugin.treeCutterEnabled()),
                        "§7Apple chance: §e" + MenuItems.number(plugin.treeAppleChance()) + "%", "", "§eClick to configure.")));
        inventory.setItem(24, MenuItems.item(Material.CLOCK, "§bBreaking & Limits",
                List.of("§7Adjust mining cycles and tree size.",
                        "§7Scaled breaking: " + MenuItems.effectiveState(plugin.treeCutterBreakTimeEnabled(), plugin.treeCutterEnabled()),
                        "§7Maximum: §e" + plugin.getBoundedInt("tree-cutter.max-logs", 256, 2, 2048) + " logs",
                        "", "§eClick to configure.")));
        footer("CCR Config");
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (slot == 13) { plugin.setTreeCutterEnabled(!plugin.treeCutterEnabled()); refresh(); }
        else if (slot == 20) new TreeAxeMenu(plugin).open(player);
        else if (slot == 22) new TreeHarvestMenu(plugin).open(player);
        else if (slot == 24) new TreeBreakTimeMenu(plugin).open(player);
    }
}
