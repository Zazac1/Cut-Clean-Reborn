package fr.isaac.cutcleanreborn.paper;

import java.util.Comparator;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Paginated 7 × 3 grid of every configurable living entity. */
final class MobListMenu extends MenuSupport {
    static final int PAGE_SIZE = 21;
    static final int[] MOB_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    static final int PREVIOUS_PAGE_SLOT = 45;
    static final int BACK_SLOT = 49;
    static final int NEXT_PAGE_SLOT = 53;
    private final List<EntityType> mobs;
    private final int page;

    MobListMenu(CutCleanRebornPlugin plugin, int requestedPage) {
        super(plugin, 6, "§8CCR • Mob Customisation");
        mobs = MenuItems.configurableMobs().stream()
                .sorted(Comparator.comparingInt((EntityType mob) -> plugin.mobEditScore(mob) > 0 ? 0 : 1)
                        .thenComparingInt(MenuItems::mobPriority)
                        .thenComparing(Comparator.comparingInt(plugin::mobEditScore).reversed())
                        .thenComparing(Comparator.comparingLong(plugin::mobLastEdited).reversed())
                        .thenComparing(EntityType::name))
                .toList();
        int pageCount = Math.max(1, (mobs.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.clamp(requestedPage, 0, pageCount - 1);
        render(pageCount);
    }
    private void render(int pageCount) {
        inventory.clear();
        int start = page * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE && start + index < mobs.size(); index++) {
            EntityType mob = mobs.get(start + index);
            int edits = plugin.mobEditScore(mob);
            inventory.setItem(MOB_SLOTS[index], MenuItems.mobItem(mob, edits > 0
                    ? List.of("§6Configured edits: §e" + edits, "§7Click to customise.")
                    : List.of("§7Click to customise.")));
        }
        if (page > 0) inventory.setItem(PREVIOUS_PAGE_SLOT, MenuItems.item(Material.ARROW, "§ePrevious page",
                List.of("§7Page " + (page + 1) + " / " + pageCount)));
        if (page + 1 < pageCount) inventory.setItem(NEXT_PAGE_SLOT, MenuItems.item(Material.ARROW, "§eNext page",
                List.of("§7Page " + (page + 1) + " / " + pageCount)));
        inventory.setItem(BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to CCR Config.")));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == PREVIOUS_PAGE_SLOT && page > 0) new MobListMenu(plugin, page - 1).open(player);
        else if (slot == NEXT_PAGE_SLOT && (page + 1) * PAGE_SIZE < mobs.size()) new MobListMenu(plugin, page + 1).open(player);
        else if (slot == BACK_SLOT) new ConfigMainMenu(plugin).open(player);
        else for (int index = 0; index < MOB_SLOTS.length; index++) {
            if (slot == MOB_SLOTS[index] && page * PAGE_SIZE + index < mobs.size()) {
                new MobConfigurationMenu(plugin, new MobMenuContext(mobs.get(page * PAGE_SIZE + index), page)).open(player);
                return;
            }
        }
    }
}
