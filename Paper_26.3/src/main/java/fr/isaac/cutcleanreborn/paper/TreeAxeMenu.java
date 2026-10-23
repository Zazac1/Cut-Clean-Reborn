package fr.isaac.cutcleanreborn.paper;

import java.util.Arrays;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Each axe has a visible colored status directly below its icon. */
final class TreeAxeMenu extends MenuSupport {
    private static final Material[] AXES = Arrays.stream(Material.values())
            .filter(material -> !material.name().startsWith("LEGACY_") && material.name().endsWith("_AXE"))
            .sorted(AutoEnchantCategory.TOOLS.comparator()).toArray(Material[]::new);
    private final int page;

    TreeAxeMenu(CutCleanRebornPlugin plugin) { this(plugin, 0); }
    TreeAxeMenu(CutCleanRebornPlugin plugin, int requestedPage) {
        super(plugin, 5, "§8Timber • Tools & Activation");
        page = Math.clamp(requestedPage, 0, Math.max(0, (AXES.length - 1) / 7));
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return new TreeCutterMenu(plugin); }

    @Override protected void refresh() {
        clearMenu();
        boolean active = plugin.treeCutterEnabled();
        header(Material.DIAMOND_AXE, "§6Tools & Activation",
                "§7Axes use the switches beneath their icons.",
                "§7The other options control when Timber starts.");
        inventory.setItem(10, MenuItems.toggle("Allow Non-Axe Tools", plugin.treeCutterHandEnabled(), active, "Timber",
                "§7Allow bare hands and items other than axes.",
                "§7A disabled axe is still excluded."));
        information(13, MenuItems.item(Material.PAPER, "§7Allowed Axes",
                java.util.List.of("§7Icon and switch below refer to the same axe.", "§7Green = active • Orange = paused • Gray = off")));
        inventory.setItem(16, MenuItems.toggle("Require Sneaking", plugin.treeCutterSneakOnly(), active, "Timber",
                "§7ON: crouch to activate whole-tree cutting.",
                "§7OFF: standing also activates it."));
        for (int i = 0; i < 7 && page * 7 + i < AXES.length; i++) {
            Material axe = AXES[page * 7 + i];
            boolean enabled = plugin.treeCutterAxeEnabled(axe);
            inventory.setItem(19 + i, MenuItems.stateItem(axe, "§6" + MenuItems.prettyName(axe),
                    java.util.List.of("§7Timber: " + MenuItems.effectiveState(enabled, active), "§eClick icon or switch to toggle."), enabled && active));
            inventory.setItem(28 + i, MenuItems.toggle(MenuItems.prettyName(axe), enabled, active, "Timber"));
        }
        footer("Timber");
        if (page > 0) inventory.setItem(38, MenuItems.item(Material.ARROW, "§ePrevious Axes", java.util.List.of()));
        if ((page + 1) * 7 < AXES.length) inventory.setItem(42, MenuItems.item(Material.ARROW, "§eNext Axes", java.util.List.of()));
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (slot == 10) { plugin.setTreeCutterHandEnabled(!plugin.treeCutterHandEnabled()); refresh(); return; }
        if (slot == 16) { plugin.setTreeCutterSneakOnly(!plugin.treeCutterSneakOnly()); refresh(); return; }
        if (slot == 38 && page > 0) { new TreeAxeMenu(plugin, page - 1).open(player); return; }
        if (slot == 42 && (page + 1) * 7 < AXES.length) { new TreeAxeMenu(plugin, page + 1).open(player); return; }
        int index = slot >= 19 && slot <= 25 ? slot - 19 : slot >= 28 && slot <= 34 ? slot - 28 : -1;
        if (index >= 0 && page * 7 + index < AXES.length) {
            Material axe = AXES[page * 7 + index];
            plugin.setTreeCutterAxeEnabled(axe, !plugin.treeCutterAxeEnabled(axe));
            refresh();
        }
    }
}
