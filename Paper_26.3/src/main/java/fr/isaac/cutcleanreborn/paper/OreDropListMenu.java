package fr.isaac.cutcleanreborn.paper;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Editable vanilla loot-table outputs and extra drops for one exact ore block. */
final class OreDropListMenu extends MenuSupport {
    private static final int[] DROP_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private static final int ADD_SLOT = 40, XP_SLOT = 41, PREVIOUS_SLOT = 45, BACK_SLOT = 49, RESET_SLOT = 50, NEXT_SLOT = 53;
    private final Material oreBlock;
    private final OreDefinition family;
    private final int familyPage, page;
    private final List<OreEditableDrop> drops;
    OreDropListMenu(CutCleanRebornPlugin plugin, Material oreBlock, OreDefinition family, int familyPage) {
        this(plugin, oreBlock, family, familyPage, 0);
    }
    OreDropListMenu(CutCleanRebornPlugin plugin, Material oreBlock, OreDefinition family, int familyPage, int requestedPage) {
        super(plugin, 6, "§8CCR • " + MenuItems.prettyName(oreBlock) + " • Drops");
        this.oreBlock = oreBlock; this.family = family; this.familyPage = familyPage; drops = listedDrops();
        int pages = Math.max(1, (drops.size() + DROP_SLOTS.length - 1) / DROP_SLOTS.length);
        page = Math.clamp(requestedPage, 0, pages - 1); render(pages);
    }
    private void render(int pages) {
        int start = page * DROP_SLOTS.length;
        for (int i = 0; i < DROP_SLOTS.length && start + i < drops.size(); i++) {
            OreEditableDrop entry = drops.get(start + i); CutCleanRebornPlugin.MobDrop drop = entry.drop();
            List<String> lore = new ArrayList<>(List.of(entry.vanilla() ? "§9Vanilla loot-table drop" : "§aExtra drop", "§7Amount: §e" + drop.minimum() + "-" + drop.maximum(),
                    "§7Drop chance: §e" + MenuItems.number(drop.chance()) + "%"));
            if (entry.fortuneThree() != null) lore.add("§bFortune III vanilla: §e" + entry.fortuneThree().minimum() + "-" + entry.fortuneThree().maximum());
            if (!entry.vanilla() && plugin.oreFortuneCustomEnabled())
                lore.add("§bFortune III: §e" + drop.minimum() + "-" + (drop.maximum() * 4));
            lore.add(""); lore.add("§eLeft click: Edit"); lore.add("§cRight click: Remove");
            inventory.setItem(DROP_SLOTS[i], MenuItems.item(drop.material(), "§6" + MenuItems.prettyName(drop.material()), lore));
        }
        inventory.setItem(ADD_SLOT, MenuItems.item(Material.PAPER, "§aAdd Drop", List.of("§7Add an extra item drop.")));
        inventory.setItem(XP_SLOT, MenuItems.item(Material.EXPERIENCE_BOTTLE, "§6Extra XP",
                List.of("§7Added to vanilla XP: §e" + plugin.getOreXpBonus(oreBlock), "", "§eClick to edit")));
        inventory.setItem(RESET_SLOT, MenuItems.item(Material.CLOCK, "§eReset Vanilla Values", List.of("§7Remove overrides, added drops and extra XP.")));
        inventory.setItem(BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to ore variants.")));
        if (page > 0) inventory.setItem(PREVIOUS_SLOT, MenuItems.item(Material.ARROW, "§ePrevious page", List.of("§7Page " + (page + 1) + " / " + pages)));
        if (page + 1 < pages) inventory.setItem(NEXT_SLOT, MenuItems.item(Material.ARROW, "§eNext page", List.of("§7Page " + (page + 1) + " / " + pages)));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (slot == BACK_SLOT) { new OreConfigurationMenu(plugin, family, familyPage).open(player); return; }
        if (slot == RESET_SLOT) {
            plugin.saveOreVanillaDropOverrides(oreBlock, List.of());
            plugin.saveOreDrops(oreBlock, List.of());
            plugin.setOreXpBonus(oreBlock, 0);
            reopen(player); return;
        }
        if (slot == PREVIOUS_SLOT && page > 0) { new OreDropListMenu(plugin, oreBlock, family, familyPage, page - 1).open(player); return; }
        if (slot == NEXT_SLOT && (page + 1) * DROP_SLOTS.length < drops.size()) { new OreDropListMenu(plugin, oreBlock, family, familyPage, page + 1).open(player); return; }
        if (slot == ADD_SLOT) { new AnvilTextInput<Material>(plugin, "§8CCR • Add Ore Drop", "item", "minecraft:diamond", MenuItems::findItem, (target, item) -> {
            List<CutCleanRebornPlugin.MobDrop> updated = new ArrayList<>(plugin.getOreDrops(oreBlock));
            updated.add(new CutCleanRebornPlugin.MobDrop(item, 1, 1, 100.0D)); plugin.saveOreDrops(oreBlock, updated); reopen(target);
        }).open(player); return; }
        if (slot == XP_SLOT) { new AnvilTextInput<Integer>(plugin, "§8CCR • Extra Ore XP", "extra XP", String.valueOf(plugin.getOreXpBonus(oreBlock)),
                MobConfigurationMenu::positiveInteger, (target, xp) -> { plugin.setOreXpBonus(oreBlock, xp); reopen(target); }).open(player); return; }
        int start = page * DROP_SLOTS.length;
        for (int i = 0; i < DROP_SLOTS.length; i++) if (slot == DROP_SLOTS[i] && start + i < drops.size()) {
            int index = start + i; OreEditableDrop entry = drops.get(index);
            if (click.isRightClick()) remove(entry, index);
            else new OreDropEditorMenu(plugin, oreBlock, family, familyPage, page, entry, entry.vanilla() ? -1 : customIndex(index)).open(player);
            if (click.isRightClick()) reopen(player); return;
        }
    }
    private List<OreEditableDrop> listedDrops() {
        List<OreEditableDrop> result = new ArrayList<>();
        CutCleanRebornPlugin.MobDrop override = plugin.getOreVanillaDropOverrides(oreBlock).stream().findFirst().orElse(null);
        for (CutCleanRebornPlugin.MobDrop vanilla : OreCatalog.vanillaDrops(oreBlock)) {
            result.add(new OreEditableDrop(override == null ? vanilla : override, true, OreCatalog.fortuneThreeDrop(oreBlock)));
        }
        for (CutCleanRebornPlugin.MobDrop drop : plugin.getOreDrops(oreBlock)) result.add(new OreEditableDrop(drop, false, null)); return result;
    }
    private int customIndex(int listIndex) { int output = 0; for (int i = 0; i < listIndex; i++) if (!drops.get(i).vanilla()) output++; return output; }
    private void remove(OreEditableDrop entry, int index) {
        if (entry.vanilla()) {
            plugin.saveOreVanillaDropOverrides(oreBlock,
                    List.of(new CutCleanRebornPlugin.MobDrop(entry.drop().material(), 0, 0, 0.0D)));
        } else { List<CutCleanRebornPlugin.MobDrop> custom = new ArrayList<>(plugin.getOreDrops(oreBlock)); int customIndex = customIndex(index); if (customIndex < custom.size()) custom.remove(customIndex); plugin.saveOreDrops(oreBlock, custom); }
    }
    private void reopen(Player player) { new OreDropListMenu(plugin, oreBlock, family, familyPage, page).open(player); }
}

record OreEditableDrop(CutCleanRebornPlugin.MobDrop drop, boolean vanilla, CutCleanRebornPlugin.MobDrop fortuneThree) { }
