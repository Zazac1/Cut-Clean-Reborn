package fr.isaac.cutcleanreborn.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.isaac.cutcleanreborn.CutCleanRebornMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CutCleanConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(CutCleanRebornMod.MOD_ID + ".json");

	private static CutCleanConfig config = new CutCleanConfig();

	private CutCleanConfigManager() {
	}

	public static void load() {
		if (Files.exists(CONFIG_PATH)) {
			try {
				String raw = Files.readString(CONFIG_PATH);
				config = withDefaults(GSON.fromJson(raw, CutCleanConfig.class));
				save();
				return;
			} catch (Exception exception) {
				CutCleanRebornMod.LOGGER.error("Impossible de lire la config {}, repli sur les valeurs par defaut.", CONFIG_PATH, exception);
			}
		}

		config = withDefaults(new CutCleanConfig());
		save();
	}

	public static CutCleanConfig getConfig() {
		return config;
	}

	public static void save() {
		try {
			Files.createDirectories(CONFIG_PATH.getParent());
			Files.writeString(CONFIG_PATH, GSON.toJson(config));
		} catch (IOException exception) {
			CutCleanRebornMod.LOGGER.error("Impossible d'ecrire la config {}.", CONFIG_PATH, exception);
		}
	}

	private static CutCleanConfig withDefaults(CutCleanConfig input) {
		CutCleanConfig merged = input == null ? new CutCleanConfig() : input;

		if (merged.features == null) {
			merged.features = new CutCleanConfig.Features();
		}

		if (merged.cookedDropOverrides == null) {
			merged.cookedDropOverrides = CutCleanConfig.defaultCookedDropOverrides();
		}

		if (merged.oreSmeltOverrides == null) {
			merged.oreSmeltOverrides = CutCleanConfig.defaultOreSmeltOverrides();
		}

		if (merged.toolEnchanting == null) {
			merged.toolEnchanting = CutCleanConfig.defaultToolEnchanting();
		} else {
			if (merged.toolEnchanting.toolItemIds == null) {
				merged.toolEnchanting.toolItemIds = CutCleanConfig.defaultAutoEnchantToolIds();
			}

			if (merged.toolEnchanting.efficiencyLevel <= 0) {
				merged.toolEnchanting.efficiencyLevel = 3;
			}

			if (merged.toolEnchanting.unbreakingLevel <= 0) {
				merged.toolEnchanting.unbreakingLevel = 3;
			}

			if (merged.toolEnchanting.itemGrades == null) {
				merged.toolEnchanting.itemGrades = CutCleanConfig.defaultAutoEnchantItemGrades();
			}

			if (merged.toolEnchanting.gradeEnchantments == null) {
				merged.toolEnchanting.gradeEnchantments = CutCleanConfig.defaultGradeEnchantments();
			}

			if (merged.toolEnchanting.itemEnchantments == null) {
				merged.toolEnchanting.itemEnchantments = CutCleanConfig.defaultItemEnchantments();
			}

		}

		if (merged.stoneDropOverrides == null) {
			merged.stoneDropOverrides = CutCleanConfig.defaultStoneDropOverrides();
		}

		if (merged.animalExtraDrops == null) {
			merged.animalExtraDrops = CutCleanConfig.defaultAnimalExtraDrops();
		}

		if (merged.treeFeller == null) {
			merged.treeFeller = CutCleanConfig.defaultTreeFeller();
		}
		if (input == null || input.treeFeller == null) {
			merged.treeFeller.bonusAppleDropsEnabled = CutCleanConfig.defaultTreeFeller().bonusAppleDropsEnabled;
		}
		merged.treeFeller.extraAppleDropChance = Math.max(0.0, Math.min(1.0, merged.treeFeller.extraAppleDropChance));

		if (merged.sugarCane == null) {
			merged.sugarCane = CutCleanConfig.defaultSugarCane();
		}
		merged.sugarCane.minNaturalHeight = Math.max(1, merged.sugarCane.minNaturalHeight);
		merged.sugarCane.maxNaturalHeight = Math.max(merged.sugarCane.minNaturalHeight, merged.sugarCane.maxNaturalHeight);
		merged.sugarCane.nearbySpreadChance = Math.max(0.0, Math.min(1.0, merged.sugarCane.nearbySpreadChance));

		if (merged.legacyMining == null) {
			merged.legacyMining = CutCleanConfig.defaultLegacyMining();
		}
		merged.legacyMining.cycleTicks = Math.max(1, merged.legacyMining.cycleTicks);
		merged.legacyMining.attemptsPerPlayer = Math.max(1, merged.legacyMining.attemptsPerPlayer);
		merged.legacyMining.minY = Math.max(-64, merged.legacyMining.minY);
		merged.legacyMining.maxY = Math.max(merged.legacyMining.minY, Math.min(320, merged.legacyMining.maxY));
		merged.legacyMining.replacementChance = Math.max(0.0, Math.min(1.0, merged.legacyMining.replacementChance));
		merged.legacyMining.seedSearchRadius = Math.max(1, Math.min(12, merged.legacyMining.seedSearchRadius));
		merged.legacyMining.existingVeinBoostChance = Math.max(0.0, Math.min(1.0, merged.legacyMining.existingVeinBoostChance));
		merged.legacyMining.existingVeinExtraMin = Math.max(1, merged.legacyMining.existingVeinExtraMin);
		merged.legacyMining.existingVeinExtraMax = Math.max(merged.legacyMining.existingVeinExtraMin, merged.legacyMining.existingVeinExtraMax);
		merged.legacyMining.newVeinMinSize = Math.max(1, merged.legacyMining.newVeinMinSize);
		merged.legacyMining.newVeinMaxSize = Math.max(merged.legacyMining.newVeinMinSize, merged.legacyMining.newVeinMaxSize);

		if (merged.diamondLimit == null) {
			merged.diamondLimit = CutCleanConfig.defaultDiamondLimit();
		}
		merged.diamondLimit.limit = Math.max(0, merged.diamondLimit.limit);
		merged.diamondLimit.xpPerDiamondValue = Math.max(0, merged.diamondLimit.xpPerDiamondValue);
		if (!"convert_to_xp".equals(merged.diamondLimit.excessBehavior) && !"drop_excess".equals(merged.diamondLimit.excessBehavior)) {
			merged.diamondLimit.excessBehavior = "convert_to_xp";
		}

		return merged;
	}
}
