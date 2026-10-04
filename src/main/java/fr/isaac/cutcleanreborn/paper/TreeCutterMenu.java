package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Settings for whole-tree cutting and the vanilla apple leaf roll. */
final class TreeCutterMenu extends MenuSupport {
    private static final int ENABLE_SLOT = 10, HAND_SLOT = 12, AXES_SLOT = 14, APPLE_SLOT = 16;
    private static final int LEAVES_SLOT = 19, BREAK_TIME_SLOT = 21, RESET_SLOT = 23, SNEAK_SLOT = 25, BACK_SLOT = 31;
    TreeCutterMenu(CutCleanRebornPlugin plugin) {
        super(plugin, 4, "§8CCR • Timber"); render();
    }
    private void render() {
        boolean enabled = plugin.treeCutterEnabled(), hand = plugin.treeCutterHandEnabled(), customApple = plugin.hasCustomAppleChance();
        inventory.setItem(ENABLE_SLOT, toggle(Material.OAK_LOG, "Timber", enabled,
                "§7Cut the entire natural tree when", "§7a permitted tool breaks a log."));
        inventory.setItem(HAND_SLOT, toggle(Material.STICK, "No Axe Required", hand,
                "§7Allow whole-tree cutting with any", "§7item that is not an axe."));
        inventory.setItem(AXES_SLOT, MenuItems.item(Material.DIAMOND_AXE, "§6Allowed Axes", List.of("§7Choose each axe material.", "", "§eClick to open")));
        inventory.setItem(APPLE_SLOT, MenuItems.item(Material.APPLE, "§6Leaf Apple Chance", List.of(
                "§7Current: §e" + MenuItems.number(plugin.treeAppleChance()) + "%", customApple ? "§7Custom value." : "§7Vanilla value: §e0.5%", "", "§eClick to edit")));
        boolean leaves = plugin.treeCutterLeavesEnabled();
        inventory.setItem(LEAVES_SLOT, toggle(Material.OAK_LEAVES, "Break Leaves", leaves,
                "§7Break nearby leaves together with", "§7the tree and drop their loot."));
        inventory.setItem(BREAK_TIME_SLOT, MenuItems.item(Material.CLOCK, "§6Tree Break Time",
                List.of(plugin.treeCutterBreakTimeEnabled() ? "§7Status: §aEnabled" : "§7Status: §cDisabled",
                        "§7Adds §e" + MenuItems.number(plugin.treeCutterBreakTimePercent()) + "% §7of the other logs' time", "§7to the first log you break.", "", "§eClick to configure")));
        boolean sneakOnly = plugin.treeCutterSneakOnly();
        inventory.setItem(SNEAK_SLOT, toggle(Material.LEATHER_BOOTS, "Sneak Required", sneakOnly,
                "§7Only activate Timber while", "§7the player is crouching."));
        inventory.setItem(RESET_SLOT, MenuItems.item(Material.CLOCK, "§eReset Apple Chance", List.of("§7Restore Minecraft's vanilla 0.5% chance.")));
        inventory.setItem(BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to CCR Config.")));
    }
    private static org.bukkit.inventory.ItemStack toggle(Material icon, String label, boolean enabled, String... lines) {
        List<String> lore = new java.util.ArrayList<>(List.of(lines)); lore.add(""); lore.add(enabled ? "§eClick to disable" : "§eClick to enable");
        return MenuItems.stateItem(icon, (enabled ? "§a" : "§c") + label + ": " + (enabled ? "ON" : "OFF"), lore, enabled);
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == ENABLE_SLOT) { plugin.setTreeCutterEnabled(!plugin.treeCutterEnabled()); render(); }
        else if (slot == HAND_SLOT) { plugin.setTreeCutterHandEnabled(!plugin.treeCutterHandEnabled()); render(); }
        else if (slot == AXES_SLOT) new TreeAxeMenu(plugin).open(player);
        else if (slot == APPLE_SLOT) new AnvilTextInput<Double>(plugin, "§8CCR • Apple Chance", "chance (%)", MenuItems.number(plugin.treeAppleChance()),
                TreeCutterMenu::chance, (target, value) -> { plugin.setTreeAppleChance(value); new TreeCutterMenu(plugin).open(target); }).open(player);
        else if (slot == RESET_SLOT) { plugin.resetTreeAppleChance(); render(); }
        else if (slot == LEAVES_SLOT) { plugin.setTreeCutterLeavesEnabled(!plugin.treeCutterLeavesEnabled()); render(); }
        else if (slot == BREAK_TIME_SLOT) new TreeBreakTimeMenu(plugin).open(player);
        else if (slot == SNEAK_SLOT) { plugin.setTreeCutterSneakOnly(!plugin.treeCutterSneakOnly()); render(); }
        else if (slot == BACK_SLOT) new ConfigMainMenu(plugin).open(player);
    }
    private static Double chance(String input) { try { double value = Double.parseDouble(input.replace("%", "").trim()); return Double.isFinite(value) && value >= 0 && value <= 100 ? value : null; } catch (NumberFormatException ignored) { return null; } }
}
