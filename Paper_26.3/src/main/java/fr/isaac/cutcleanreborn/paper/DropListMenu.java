package fr.isaac.cutcleanreborn.paper;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Drop collection for a mob, with independent pagination from the mob list. */
final class DropListMenu extends MenuSupport {
    static final int CENTER_SLOT = 22;
    static final int ADD_DROP_SLOT = 40;
    static final int LOOTING_VANILLA_SLOT = 41;
    static final int LOOTING_CUSTOM_SLOT = 42;
    static final int PREVIOUS_PAGE_SLOT = 45;
    static final int BACK_SLOT = 49;
    static final int RESET_TO_VANILLA_SLOT = 50;
    static final int NEXT_PAGE_SLOT = 53;
    private static final int[] DROP_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private final MobMenuContext context;
    private final int page;
    private final List<EditableDrop> drops;

    DropListMenu(CutCleanRebornPlugin plugin, MobMenuContext context) { this(plugin, context, 0); }
    DropListMenu(CutCleanRebornPlugin plugin, MobMenuContext context, int requestedPage) {
        super(plugin, 6, "§8CCR • " + MenuItems.pretty(context.mob()) + " • Drops");
        this.context = context;
        this.drops = listedDrops();
        int count = Math.max(1, (drops.size() + DROP_SLOTS.length - 1) / DROP_SLOTS.length);
        this.page = Math.clamp(requestedPage, 0, count - 1);
        render(count);
    }
    private void render(int pageCount) {
        inventory.clear();
        inventory.setItem(CENTER_SLOT, MenuItems.item(MenuItems.mobIcon(context.mob()), "§6" + MenuItems.pretty(context.mob()),
                List.of("§7Configured drops surround this mob.")));
        int start = page * DROP_SLOTS.length;
        for (int index = 0; index < DROP_SLOTS.length && start + index < drops.size(); index++) {
            EditableDrop entry = drops.get(start + index);
            CutCleanRebornPlugin.MobDrop drop = entry.drop();
            List<String> lore = new ArrayList<>(List.of(entry.vanilla() ? "§9Vanilla drop" : "§aCustom drop", "§7Amount: §e" + drop.minimum() + "-" + drop.maximum(),
                    "§7Drop chance: §e" + MenuItems.number(drop.chance()) + "%"));
            if (entry.lootingThree() != null) lore.add("§bLooting III vanilla: §e" + entry.lootingThree().minimum() + "-" + entry.lootingThree().maximum()
                    + " §7(" + MenuItems.number(entry.lootingThree().chance()) + "%)");
            lore.add(""); lore.add("§eLeft click: Edit"); lore.add("§cRight click: Remove");
            inventory.setItem(DROP_SLOTS[index], MenuItems.item(drop.material(), "§6" + MenuItems.prettyName(drop.material()), lore));
        }
        inventory.setItem(ADD_DROP_SLOT, MenuItems.item(Material.PAPER, "§aAdd Drop", List.of("§7Add a new item drop.")));
        lootingToggle(LOOTING_VANILLA_SLOT, plugin.mobLootingVanillaEnabled(), "Looting on edited vanilla drops");
        lootingToggle(LOOTING_CUSTOM_SLOT, plugin.mobLootingCustomEnabled(), "Looting on custom drops");
        inventory.setItem(BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to " + MenuItems.pretty(context.mob()) + ".")));
        inventory.setItem(RESET_TO_VANILLA_SLOT, MenuItems.item(Material.CLOCK, "§eReset to Vanilla Values",
                List.of("§7Remove overrides and added drops for", "§7this mob and restore Minecraft values.")));
        if (page > 0) inventory.setItem(PREVIOUS_PAGE_SLOT, MenuItems.item(Material.ARROW, "§ePrevious page",
                List.of("§7Page " + (page + 1) + " / " + pageCount)));
        if (page + 1 < pageCount) inventory.setItem(NEXT_PAGE_SLOT, MenuItems.item(Material.ARROW, "§eNext page",
                List.of("§7Page " + (page + 1) + " / " + pageCount)));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == BACK_SLOT || slot == CENTER_SLOT) { new MobConfigurationMenu(plugin, context).open(player); return; }
        if (slot == RESET_TO_VANILLA_SLOT) {
            plugin.saveVanillaDropOverrides(context.mob(), List.of());
            plugin.saveMobDrops(context.mob(), List.of());
            new DropListMenu(plugin, context, page).open(player);
            return;
        }
        if (slot == PREVIOUS_PAGE_SLOT && page > 0) { new DropListMenu(plugin, context, page - 1).open(player); return; }
        if (slot == NEXT_PAGE_SLOT && (page + 1) * DROP_SLOTS.length < drops.size()) { new DropListMenu(plugin, context, page + 1).open(player); return; }
        if (slot == ADD_DROP_SLOT) {
            new AnvilTextInput<Material>(plugin, "§8CCR • Add Drop", "item", "minecraft:diamond", MenuItems::findItem, (target, material) -> {
                List<CutCleanRebornPlugin.MobDrop> updated = plugin.getMobDrops(context.mob());
                updated.add(new CutCleanRebornPlugin.MobDrop(material, 1, 1, 100.0D));
                plugin.saveMobDrops(context.mob(), updated);
                new DropListMenu(plugin, context, page).open(target);
            }).open(player);
            return;
        }
        if (slot == LOOTING_VANILLA_SLOT) {
            plugin.setMobLootingVanillaEnabled(!plugin.mobLootingVanillaEnabled());
            new DropListMenu(plugin, context, page).open(player); return;
        }
        if (slot == LOOTING_CUSTOM_SLOT) {
            plugin.setMobLootingCustomEnabled(!plugin.mobLootingCustomEnabled());
            new DropListMenu(plugin, context, page).open(player); return;
        }
        int start = page * DROP_SLOTS.length;
        for (int index = 0; index < DROP_SLOTS.length; index++) {
            if (slot != DROP_SLOTS[index] || start + index >= drops.size()) continue;
            int dropIndex = start + index;
            EditableDrop entry = drops.get(dropIndex);
            if (click.isRightClick()) {
                if (entry.vanilla()) disableVanillaDrop(entry.drop());
                else {
                    List<CutCleanRebornPlugin.MobDrop> updated = plugin.getMobDrops(context.mob());
                    int customIndex = customDropIndex(dropIndex);
                    if (customIndex < updated.size()) updated.remove(customIndex);
                    plugin.saveMobDrops(context.mob(), updated);
                }
                new DropListMenu(plugin, context, page).open(player);
            } else new DropEditorMenu(plugin, context, page, entry, entry.vanilla() ? -1 : customDropIndex(dropIndex)).open(player);
            return;
        }
    }

