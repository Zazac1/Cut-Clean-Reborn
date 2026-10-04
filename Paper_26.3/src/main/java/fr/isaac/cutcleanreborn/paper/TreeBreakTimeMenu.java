package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Controls the additional delay paid for all logs cut by Timber. */
final class TreeBreakTimeMenu extends MenuSupport {
    private static final int ENABLE_SLOT = 11, PERCENT_SLOT = 15, BACK_SLOT = 22;
    TreeBreakTimeMenu(CutCleanRebornPlugin plugin) { super(plugin, 3, "§8CCR • Timber Break Time"); render(); }
    private void render() {
        boolean enabled = plugin.treeCutterBreakTimeEnabled();
        inventory.setItem(ENABLE_SLOT, MenuItems.stateItem(Material.CLOCK,
                enabled ? "§aBreak-time scaling: ON" : "§cBreak-time scaling: OFF",
                List.of("§7Adds the other logs' time to the", "§7first log you break.", "", enabled ? "§eClick to disable" : "§eClick to enable"), enabled));
        inventory.setItem(PERCENT_SLOT, MenuItems.item(Material.CLOCK, "§6Additional Break Time",
                List.of("§7Current value: §e" + MenuItems.number(plugin.treeCutterBreakTimePercent()) + "%", "§7100% = full time of every other", "§7log added to the first one; 0% = instant.", "", "§eClick to edit")));
        inventory.setItem(BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to Timber.")));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == ENABLE_SLOT) { plugin.setTreeCutterBreakTimeEnabled(!plugin.treeCutterBreakTimeEnabled()); render(); }
        else if (slot == PERCENT_SLOT) new AnvilTextInput<Double>(plugin, "§8CCR • Timber Time", "percent", MenuItems.number(plugin.treeCutterBreakTimePercent()),
                TreeBreakTimeMenu::percent, (target, value) -> { plugin.setTreeCutterBreakTimePercent(value); new TreeBreakTimeMenu(plugin).open(target); }).open(player);
        else if (slot == BACK_SLOT) new TreeCutterMenu(plugin).open(player);
    }
    private static Double percent(String input) { try { double value = Double.parseDouble(input.replace("%", "").trim()); return Double.isFinite(value) && value >= 0 && value <= 100 ? value : null; } catch (NumberFormatException ignored) { return null; } }
}
