package fr.isaac.cutcleanreborn.paper;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Paginated catalogue of vanilla-compatible enchantments for one item. */
final class AutoEnchantmentsListMenu extends MenuSupport {
    private final AutoEnchantCategory category;
    private final Material material;
    private final int itemPage;
    private final int page;
    private final List<Map.Entry<Enchantment, Integer>> enchantments;
    AutoEnchantmentsListMenu(CutCleanRebornPlugin plugin, AutoEnchantCategory category, Material material, int itemPage, int requestedPage) {
        super(plugin, 6, "§8CCR • " + MenuItems.prettyName(material) + " • Enchantments");
        this.category = category; this.material = material; this.itemPage = itemPage;
        enchantments = org.bukkit.Registry.ENCHANTMENT.stream()
                .filter(this::canEnchantMaterial)
                .sorted(Comparator.comparing(enchantment -> enchantment.getKey().toString()))
                .map(enchantment -> Map.entry(enchantment, plugin.autoEnchantments().enchantments(material).getOrDefault(enchantment, 0))).toList();
        int count = Math.max(1, (enchantments.size() + MobListMenu.PAGE_SIZE - 1) / MobListMenu.PAGE_SIZE);
        page = Math.clamp(requestedPage, 0, count - 1);
        render(count);
    }
    private void render(int pageCount) {
        int start = page * MobListMenu.PAGE_SIZE;
        for (int index = 0; index < MobListMenu.PAGE_SIZE && start + index < enchantments.size(); index++) {
            Map.Entry<Enchantment, Integer> entry = enchantments.get(start + index);
            boolean configured = entry.getValue() > 0;
            inventory.setItem(MobListMenu.MOB_SLOTS[index], MenuItems.item(Material.ENCHANTED_BOOK,
                    "§6" + MenuItems.prettyName(entry.getKey()) + (configured ? " " + roman(entry.getValue()) : ""),
                    List.of(configured ? "§aCurrent level: §e" + entry.getValue() : "§7Not configured.", "", "§eLeft click: Set level", configured ? "§cRight click: Remove" : "§8Right click: nothing to remove")));
        }
        inventory.setItem(40, MenuItems.item(Material.COMPASS, "§bSearch Enchantment", List.of("§7Enter an enchantment identifier.")));
        inventory.setItem(MobListMenu.BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to item configuration.")));
        if (page > 0) inventory.setItem(MobListMenu.PREVIOUS_PAGE_SLOT, MenuItems.item(Material.ARROW, "§ePrevious page", List.of()));
        if (page + 1 < pageCount) inventory.setItem(MobListMenu.NEXT_PAGE_SLOT, MenuItems.item(Material.ARROW, "§eNext page", List.of()));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == MobListMenu.BACK_SLOT) { new AutoEnchantItemMenu(plugin, category, material, itemPage).open(player); return; }
        if (slot == MobListMenu.PREVIOUS_PAGE_SLOT && page > 0) { new AutoEnchantmentsListMenu(plugin, category, material, itemPage, page - 1).open(player); return; }
        if (slot == MobListMenu.NEXT_PAGE_SLOT && (page + 1) * MobListMenu.PAGE_SIZE < enchantments.size()) { new AutoEnchantmentsListMenu(plugin, category, material, itemPage, page + 1).open(player); return; }
        if (slot == 40) { openSearch(player); return; }
        int start = page * MobListMenu.PAGE_SIZE;
        for (int index = 0; index < MobListMenu.MOB_SLOTS.length; index++) {
            if (slot != MobListMenu.MOB_SLOTS[index] || start + index >= enchantments.size()) continue;
            Map.Entry<Enchantment, Integer> entry = enchantments.get(start + index);
            if (click.isRightClick() && entry.getValue() > 0) {
                plugin.autoEnchantments().removeEnchantment(category, material, entry.getKey());
                new AutoEnchantmentsListMenu(plugin, category, material, itemPage, page).open(player);
            } else openLevelInput(player, entry.getKey(), entry.getValue() > 0 ? entry.getValue() : 1);
            return;
        }
    }
    private void openSearch(Player player) {
        new AnvilTextInput<Enchantment>(plugin, "§8CCR • Search Enchantment", "enchantment", "minecraft:sharpness",
                AutoEnchantService::findEnchantment, (target, enchantment) -> {
                    if (!canEnchantMaterial(enchantment)) {
                        target.sendMessage("§cCCR §8» §7This enchantment cannot be applied to " + MenuItems.prettyName(material) + ".");
                        new AutoEnchantmentsListMenu(plugin, category, material, itemPage, page).open(target);
                        return;
                    }
                    openLevelInput(target, enchantment, plugin.autoEnchantments().enchantments(material).getOrDefault(enchantment, 1));
                }).open(player);
    }
    private void openLevelInput(Player player, Enchantment enchantment, int initialValue) {
        new AnvilTextInput<Integer>(plugin, "§8CCR • Enchantment Level", "level", String.valueOf(initialValue),
                AutoEnchantmentsListMenu::parseLevel, (target, level) -> {
                    plugin.autoEnchantments().setEnchantment(category, material, enchantment, level);
                    new AutoEnchantmentsListMenu(plugin, category, material, itemPage, page).open(target);
                }).open(player);
    }
    private static Integer parseLevel(String input) { try { int level = Integer.parseInt(input.trim()); return level > 0 && level <= 255 ? level : null; } catch (NumberFormatException ignored) { return null; } }
    private boolean canEnchantMaterial(Enchantment enchantment) {
        return enchantment.canEnchantItem(new org.bukkit.inventory.ItemStack(material));
    }
    private static String roman(int number) {
        return switch (number) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X"; default -> String.valueOf(number); };
    }
}