    private List<EditableDrop> listedDrops() {
        List<EditableDrop> result = new ArrayList<>();
        Map<Material, CutCleanRebornPlugin.MobDrop> overrides = new HashMap<>();
        Map<Material, CutCleanRebornPlugin.MobDrop> lootingThree = new HashMap<>();
        for (CutCleanRebornPlugin.MobDrop drop : plugin.getVanillaDropOverrides(context.mob())) overrides.put(drop.material(), drop);
        for (CutCleanRebornPlugin.MobDrop drop : VanillaDropCatalog.previewLooting(context.mob(), 3)) lootingThree.put(drop.material(), drop);
        for (CutCleanRebornPlugin.MobDrop vanilla : VanillaDropCatalog.preview(context.mob())) {
            CutCleanRebornPlugin.MobDrop override = overrides.remove(vanilla.material());
            result.add(new EditableDrop(override == null ? vanilla : override, true, lootingThree.get(vanilla.material())));
        }
        overrides.values().stream().sorted(java.util.Comparator.comparing(drop -> drop.material().getKey().toString()))
                .forEach(override -> result.add(new EditableDrop(override, true, null)));
        for (CutCleanRebornPlugin.MobDrop custom : plugin.getMobDrops(context.mob())) result.add(new EditableDrop(custom, false, null));
        return result;
    }
    private int customDropIndex(int listIndex) {
        int index = 0;
        for (int i = 0; i < listIndex; i++) if (!drops.get(i).vanilla()) index++;
        return index;
    }
    private void disableVanillaDrop(CutCleanRebornPlugin.MobDrop drop) {
        List<CutCleanRebornPlugin.MobDrop> overrides = new ArrayList<>(plugin.getVanillaDropOverrides(context.mob()));
        overrides.removeIf(existing -> existing.material() == drop.material());
        overrides.add(new CutCleanRebornPlugin.MobDrop(drop.material(), 0, 0, 0.0D));
        plugin.saveVanillaDropOverrides(context.mob(), overrides);
    }
    private void lootingToggle(int slot, boolean enabled, String label) {
        inventory.setItem(slot, MenuItems.stateItem(Material.BOOK,
                (enabled ? "§a" : "§c") + label + ": " + (enabled ? "ON" : "OFF"),
                List.of("§7Applies the killer's Looting", "§7to this configured drop type.", "", enabled ? "§eClick to disable" : "§eClick to enable"), enabled));
    }
}

record EditableDrop(CutCleanRebornPlugin.MobDrop drop, boolean vanilla, CutCleanRebornPlugin.MobDrop lootingThree) { }
