package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Per-material axe eligibility for tree cutting. */
final class TreeAxeMenu extends MenuSupport {
    private static final Material[] AXES = {Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE};
    private static final int[] SLOTS = {10, 11, 12, 14, 15, 16};
    private static final int BACK_SLOT = 22;
    TreeAxeMenu(CutCleanRebornPlugin plugin) { super(plugin, 3, "§8CCR • Allowed Axes"); render(); }
    private void render() {
        for (int i = 0; i < AXES.length; i++) {
            Material axe = AXES[i]; boolean enabled = plugin.treeCutterAxeEnabled(axe);
            inventory.setItem(SLOTS[i], MenuItems.stateItem(axe, (enabled ? "§a" : "§c") + MenuItems.prettyName(axe) + ": " + (enabled ? "ON" : "OFF"),
                    List.of("§7Tree cutter is " + (enabled ? "§aallowed" : "§cblocked") + "§7 with this axe.", "", enabled ? "§eClick to disable" : "§eClick to enable"), enabled));
        }
        inventory.setItem(BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to Timber.")));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == BACK_SLOT) { new TreeCutterMenu(plugin).open(player); return; }
        for (int i = 0; i < AXES.length; i++) if (slot == SLOTS[i]) { plugin.setTreeCutterAxeEnabled(AXES[i], !plugin.treeCutterAxeEnabled(AXES[i])); render(); return; }
    }
}
