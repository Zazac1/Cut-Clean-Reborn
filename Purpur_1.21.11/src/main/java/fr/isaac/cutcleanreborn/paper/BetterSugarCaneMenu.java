package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** One global switch, then one clearly separated settings page per break cause. */
final class BetterSugarCaneMenu extends MenuSupport {
    BetterSugarCaneMenu(CutCleanRebornPlugin plugin) {
        super(plugin, 4, "§8CCR • Better Sugar Cane");
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return new ConfigMainMenu(plugin); }

    @Override protected void refresh() {
        clearMenu();
        BetterSugarCaneService service = plugin.betterSugarCane();
        header(Material.SUGAR_CANE, "§6Better Sugar Cane",
                "§7Choose which kind of break receives a bonus.",
                "§7Player-placed cane keeps its normal drop.");
        inventory.setItem(13, MenuItems.toggle("Enable Sugar Cane Bonuses", service.enabled(), true, "",
                "§7Master switch for both pages below."));
        category(20, Material.IRON_HOE, "Player Break", BetterSugarCaneService.Cause.PLAYER,
                "§7A player directly breaks a cane block.");
        category(24, Material.DIRT, "Support Break", BetterSugarCaneService.Cause.SUPPORT,
                "§7Cane falls after losing its support.");
        footer("CCR Config");
        inventory.setItem(31, MenuItems.item(Material.TNT, "§cReset Sugar Cane Settings",
                List.of("§7Restore switches, chances and quantities.", "§7You will be asked to confirm.")));
    }

    private void category(int slot, Material icon, String title, BetterSugarCaneService.Cause cause, String description) {
        BetterSugarCaneService service = plugin.betterSugarCane();
        inventory.setItem(slot, MenuItems.item(icon, "§b" + title,
                List.of(description, "", "§7Status: " + MenuItems.effectiveState(service.causeEnabled(cause), service.enabled()),
                        "§7Bonus chance: §e" + MenuItems.number(service.chance(cause)) + "%",
                        "§7Final quantity: §e" + service.minimum(cause) + "-" + service.maximum(cause),
                        "", "§eClick to configure this break type.")));
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (slot == 13) {
            plugin.betterSugarCane().setEnabled(!plugin.betterSugarCane().enabled());
            refresh();
        } else if (slot == 20) new SugarCaneCauseMenu(plugin, BetterSugarCaneService.Cause.PLAYER).open(player);
        else if (slot == 24) new SugarCaneCauseMenu(plugin, BetterSugarCaneService.Cause.SUPPORT).open(player);
        else if (slot == 31) confirm(player, "Reset all sugar cane settings?", plugin.betterSugarCane()::reset,
                target -> new BetterSugarCaneMenu(plugin).open(target));
    }
}
