package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import java.util.function.Predicate;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Per-source output picker for Clean Stacks. */
final class CleanStackMaterialMenu extends MenuSupport {
    private static final int ALL_LOGS_SLOT = 46;
    private static final int ALL_STRIPPED_SLOT = 47;
    private static final int ALL_PLANKS_SLOT = 48;
    private static final int ALL_STONE_SLOT = 48;
    private final CleanStackService.Category category;
    private final List<Material> materials;
    private final int page;
    CleanStackMaterialMenu(CutCleanRebornPlugin plugin, CleanStackService.Category category, int requestedPage) {
        super(plugin, 6, "§8CCR • Clean Stacks • " + category.title());
        this.category = category; materials = plugin.cleanStacks().sources(category);
        int pages = Math.max(1, (materials.size() + MobListMenu.PAGE_SIZE - 1) / MobListMenu.PAGE_SIZE);
        page = Math.clamp(requestedPage, 0, pages - 1); render(pages);
    }
    private void render(int pages) {
        int start = page * MobListMenu.PAGE_SIZE;
        for (int i = 0; i < MobListMenu.PAGE_SIZE && start + i < materials.size(); i++) {
            Material source = materials.get(start + i), target = plugin.cleanStacks().target(category, source);
            inventory.setItem(MobListMenu.MOB_SLOTS[i], MenuItems.item(source, "§6" + MenuItems.prettyName(source),
                    List.of("§7Gives: §e" + MenuItems.prettyName(target), "", "§eLeft click: Choose output", "§cRight click: Reset default")));
        }
        inventory.setItem(MobListMenu.BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to Clean Stacks.")));
        if (page > 0) inventory.setItem(MobListMenu.PREVIOUS_PAGE_SLOT, MenuItems.item(Material.ARROW, "§ePrevious page", List.of()));
        if (page + 1 < pages) inventory.setItem(MobListMenu.NEXT_PAGE_SLOT, MenuItems.item(Material.ARROW, "§eNext page", List.of()));
        if (category == CleanStackService.Category.WOOD) {
            inventory.setItem(ALL_LOGS_SLOT, MenuItems.stateItem(Material.OAK_LOG, "§6Set all logs & stems",
                    List.of("§7Change every non-stripped log", "§7and Nether stem at once.", "", "§eClick to choose output"), true));
            inventory.setItem(ALL_STRIPPED_SLOT, MenuItems.stateItem(Material.STRIPPED_OAK_LOG, "§6Set all stripped logs & stems",
                    List.of("§7Change every stripped log", "§7and Nether stem at once.", "", "§eClick to choose output"), true));
            inventory.setItem(ALL_PLANKS_SLOT, MenuItems.stateItem(Material.OAK_PLANKS, "§6Set all planks",
                    List.of("§7Change every plank type at once.", "", "§eClick to choose output"), true));
        } else inventory.setItem(ALL_STONE_SLOT, MenuItems.stateItem(Material.STONE, "§6Set all stone",
                List.of("§7Change every listed stone block at once.", "", "§eClick to choose output"), true));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == MobListMenu.BACK_SLOT) { new CleanStackMenu(plugin).open(player); return; }
        if (slot == MobListMenu.PREVIOUS_PAGE_SLOT && page > 0) { new CleanStackMaterialMenu(plugin, category, page - 1).open(player); return; }
        if (slot == MobListMenu.NEXT_PAGE_SLOT && (page + 1) * MobListMenu.PAGE_SIZE < materials.size()) { new CleanStackMaterialMenu(plugin, category, page + 1).open(player); return; }
        if (category == CleanStackService.Category.WOOD && slot == ALL_LOGS_SLOT) {
            chooseBulkOutput(player, "all logs and stems", Material.OAK_LOG, material -> !material.name().startsWith("STRIPPED_") && !material.name().endsWith("_PLANKS")); return;
        }
        if (category == CleanStackService.Category.WOOD && slot == ALL_STRIPPED_SLOT) {
            chooseBulkOutput(player, "all stripped logs and stems", Material.STRIPPED_OAK_LOG, material -> material.name().startsWith("STRIPPED_")); return;
        }
        if (category == CleanStackService.Category.WOOD && slot == ALL_PLANKS_SLOT) {
            chooseBulkOutput(player, "all planks", Material.OAK_PLANKS, material -> material.name().endsWith("_PLANKS")); return;
        }
        if (category == CleanStackService.Category.STONE && slot == ALL_STONE_SLOT) {
            chooseBulkOutput(player, "all stone", Material.STONE, material -> true); return;
        }
        int start = page * MobListMenu.PAGE_SIZE;
        for (int i = 0; i < MobListMenu.MOB_SLOTS.length; i++) if (slot == MobListMenu.MOB_SLOTS[i] && start + i < materials.size()) {
            Material source = materials.get(start + i);
            if (click.isRightClick()) { plugin.cleanStacks().resetTarget(category, source); new CleanStackMaterialMenu(plugin, category, page).open(player); }
            else new AnvilTextInput<Material>(plugin, "§8CCR • Clean Stack Output", "item", plugin.cleanStacks().target(category, source).getKey().toString(), MenuItems::findItem,
                    (target, item) -> { plugin.cleanStacks().setTarget(category, source, item); new CleanStackMaterialMenu(plugin, category, page).open(target); }).open(player);
            return;
        }
    }
    private void chooseBulkOutput(Player player, String label, Material initial, Predicate<Material> matches) {
        new AnvilTextInput<Material>(plugin, "§8CCR • Clean Stack Output", "item", initial.getKey().toString(), MenuItems::findItem,
                (target, item) -> {
                    materials.stream().filter(matches).forEach(source -> plugin.cleanStacks().setTarget(category, source, item));
                    new CleanStackMaterialMenu(plugin, category, page).open(target);
                }).open(player);
    }
}
