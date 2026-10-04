package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

final class StuffRulesMenu extends MenuSupport {
    StuffRulesMenu(CutCleanRebornPlugin plugin) { super(plugin, 4, "§8CCR • Stuff Rules"); render(); }
    void render() { StuffRulesService service = plugin.stuffRules(); inventory.setItem(10, MenuItems.stateItem(Material.CHEST, "§6Stuff Rules: " + (service.enabled() ? "§aON" : "§cOFF"), List.of("§7Disabled means vanilla behavior."), service.enabled())); inventory.setItem(12, MenuItems.item(Material.DIAMOND_CHESTPLATE, "§6Armor Limits", List.of("§7" + service.armorRules().size() + " configured rule(s).", "§7Limit equipped armor only.", "", "§eClick to open"))); inventory.setItem(14, MenuItems.item(Material.ENCHANTING_TABLE, "§6Enchantment Caps", List.of("§7" + service.enchantmentCaps().size() + " configured rule(s).", "§7Limit only the named enchantment.", "", "§eClick to open"))); inventory.setItem(16, MenuItems.stateItem(Material.MACE, "§6Unique Mace: " + (service.maceEnabled() ? "§aON" : "§cOFF"), List.of("§7One CCR-crafted mace server-wide.", service.maceExists() ? "§eA mace currently exists." : "§aA mace may be crafted."), service.maceEnabled())); inventory.setItem(31, MenuItems.item(Material.BARRIER, "§cBack", List.of())); }
    @Override void handleClick(Player player, int slot, ClickType click) { StuffRulesService service = plugin.stuffRules(); if (slot == 10) { service.setEnabled(!service.enabled()); render(); } else if (slot == 12) new ArmorRuleListMenu(plugin, 0).open(player); else if (slot == 14) new EnchantmentCapListMenu(plugin, 0).open(player); else if (slot == 16) { service.setMaceEnabled(!service.maceEnabled()); render(); } else if (slot == 31) new ConfigMainMenu(plugin).open(player); }
}
