package fr.isaac.cutcleanreborn.feature;

import fr.isaac.cutcleanreborn.CutCleanRebornMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class ItemIdResolver {
	private ItemIdResolver() {
	}

	public static Item resolveItem(String itemId) {
		Identifier identifier = parseIdentifier(itemId);
		if (identifier == null) {
			return null;
		}

		Item item = Registries.ITEM.get(identifier);
		if (item == null) {
			return null;
		}

		Identifier resolved = Registries.ITEM.getId(item);
		if (resolved == null || !resolved.equals(identifier)) {
			CutCleanRebornMod.LOGGER.warn("Item de config introuvable: {}", itemId);
			return null;
		}

		return item;
	}

	public static Identifier parseIdentifier(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		String[] split = value.split(":", 2);
		try {
			if (split.length == 1) {
				return Identifier.of("minecraft", split[0]);
			}
			return Identifier.of(split[0], split[1]);
		} catch (IllegalArgumentException exception) {
			CutCleanRebornMod.LOGGER.warn("Identifier invalide dans la config: {}", value);
			return null;
		}
	}
}
