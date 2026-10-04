package fr.isaac.cutcleanreborn.feature;

import fr.isaac.cutcleanreborn.config.CutCleanConfigManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StoneDropNormalizer {
	private static Map<String, String> stoneDropOverrides = Collections.emptyMap();

	private StoneDropNormalizer() {
	}

	public static void reloadFromConfig() {
		stoneDropOverrides = new LinkedHashMap<>(CutCleanConfigManager.getConfig().stoneDropOverrides);
	}

	public static ItemStack normalize(ItemStack originalStack) {
		if (!CutCleanConfigManager.getConfig().features.normalizeStoneDropsToCobblestone || originalStack.isEmpty()) {
			return originalStack;
		}

		Identifier rawId = Registries.ITEM.getId(originalStack.getItem());
		if (rawId == null) {
			return originalStack;
		}

		String targetId = stoneDropOverrides.get(rawId.toString());
		if (targetId == null || targetId.isBlank()) {
			return originalStack;
		}

		Item targetItem = ItemIdResolver.resolveItem(targetId);
		if (targetItem == null || targetItem == originalStack.getItem()) {
			return originalStack;
		}

		return new ItemStack(targetItem, originalStack.getCount());
	}
}
