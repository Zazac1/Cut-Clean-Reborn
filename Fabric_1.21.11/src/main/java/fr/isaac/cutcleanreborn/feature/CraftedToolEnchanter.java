package fr.isaac.cutcleanreborn.feature;

import fr.isaac.cutcleanreborn.CutCleanRebornMod;
import fr.isaac.cutcleanreborn.config.CutCleanConfigManager;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class CraftedToolEnchanter {
	private static Set<String> autoEnchantToolIds = Collections.emptySet();
	private static Map<String, String> itemGrades = Collections.emptyMap();
	private static Map<String, Map<String, Integer>> gradeEnchantments = Collections.emptyMap();
	private static Map<String, Map<String, Integer>> itemEnchantments = Collections.emptyMap();

	private CraftedToolEnchanter() {
	}

	public static void reloadFromConfig() {
		autoEnchantToolIds = new LinkedHashSet<>(CutCleanConfigManager.getConfig().toolEnchanting.toolItemIds);
		itemGrades = new LinkedHashMap<>(CutCleanConfigManager.getConfig().toolEnchanting.itemGrades);
		gradeEnchantments = deepCopy(CutCleanConfigManager.getConfig().toolEnchanting.gradeEnchantments);
		itemEnchantments = deepCopy(CutCleanConfigManager.getConfig().toolEnchanting.itemEnchantments);

		for (String itemId : autoEnchantToolIds) {
			itemGrades.putIfAbsent(itemId, inferGradeFromItemId(itemId));
		}
	}

	public static void enchantCraftedToolInPlace(ItemStack craftedStack, PlayerEntity player) {
		enchantIfEligibleInPlace(craftedStack, player);
	}

	public static void scanAndEnchantPlayerInventory(PlayerEntity player) {
		if (!CutCleanConfigManager.getConfig().features.autoEnchantCraftedTools) {
			return;
		}

		if (player.age % 20 != 0) {
			return;
		}

		PlayerInventory inventory = player.getInventory();
		boolean changed = false;
		for (int slot = 0; slot < inventory.size(); slot++) {
			if (enchantIfEligibleInPlace(inventory.getStack(slot), player)) {
				changed = true;
			}
		}

		if (enchantIfEligibleInPlace(player.currentScreenHandler.getCursorStack(), player)) {
			changed = true;
		}

		if (changed) {
			inventory.markDirty();
			player.currentScreenHandler.sendContentUpdates();
		}
	}

	public static boolean enchantIfEligibleInPlace(ItemStack stack, PlayerEntity player) {
		if (!CutCleanConfigManager.getConfig().features.autoEnchantCraftedTools || stack.isEmpty()) {
			return false;
		}

		Identifier itemId = Registries.ITEM.getId(stack.getItem());
		if (itemId == null) {
			return false;
		}

		String itemIdString = itemId.toString();
		if (!autoEnchantToolIds.contains(itemIdString)
			&& !itemGrades.containsKey(itemIdString)
			&& !itemEnchantments.containsKey(itemIdString)) {
			return false;
		}

		if (stack.hasEnchantments()) {
			return false;
		}

		Map<String, Integer> enchantRules = resolveEnchantRules(itemIdString);
		if (enchantRules.isEmpty()) {
			return false;
		}

		for (Map.Entry<String, Integer> entry : enchantRules.entrySet()) {
			Identifier enchantId = ItemIdResolver.parseIdentifier(entry.getKey());
			int level = Math.max(1, entry.getValue() == null ? 1 : entry.getValue());
			if (enchantId == null) {
				continue;
			}

			RegistryEntry.Reference<net.minecraft.enchantment.Enchantment> enchantment = player.getRegistryManager()
				.getOrThrow(RegistryKeys.ENCHANTMENT)
				.getEntry(enchantId)
				.orElse(null);

			if (enchantment != null) {
				stack.addEnchantment(enchantment, level);
			}
		}

		if (stack.hasEnchantments()) {
			CutCleanRebornMod.LOGGER.info("Auto-enchant applique sur {}", itemId);
			return true;
		}

		return false;
	}

	private static Map<String, Integer> resolveEnchantRules(String itemId) {
		Map<String, Integer> itemRules = itemEnchantments.get(itemId);
		if (itemRules != null && !itemRules.isEmpty()) {
			return itemRules;
		}

		String grade = itemGrades.get(itemId);
		if (grade == null || grade.isBlank()) {
			grade = inferGradeFromItemId(itemId);
		}

		Map<String, Integer> gradeRules = gradeEnchantments.get(grade);
		if (gradeRules != null && !gradeRules.isEmpty()) {
			return gradeRules;
		}

		Map<String, Integer> defaultRules = gradeEnchantments.get("default");
		return defaultRules == null ? Collections.emptyMap() : defaultRules;
	}

	private static String inferGradeFromItemId(String itemId) {
		if (itemId.contains("wooden_")) {
			return "wood";
		}
		if (itemId.contains("stone_")) {
			return "stone";
		}
		if (itemId.contains("iron_")) {
			return "iron";
		}
		if (itemId.contains("golden_")) {
			return "gold";
		}
		if (itemId.contains("diamond_")) {
			return "diamond";
		}
		if (itemId.contains("netherite_")) {
			return "netherite";
		}
		return "default";
	}

	private static Map<String, Map<String, Integer>> deepCopy(Map<String, Map<String, Integer>> input) {
		Map<String, Map<String, Integer>> copy = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, Integer>> entry : input.entrySet()) {
			copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
		}
		return copy;
	}
}
