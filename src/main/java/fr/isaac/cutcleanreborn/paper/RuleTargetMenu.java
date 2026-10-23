package fr.isaac.cutcleanreborn.paper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Pick one target mode, rather than presenting two fields that silently overwrite each other. */
final class RuleTargetMenu extends MenuSupport {
    private final boolean armor;
    private final String current;
    private final BiConsumer<Player, String> selected;
    private final Supplier<MenuSupport> back;
    private final Map<Integer, String> choices = new LinkedHashMap<>();

    RuleTargetMenu(CutCleanRebornPlugin plugin, boolean armor, String current, String title,
            BiConsumer<Player, String> selected, Supplier<MenuSupport> back) {
        super(plugin, 5, "§8Stuff Rules • " + title);
        this.armor = armor;
        this.current = current;
        this.selected = selected;
        this.back = back;
        refresh();
    }

    @Override protected MenuSupport parentMenu() { return back.get(); }

    @Override protected void refresh() {
        clearMenu();
        choices.clear();
        header(Material.COMPASS, "§6Choose One Target",
                "§7A preset or a specific item list: choose one.",
                "§7Your choice replaces the previous target.");
        Map<String, Material> presets = new LinkedHashMap<>();
        if (armor) {
            presets.put("ARMOR", Material.IRON_CHESTPLATE);
            for (String family : List.of("LEATHER", "CHAINMAIL", "COPPER", "IRON", "GOLDEN", "DIAMOND", "NETHERITE", "TURTLE")) {
                Material icon = Material.matchMaterial(family + "_CHESTPLATE");
                if (icon == null) icon = Material.matchMaterial(family + "_HELMET");
                if (icon != null && icon.isItem()) presets.put(family, icon);
            }
        } else {
            presets.put("SWORDS", Material.IRON_SWORD);
            presets.put("TOOLS", Material.DIAMOND_PICKAXE);
            presets.put("ARMOR", Material.IRON_CHESTPLATE);
            presets.put("ALL", Material.CHEST);
        }
        int index = 0;
        int[] itemSlots = armor ? MobListMenu.MOB_SLOTS : new int[] {10, 12, 14, 16};
        for (var entry : presets.entrySet()) {
            int slot = itemSlots[index++];
            choices.put(slot, entry.getKey());
            boolean chosen = current.equals(entry.getKey());
            inventory.setItem(slot, MenuItems.stateItem(entry.getValue(), "§6" + label(entry.getKey()) + (chosen ? " §a✓" : ""),
                    List.of(armor ? "§7Includes matching equipped armor pieces." : "§7Includes every item in this category.",
                            chosen ? "§aCurrently selected." : "§eClick to select this target."), chosen));
        }
        inventory.setItem(31, MenuItems.item(Material.NAME_TAG, "§bSpecific Item(s)",
                List.of("§7Use one item ID or a comma-separated list.",
                        armor ? "§7Example: diamond_helmet,diamond_boots" : "§7Example: diamond_sword,bow",
                        "§7Only the listed items are targeted.", "", "§eClick to enter item IDs.")));
        footer("Edit Rule");
        information(40, MenuItems.item(Material.PAPER, "§7Current: §f" + label(current),
                List.of("§7Nothing changes until you select a target.", "§7Escape: return without changing it.")));
    }

    @Override void handleClick(Player player, int slot, ClickType click) {
        if (handleNavigation(player, slot)) return;
        if (choices.containsKey(slot)) selected.accept(player, choices.get(slot));
        else if (slot == 31) {
            String initial = current.contains(":") || current.contains(",") ? current
                    : armor ? "minecraft:diamond_helmet" : "minecraft:diamond_sword";
            new AnvilTextInput<String>(plugin, "§8Stuff Rules • Specific Items", "comma-separated item IDs", initial,
                    armor ? StuffRulesService::normalizeArmorItems : StuffRulesService::normalizeItemItems, selected).open(player);
        }
    }

    static Material armorIcon(String target) {
        Material family = Material.matchMaterial(target + "_CHESTPLATE");
        if (family != null && family.isItem()) return family;
        if (!target.contains(",")) {
            Material direct = MenuItems.findItem(target);
            if (direct != null && AutoEnchantCategory.ARMOR.matches(direct)) return direct;
        }
        return Material.IRON_CHESTPLATE;
    }

    static String label(String target) {
        return switch (target) {
            case "ARMOR" -> "All Armor";
            case "TOOLS" -> "All Tools & Weapons";
            case "SWORDS" -> "All Swords";
            case "ALL" -> "All Items";
            default -> {
                if (target.contains(",")) yield target.split(",").length + " Specific Items";
                if (target.contains(":")) {
                    Material material = MenuItems.findItem(target);
                    yield material == null ? target : MenuItems.prettyName(material);
                }
                yield target.charAt(0) + target.substring(1).toLowerCase(Locale.ROOT) + " Armor";
            }
        };
    }
}
