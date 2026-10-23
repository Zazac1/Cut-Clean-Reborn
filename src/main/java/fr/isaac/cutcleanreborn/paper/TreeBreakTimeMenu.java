package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Explains the actual repeated-mining mechanic separately from server processing speed. */
final class TreeBreakTimeMenu extends MenuSupport {
    TreeBreakTimeMenu(CutCleanRebornPlugin plugin) {
        super(plugin, 4, "§8Timber • Breaking & Limits");
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return new TreeCutterMenu(plugin); }

    @Override protected void refresh() {
        clearMenu();
        header(Material.CLOCK, "§6Breaking & Limits",
                "§7Mining effort and server processing are separate settings.");
        inventory.setItem(11, MenuItems.toggle("Scale Mining Effort", plugin.treeCutterBreakTimeEnabled(),
                plugin.treeCutterEnabled(), "Timber", "§7Repeat breaking the first log until the", "§7action-bar counter is complete."));
        inventory.setItem(15, MenuItems.item(Material.IRON_PICKAXE,
                "§6Effort per Extra Log §8• §e" + MenuItems.number(plugin.treeCutterBreakTimePercent()) + "%",
                List.of("§70%: one successful mining cycle.", "§7100%: one cycle per log in the tree.",
                        "§7Example: 10 logs at 50% need 6 cycles.",
                        "§7Used only when mining-effort scaling is ON.", "", "§eClick to edit (0-100%).")));
        inventory.setItem(20, MenuItems.item(Material.OAK_SAPLING,
                "§6Maximum Tree Size §8• §e" + plugin.getBoundedInt("tree-cutter.max-logs", 256, 2, 2048),
                List.of("§7Larger trees keep normal single-log breaking.", "§7Range: 2-2048 logs.", "", "§eClick to edit.")));
        inventory.setItem(24, MenuItems.item(Material.REPEATER,
                "§6Processing Speed §8• §e" + plugin.getBoundedInt("tree-cutter.blocks-per-tick", 12, 1, 64) + " blocks/tick",
                List.of("§7How quickly remaining blocks are harvested.", "§7Lower values spread the work over more ticks.",
                        "§7This does not change the mining effort.", "", "§eClick to edit (1-64).")));
        footer("Timber");
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (slot == 11) { plugin.setTreeCutterBreakTimeEnabled(!plugin.treeCutterBreakTimeEnabled()); refresh(); }
        else if (slot == 15) new AnvilTextInput<Double>(plugin, "§8Timber • Mining Effort", "effort (0-100%)",
                MenuItems.number(plugin.treeCutterBreakTimePercent()), GameplayMath::percentage,
                (target, value) -> { plugin.setTreeCutterBreakTimePercent(value); new TreeBreakTimeMenu(plugin).open(target); }).open(player);
        else if (slot == 20) numberInput(player, "max-logs", "maximum tree size", 256, 2, 2048);
        else if (slot == 24) numberInput(player, "blocks-per-tick", "blocks per tick", 12, 1, 64);
    }

    private void numberInput(Player player, String setting, String label, int fallback, int min, int max) {
        new AnvilTextInput<Integer>(plugin, "§8Timber • " + label, label + " (" + min + "-" + max + ")",
                String.valueOf(plugin.getBoundedInt("tree-cutter." + setting, fallback, min, max)), text -> GameplayMath.integer(text, min, max),
                (target, value) -> { plugin.setConfigValue("tree-cutter." + setting, value); new TreeBreakTimeMenu(plugin).open(target); }).open(player);
    }
}
