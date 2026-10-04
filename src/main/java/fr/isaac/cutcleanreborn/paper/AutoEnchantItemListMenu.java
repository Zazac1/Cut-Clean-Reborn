package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import java.util.Arrays;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Paginated catalogue of every armor or tool available on this Paper server. */
final class AutoEnchantItemListMenu extends MenuSupport {
    private final AutoEnchantCategory category;
    private final List<Material> items;
    private final int page;
    AutoEnchantItemListMenu(CutCleanRebornPlugin plugin, AutoEnchantCategory category, int requestedPage) {
        super(plugin, 6, "§8CCR • " + category.title());
        this.category = category;
        items = Arrays.stream(Material.values()).filter(category::matches).sorted(category.comparator()).toList();
        int count = Math.max(1, (items.size() + MobListMenu.PAGE_SIZE - 1) / MobListMenu.PAGE_SIZE);
        page = Math.clamp(requestedPage, 0, count - 1);
        render(count);
    }
    private void render(int pageCount) {
        int start = page * MobListMenu.PAGE_SIZE;
        for (int index = 0; index < MobListMenu.PAGE_SIZE && start + index < items.size(); index++) {
            Material material = items.get(start + index);
            boolean configured = plugin.autoEnchantments().isConfigured(material);
            inventory.setItem(MobListMenu.MOB_SLOTS[index], MenuItems.item(material, "§6" + MenuItems.prettyName(material),
                    List.of(configured ? "§aConfigured enchantments: §e" + plugin.autoEnchantments().enchantments(material).size() : "§7Not configured yet.", "", "§eLeft click: Configure", configured ? "§cRight click: Remove configuration" : "§8Right click: nothing to remove")));
        }
        inventory.setItem(40, MenuItems.item(Material.COMPASS, "§bSearch Item", List.of("§7Enter an item identifier.")));
        inventory.setItem(MobListMenu.BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to categories.")));
        if (page > 0) inventory.setItem(MobListMenu.PREVIOUS_PAGE_SLOT, MenuItems.item(Material.ARROW, "§ePrevious page", List.of()));
        if (page + 1 < pageCount) inventory.setItem(MobListMenu.NEXT_PAGE_SLOT, MenuItems.item(Material.ARROW, "§eNext page", List.of()));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == MobListMenu.BACK_SLOT) { new AutoEnchantCategoryMenu(plugin).open(player); return; }
        if (slot == MobListMenu.PREVIOUS_PAGE_SLOT && page > 0) { new AutoEnchantItemListMenu(plugin, category, page - 1).open(player); return; }
        if (slot == MobListMenu.NEXT_PAGE_SLOT && (page + 1) * MobListMenu.PAGE_SIZE < items.size()) { new AutoEnchantItemListMenu(plugin, category, page + 1).open(player); return; }
        if (slot == 40) {
            new AnvilTextInput<Material>(plugin, "§8CCR • Add " + category.title(), "item", "minecraft:diamond_sword", MenuItems::findItem, (target, material) -> {
                if (!category.matches(material)) { target.sendMessage("§cThis item does not belong to " + category.title() + "."); new AutoEnchantItemListMenu(plugin, category, page).open(target); return; }
                plugin.autoEnchantments().addItem(category, material);
                new AutoEnchantItemMenu(plugin, category, material, page).open(target);
            }).open(player);
            return;
        }
        int start = page * MobListMenu.PAGE_SIZE;
        for (int index = 0; index < MobListMenu.MOB_SLOTS.length; index++) {
            if (slot != MobListMenu.MOB_SLOTS[index] || start + index >= items.size()) continue;
            Material material = items.get(start + index);
            if (click.isRightClick() && plugin.autoEnchantments().isConfigured(material)) {
                plugin.autoEnchantments().removeItem(category, material);
                new AutoEnchantItemListMenu(plugin, category, page).open(player);
            } else new AutoEnchantItemMenu(plugin, category, material, page).open(player);
            return;
        }
    }
}
