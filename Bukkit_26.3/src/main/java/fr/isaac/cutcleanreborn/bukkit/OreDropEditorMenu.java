package fr.isaac.cutcleanreborn.bukkit;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Editor for a single ore loot-table or extra-drop entry. */
final class OreDropEditorMenu extends MenuSupport {
    @Override protected MenuSupport parentMenu() { return new OreDropListMenu(plugin, oreBlock, family, familyPage, dropPage); }

    private static final int AMOUNT_SLOT = 11, ITEM_SLOT = 13, CHANCE_SLOT = 15, BACK_SLOT = 18, REMOVE_SLOT = 22;
    private final Material oreBlock;
    private final OreDefinition family;
    private final int familyPage, dropPage, sourceIndex;
    private final boolean vanilla;
    private final CutCleanRebornPlugin.MobDrop drop;
    OreDropEditorMenu(CutCleanRebornPlugin plugin, Material oreBlock, OreDefinition family, int familyPage, int dropPage,
                      OreEditableDrop entry, int sourceIndex) {
        super(plugin, 3, "§8CCR • Ore Drop Editor");
        this.oreBlock = oreBlock; this.family = family; this.familyPage = familyPage; this.dropPage = dropPage;
        this.sourceIndex = sourceIndex; vanilla = entry.vanilla(); drop = entry.drop(); render();
    }
    private void render() {
        inventory.setItem(ITEM_SLOT, MenuItems.item(drop.material(), "§6" + MenuItems.prettyName(drop.material()),
                List.of(vanilla ? "§9Vanilla loot-table drop" : "§aExtra drop", "", "§eClick to change item")));
        inventory.setItem(AMOUNT_SLOT, MenuItems.item(Material.COMPARATOR, "§6Amount", List.of("§7Current range: §e" + drop.minimum() + "-" + drop.maximum(), "", "§eClick to edit")));
        inventory.setItem(CHANCE_SLOT, MenuItems.item(Material.GOLD_NUGGET, "§6Drop Chance", List.of("§7Current value: §e" + MenuItems.number(drop.chance()) + "%", "", "§eClick to edit")));
        inventory.setItem(REMOVE_SLOT, MenuItems.item(Material.BARRIER, vanilla ? "§cDisable Vanilla Drop" : "§cRemove Drop", List.of("§7Set this entry to 0% or remove it.")));
        inventory.setItem(BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to ore drops.")));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == BACK_SLOT) { back(player); return; }
        if (slot == REMOVE_SLOT) { update(new CutCleanRebornPlugin.MobDrop(drop.material(), drop.minimum(), drop.maximum(), 0.0D), player, !vanilla); return; }
        if (slot == ITEM_SLOT) new AnvilTextInput<Material>(plugin, "§8CCR • Drop Item", "item", drop.material().getKey().toString(), MenuItems::findItem,
                (target, item) -> update(new CutCleanRebornPlugin.MobDrop(item, drop.minimum(), drop.maximum(), drop.chance()), target, false)).open(player);
        else if (slot == AMOUNT_SLOT) new AnvilTextInput<Range>(plugin, "§8CCR • Drop Amount", "amount", drop.minimum() + "-" + drop.maximum(),
                OreDropEditorMenu::parseRange, (target, range) -> update(new CutCleanRebornPlugin.MobDrop(drop.material(), range.minimum(), range.maximum(), drop.chance()), target, false)).open(player);
        else if (slot == CHANCE_SLOT) new AnvilTextInput<Double>(plugin, "§8CCR • Drop Chance", "chance", MenuItems.number(drop.chance()),
                OreDropEditorMenu::parseChance, (target, chance) -> update(new CutCleanRebornPlugin.MobDrop(drop.material(), drop.minimum(), drop.maximum(), chance), target, false)).open(player);
    }
    private void update(CutCleanRebornPlugin.MobDrop value, Player player, boolean removeCustom) {
        if (vanilla) {
            plugin.saveOreVanillaDropOverrides(oreBlock, List.of(value));
        } else {
            List<CutCleanRebornPlugin.MobDrop> drops = new ArrayList<>(plugin.getOreDrops(oreBlock));
            if (sourceIndex < drops.size()) { if (removeCustom) drops.remove(sourceIndex); else drops.set(sourceIndex, value); }
            plugin.saveOreDrops(oreBlock, drops);
        }
        if (removeCustom) back(player);
        else new OreDropEditorMenu(plugin, oreBlock, family, familyPage, dropPage,
                new OreEditableDrop(value, vanilla, null), sourceIndex).open(player);
    }
    private void back(Player player) { new OreDropListMenu(plugin, oreBlock, family, familyPage, dropPage).open(player); }
    private static Range parseRange(String input) {
        var range = GameplayMath.range(input, 0, 64);
        return range == null ? null : new Range(range.minimum(), range.maximum());
    }
    private static Double parseChance(String input) {
        return GameplayMath.percentage(input);
    }
    private record Range(int minimum, int maximum) { }
}

