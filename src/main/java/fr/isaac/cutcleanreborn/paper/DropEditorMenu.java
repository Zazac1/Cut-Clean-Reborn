package fr.isaac.cutcleanreborn.paper;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/** Focused editor for the amount range and probability of a single custom drop. */
final class DropEditorMenu extends MenuSupport {
    private static final int ITEM_SLOT = 13;
    private static final int AMOUNT_SLOT = 11;
    private static final int CHANCE_SLOT = 15;
    private static final int REMOVE_SLOT = 22;
    private static final int BACK_SLOT = 18;
    private final MobMenuContext context;
    private final int dropPage;
    private final int sourceIndex;
    private final boolean vanilla;
    private final CutCleanRebornPlugin.MobDrop drop;

    DropEditorMenu(CutCleanRebornPlugin plugin, MobMenuContext context, int dropPage, EditableDrop entry, int sourceIndex) {
        super(plugin, 3, "§8CCR • Drop Editor");
        this.context = context;
        this.dropPage = dropPage;
        this.sourceIndex = sourceIndex;
        this.vanilla = entry.vanilla();
        this.drop = entry.drop();
        if (drop != null) render();
    }
    private void render() {
        inventory.setItem(ITEM_SLOT, MenuItems.item(drop.material(), "§6" + MenuItems.prettyName(drop.material()),
                List.of(vanilla ? "§9Vanilla drop" : "§aCustom drop", "§7Amount: §e" + drop.minimum() + "-" + drop.maximum(), "§7Drop chance: §e" + MenuItems.number(drop.chance()) + "%")));
        inventory.setItem(AMOUNT_SLOT, MenuItems.item(Material.COMPARATOR, "§6Amount",
                List.of("§7Current range: §e" + drop.minimum() + "-" + drop.maximum(), "", "§eClick to edit")));
        inventory.setItem(CHANCE_SLOT, MenuItems.item(Material.GOLD_NUGGET, "§6Drop chance",
                List.of("§7Current value: §e" + MenuItems.number(drop.chance()) + "%", "", "§eClick to edit")));
        inventory.setItem(REMOVE_SLOT, MenuItems.item(Material.BARRIER, vanilla ? "§cDisable Vanilla Drop" : "§cRemove Drop",
                List.of(vanilla ? "§7Sets this vanilla drop to 0%." : "§7Remove this custom drop.")));
        inventory.setItem(BACK_SLOT, MenuItems.item(Material.BARRIER, "§cBack", List.of("§7Return to drops.")));
    }
    @Override void handleClick(Player player, int slot, ClickType click) {
        if (drop == null) { new DropListMenu(plugin, context, dropPage).open(player); return; }
        if (slot == BACK_SLOT) { new DropListMenu(plugin, context, dropPage).open(player); return; }
        if (slot == REMOVE_SLOT) { remove(player); return; }
        if (slot == AMOUNT_SLOT) new AnvilTextInput<AmountRange>(plugin, "§8CCR • Drop Amount", "amount", drop.minimum() + "-" + drop.maximum(),
                input -> parseAmount(input, 64), (target, range) -> update(target,
                        new CutCleanRebornPlugin.MobDrop(drop.material(), range.minimum(), range.maximum(), drop.chance()))).open(player);
        else if (slot == CHANCE_SLOT) new AnvilTextInput<Double>(plugin, "§8CCR • Drop Chance", "chance", MenuItems.number(drop.chance()),
                DropEditorMenu::parseChance, (target, chance) -> update(target,
                        new CutCleanRebornPlugin.MobDrop(drop.material(), drop.minimum(), drop.maximum(), chance))).open(player);
    }
    private void update(Player player, CutCleanRebornPlugin.MobDrop value) {
        if (vanilla) {
            List<CutCleanRebornPlugin.MobDrop> overrides = new ArrayList<>(plugin.getVanillaDropOverrides(context.mob()));
            overrides.removeIf(existing -> existing.material() == value.material());
            overrides.add(value);
            plugin.saveVanillaDropOverrides(context.mob(), overrides);
        } else {
            List<CutCleanRebornPlugin.MobDrop> updated = new ArrayList<>(plugin.getMobDrops(context.mob()));
            if (sourceIndex >= updated.size()) { new DropListMenu(plugin, context, dropPage).open(player); return; }
            updated.set(sourceIndex, value);
            plugin.saveMobDrops(context.mob(), updated);
        }
        new DropListMenu(plugin, context, dropPage).open(player);
    }
    private void remove(Player player) {
        if (vanilla) {
            update(player, new CutCleanRebornPlugin.MobDrop(drop.material(), 0, 0, 0.0D));
            return;
        }
        List<CutCleanRebornPlugin.MobDrop> updated = new ArrayList<>(plugin.getMobDrops(context.mob()));
        if (sourceIndex < updated.size()) updated.remove(sourceIndex);
        plugin.saveMobDrops(context.mob(), updated);
        new DropListMenu(plugin, context, dropPage).open(player);
    }
    private static AmountRange parseAmount(String input, int maximumStack) {
        String[] parts = input.trim().split("-", -1);
        if (parts.length > 2 || parts.length == 0) return null;
        try {
            int minimum = Integer.parseInt(parts[0].trim());
            int maximum = parts.length == 1 ? minimum : Integer.parseInt(parts[1].trim());
            return minimum >= 0 && maximum >= minimum && maximum <= maximumStack ? new AmountRange(minimum, maximum) : null;
        } catch (NumberFormatException ignored) { return null; }
    }
    private static Double parseChance(String input) {
        String number = input.trim().replace("%", "").trim();
        try {
            double chance = Double.parseDouble(number);
            return Double.isFinite(chance) && chance >= 0.0D && chance <= 100.0D ? chance : null;
        } catch (NumberFormatException ignored) { return null; }
    }
    private record AmountRange(int minimum, int maximum) { }
}
