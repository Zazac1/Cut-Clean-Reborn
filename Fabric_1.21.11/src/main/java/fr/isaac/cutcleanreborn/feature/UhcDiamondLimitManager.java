package fr.isaac.cutcleanreborn.feature;

import fr.isaac.cutcleanreborn.config.CutCleanConfigManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UhcDiamondLimitManager {
	private static boolean enabled;
	private static int maxDiamondValue = 17;
	private static int xpPerDiamondValue = 5;
	private static boolean dropExcess;
	private static final Map<Item, Integer> DIAMOND_VALUE_BY_ITEM = createDiamondValues();
	private static final LoadoutOption ARMOR_LOADOUT = new LoadoutOption(
		"casque + plastron + bottes",
		Map.of(
			Items.DIAMOND_HELMET, 1,
			Items.DIAMOND_CHESTPLATE, 1,
			Items.DIAMOND_BOOTS, 1
		)
	);
	private static final LoadoutOption PVP_LOADOUT = new LoadoutOption(
		"plastron + bottes + pioche + epee",
		Map.of(
			Items.DIAMOND_CHESTPLATE, 1,
			Items.DIAMOND_BOOTS, 1,
			Items.DIAMOND_PICKAXE, 1,
			Items.DIAMOND_SWORD, 1
		)
	);

	private UhcDiamondLimitManager() {
	}

	public static void reloadFromConfig() {
		var config = CutCleanConfigManager.getConfig().diamondLimit;
		enabled = config.enabled;
		maxDiamondValue = Math.max(0, config.limit);
		xpPerDiamondValue = Math.max(0, config.xpPerDiamondValue);
		dropExcess = "drop_excess".equals(config.excessBehavior);
	}

	public static void onPlayerTick(ServerPlayerEntity player) {
		if (!enabled || player.age % 20 != 0) {
			return;
		}

		PlayerInventory inventory = player.getInventory();
		Map<Item, Integer> counts = countDiamondGear(inventory);
		LoadoutOption chosen = chooseBestLoadout(counts);
		Map<Item, Integer> remainingAllowances = new HashMap<>(chosen.allowedItems());
		Map<String, Integer> removedSummary = new LinkedHashMap<>();
		int keptGearValue = 0;
		int removedDiamondValue = 0;
		boolean changed = false;

		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (stack.isEmpty()) {
				continue;
			}

			Item item = stack.getItem();
			Integer unitValue = DIAMOND_VALUE_BY_ITEM.get(item);
			if (unitValue == null || item == Items.DIAMOND) {
				continue;
			}

			int allowance = remainingAllowances.getOrDefault(item, 0);
			int keep = Math.min(allowance, stack.getCount());
			remainingAllowances.put(item, Math.max(0, allowance - keep));
			keptGearValue += keep * unitValue;

			int remove = stack.getCount() - keep;
			if (remove <= 0) {
				continue;
			}

			removeExcess(player, stack, remove);
			removedDiamondValue += remove * unitValue;
			removedSummary.merge(stack.getName().getString(), remove, Integer::sum);
			changed = true;
		}

		int rawDiamondsAllowed = Math.max(0, maxDiamondValue - keptGearValue);
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (!stack.isOf(Items.DIAMOND)) {
				continue;
			}

			int keep = Math.min(rawDiamondsAllowed, stack.getCount());
			rawDiamondsAllowed -= keep;
			int remove = stack.getCount() - keep;
			if (remove <= 0) {
				continue;
			}

			removeExcess(player, stack, remove);
			removedDiamondValue += remove;
			removedSummary.merge("Diamants", remove, Integer::sum);
			changed = true;
		}

		if (!changed || removedDiamondValue <= 0) {
			return;
		}

		inventory.markDirty();
		player.currentScreenHandler.sendContentUpdates();

		int xp = dropExcess ? 0 : removedDiamondValue * xpPerDiamondValue;
		if (xp > 0) player.addExperience(xp);
		player.sendMessage(Text.literal(
			"[Regle UHC] Excess diamants " + (dropExcess ? "deposes au sol: " : "convertis en XP: ") + formatRemovedSummary(removedSummary)
				+ (dropExcess ? "" : " -> " + xp + " xp.") + " Loadout garde: " + chosen.label()
		), false);
	}

	private static void removeExcess(ServerPlayerEntity player, ItemStack stack, int amount) {
		if (dropExcess) player.dropItem(stack.copyWithCount(amount), true, false);
		stack.decrement(amount);
	}

	private static Map<Item, Integer> countDiamondGear(PlayerInventory inventory) {
		Map<Item, Integer> counts = new HashMap<>();
		for (int slot = 0; slot < inventory.size(); slot++) {
			ItemStack stack = inventory.getStack(slot);
			if (stack.isEmpty()) {
				continue;
			}

			Item item = stack.getItem();
			if (DIAMOND_VALUE_BY_ITEM.containsKey(item) && item != Items.DIAMOND) {
				counts.merge(item, stack.getCount(), Integer::sum);
			}
		}
		return counts;
	}

	private static LoadoutOption chooseBestLoadout(Map<Item, Integer> counts) {
		int armorValue = keptValueFor(ARMOR_LOADOUT, counts);
		int pvpValue = keptValueFor(PVP_LOADOUT, counts);
		if (pvpValue > armorValue) {
			return PVP_LOADOUT;
		}
		return ARMOR_LOADOUT;
	}

	private static int keptValueFor(LoadoutOption option, Map<Item, Integer> counts) {
		int kept = 0;
		for (Map.Entry<Item, Integer> entry : option.allowedItems().entrySet()) {
			int present = counts.getOrDefault(entry.getKey(), 0);
			int keep = Math.min(present, entry.getValue());
			kept += keep * DIAMOND_VALUE_BY_ITEM.getOrDefault(entry.getKey(), 0);
		}
		return kept;
	}

	private static String formatRemovedSummary(Map<String, Integer> removedSummary) {
		List<String> parts = new ArrayList<>();
		for (Map.Entry<String, Integer> entry : removedSummary.entrySet()) {
			parts.add(entry.getValue() + "x " + entry.getKey());
		}
		return String.join(", ", parts);
	}

	private static Map<Item, Integer> createDiamondValues() {
		Map<Item, Integer> values = new HashMap<>();
		values.put(Items.DIAMOND, 1);
		values.put(Items.DIAMOND_HELMET, 5);
		values.put(Items.DIAMOND_CHESTPLATE, 8);
		values.put(Items.DIAMOND_LEGGINGS, 7);
		values.put(Items.DIAMOND_BOOTS, 4);
		values.put(Items.DIAMOND_SWORD, 2);
		values.put(Items.DIAMOND_PICKAXE, 3);
		values.put(Items.DIAMOND_AXE, 3);
		values.put(Items.DIAMOND_SHOVEL, 1);
		values.put(Items.DIAMOND_HOE, 2);
		return values;
	}

	private record LoadoutOption(String label, Map<Item, Integer> allowedItems) {
	}
}
