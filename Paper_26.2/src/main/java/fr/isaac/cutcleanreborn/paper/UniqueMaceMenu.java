package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

final class UniqueMaceMenu extends MenuSupport {
    UniqueMaceMenu(CutCleanRebornPlugin plugin) {
        super(plugin, 3, "§8Stuff Rules • Unique Mace");
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return new StuffRulesMenu(plugin); }

    @Override protected void refresh() {
        clearMenu();
        StuffRulesService service = plugin.stuffRules();
        header(Material.MACE, "§6One CCR-Crafted Mace",
                "§7Only one mace crafted under this rule may exist.", "§7Existing ordinary maces are not removed.");
        inventory.setItem(11, MenuItems.toggle("Enable Unique Mace", service.maceEnabled(), service.enabled(), "Stuff Rules",
                "§7The first successful craft reserves the mace.", "§7Its destruction or breakage releases a new craft."));
        information(15, MenuItems.item(service.maceExists() ? Material.RED_CONCRETE : Material.LIME_CONCRETE,
                service.maceExists() ? "§cRegistered Mace: YES" : "§aRegistered Mace: NONE",
                List.of(service.maceExists() ? "§7A tracked mace currently reserves the craft." : "§7No tracked mace currently reserves the craft.",
                        "§7Pickup and chunk unloads do not release it.",
                        service.enabled() && service.maceEnabled() ? "§7The unique-craft restriction is active." : "§eRule inactive: ordinary crafting is allowed.")));
        footer("Stuff Rules");
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (slot == 11) { plugin.stuffRules().setMaceEnabled(!plugin.stuffRules().maceEnabled()); refresh(); }
    }
}
