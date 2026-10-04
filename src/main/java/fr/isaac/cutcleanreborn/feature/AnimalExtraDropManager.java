package fr.isaac.cutcleanreborn.feature;

import fr.isaac.cutcleanreborn.config.CutCleanConfig;
import fr.isaac.cutcleanreborn.config.CutCleanConfigManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnimalExtraDropManager {
	private static Map<String, List<CutCleanConfig.ExtraDropEntry>> extraDrops = Collections.emptyMap();

	private AnimalExtraDropManager() {
	}

	public static void reloadFromConfig() {
		Map<String, List<CutCleanConfig.ExtraDropEntry>> source = CutCleanConfigManager.getConfig().animalExtraDrops;
		Map<String, List<CutCleanConfig.ExtraDropEntry>> copy = new LinkedHashMap<>();
		for (Map.Entry<String, List<CutCleanConfig.ExtraDropEntry>> entry : source.entrySet()) {
			copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
		}
		extraDrops = copy;
	}

	public static void dropExtras(ServerWorld world, LivingEntity entity) {
		if (!CutCleanConfigManager.getConfig().features.customAnimalExtraDrops) {
			return;
		}

		Identifier entityId = Registries.ENTITY_TYPE.getId(entity.getType());
		if (entityId == null) {
			return;
		}

		List<CutCleanConfig.ExtraDropEntry> entries = extraDrops.get(entityId.toString());
		if (entries == null || entries.isEmpty()) {
			return;
		}

		Random random = world.getRandom();
		for (CutCleanConfig.ExtraDropEntry entry : entries) {
			if (entry == null || entry.itemId == null || entry.itemId.isBlank()) {
				continue;
			}

			double chance = Math.max(0.0D, Math.min(1.0D, entry.chance));
			if (random.nextDouble() > chance) {
				continue;
			}

			int min = Math.max(0, entry.minCount);
			int max = Math.max(min, entry.maxCount);
			int amount = min + random.nextInt(max - min + 1);
			if (amount <= 0) {
				continue;
			}

			Item item = ItemIdResolver.resolveItem(entry.itemId);
			if (item == null) {
				continue;
			}

			entity.dropStack(world, new ItemStack(item, amount));
		}
	}
}
