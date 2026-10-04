package fr.isaac.cutcleanreborn.feature;

import fr.isaac.cutcleanreborn.config.CutCleanConfigManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CookedDropConverter {
	private static Map<String, String> cookedDropOverrides = Collections.emptyMap();

	private CookedDropConverter() {
	}

	public static void reloadFromConfig() {
		cookedDropOverrides = new LinkedHashMap<>(CutCleanConfigManager.getConfig().cookedDropOverrides);
	}

	public static ItemStack convert(ItemStack originalStack) {
		if (!CutCleanConfigManager.getConfig().features.cookAnimalDrops || originalStack.isEmpty()) {
			return originalStack;
		}

		Identifier rawId = Registries.ITEM.getId(originalStack.getItem());
		if (rawId == null) {
			return originalStack;
		}

		String cookedIdString = cookedDropOverrides.get(rawId.toString());
		if (cookedIdString == null || cookedIdString.isBlank()) {
			return originalStack;
		}

		Item cookedItem = ItemIdResolver.resolveItem(cookedIdString);
		if (cookedItem == null || cookedItem == originalStack.getItem()) {
			return originalStack;
		}

		return new ItemStack(cookedItem, originalStack.getCount());
	}
}
