package fr.isaac.cutcleanreborn.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CutCleanConfig {
	public Features features = new Features();
	public Map<String, String> cookedDropOverrides = defaultCookedDropOverrides();
	public Map<String, String> oreSmeltOverrides = defaultOreSmeltOverrides();
	public ToolEnchanting toolEnchanting = defaultToolEnchanting();
	public Map<String, String> stoneDropOverrides = defaultStoneDropOverrides();
	public Map<String, List<ExtraDropEntry>> animalExtraDrops = defaultAnimalExtraDrops();
	public TreeFeller treeFeller = defaultTreeFeller();
	public SugarCane sugarCane = defaultSugarCane();
	public LegacyMining legacyMining = defaultLegacyMining();
	public DiamondLimit diamondLimit = defaultDiamondLimit();

	public static class Features {
		public boolean cookAnimalDrops = true;
		public boolean autoSmeltOreDrops = true;
		public boolean autoEnchantCraftedTools = true;
		public boolean normalizeStoneDropsToCobblestone = true;
		public boolean customAnimalExtraDrops = true;
		public boolean breakWholeTreeFromOneLog = true;
		public boolean boostSugarCaneDrops = true;
		public boolean spreadSugarCaneNearExisting = true;
		public boolean legacyPre118Mining = false;
	}

	public static class TreeFeller {
		public boolean bonusAppleDropsEnabled = true;
		public double extraAppleDropChance = 0.04;
	}

	public static class SugarCane {
		public int minNaturalHeight = 3;
		public int maxNaturalHeight = 4;
		public double nearbySpreadChance = 0.32;
	}

	public static class LegacyMining {
		public int cycleTicks = 40;
		public int attemptsPerPlayer = 6;
		public int minY = 24;
		public int maxY = 112;
		public double replacementChance = 0.35;
		public int seedSearchRadius = 4;
		public double existingVeinBoostChance = 0.7;
		public int existingVeinExtraMin = 2;
		public int existingVeinExtraMax = 5;
		public int newVeinMinSize = 3;
		public int newVeinMaxSize = 7;
		public boolean preferNearCaves = true;
	}

	/** Server-authoritative UHC diamond cap. Disabled by default for existing worlds. */
	public static class DiamondLimit {
		public boolean enabled = false;
		public int limit = 17;
		/** convert_to_xp or drop_excess. */
		public String excessBehavior = "convert_to_xp";
		public int xpPerDiamondValue = 5;
	}

	public static class ToolEnchanting {
		public int efficiencyLevel = 3;
		public int unbreakingLevel = 3;
		public List<String> toolItemIds = defaultAutoEnchantToolIds();
		public Map<String, String> itemGrades = defaultAutoEnchantItemGrades();
		public Map<String, Map<String, Integer>> gradeEnchantments = defaultGradeEnchantments();
		public Map<String, Map<String, Integer>> itemEnchantments = defaultItemEnchantments();
	}

	public static class ExtraDropEntry {
		public String itemId = "minecraft:leather";
		public int minCount = 1;
		public int maxCount = 1;
		public double chance = 1.0;
	}

	public static Map<String, String> defaultCookedDropOverrides() {
		Map<String, String> defaults = new LinkedHashMap<>();
		defaults.put("minecraft:beef", "minecraft:cooked_beef");
		defaults.put("minecraft:porkchop", "minecraft:cooked_porkchop");
		defaults.put("minecraft:chicken", "minecraft:cooked_chicken");
		defaults.put("minecraft:mutton", "minecraft:cooked_mutton");
		defaults.put("minecraft:rabbit", "minecraft:cooked_rabbit");
		defaults.put("minecraft:cod", "minecraft:cooked_cod");
		defaults.put("minecraft:salmon", "minecraft:cooked_salmon");
		return defaults;
	}

	public static Map<String, String> defaultOreSmeltOverrides() {
		Map<String, String> defaults = new LinkedHashMap<>();
		defaults.put("minecraft:raw_iron", "minecraft:iron_ingot");
		defaults.put("minecraft:raw_gold", "minecraft:gold_ingot");
		defaults.put("minecraft:raw_copper", "minecraft:copper_ingot");
		defaults.put("minecraft:iron_ore", "minecraft:iron_ingot");
		defaults.put("minecraft:deepslate_iron_ore", "minecraft:iron_ingot");
		defaults.put("minecraft:gold_ore", "minecraft:gold_ingot");
		defaults.put("minecraft:deepslate_gold_ore", "minecraft:gold_ingot");
		defaults.put("minecraft:copper_ore", "minecraft:copper_ingot");
		defaults.put("minecraft:deepslate_copper_ore", "minecraft:copper_ingot");
		defaults.put("minecraft:nether_gold_ore", "minecraft:gold_ingot");
		defaults.put("minecraft:ancient_debris", "minecraft:netherite_scrap");
		return defaults;
	}

	public static ToolEnchanting defaultToolEnchanting() {
		ToolEnchanting toolEnchanting = new ToolEnchanting();
		toolEnchanting.efficiencyLevel = 3;
		toolEnchanting.unbreakingLevel = 3;
		toolEnchanting.toolItemIds = defaultAutoEnchantToolIds();
		toolEnchanting.itemGrades = defaultAutoEnchantItemGrades();
		toolEnchanting.gradeEnchantments = defaultGradeEnchantments();
		toolEnchanting.itemEnchantments = defaultItemEnchantments();
		return toolEnchanting;
	}

	public static Map<String, String> defaultAutoEnchantItemGrades() {
		Map<String, String> defaults = new LinkedHashMap<>();
		defaults.put("minecraft:wooden_pickaxe", "wood");
		defaults.put("minecraft:wooden_axe", "wood");
		defaults.put("minecraft:wooden_shovel", "wood");
		defaults.put("minecraft:wooden_hoe", "wood");
		defaults.put("minecraft:stone_pickaxe", "stone");
		defaults.put("minecraft:stone_axe", "stone");
		defaults.put("minecraft:stone_shovel", "stone");
		defaults.put("minecraft:stone_hoe", "stone");
		defaults.put("minecraft:iron_pickaxe", "iron");
		defaults.put("minecraft:iron_axe", "iron");
		defaults.put("minecraft:iron_shovel", "iron");
		defaults.put("minecraft:iron_hoe", "iron");
		defaults.put("minecraft:diamond_pickaxe", "diamond");
		defaults.put("minecraft:diamond_axe", "diamond");
		defaults.put("minecraft:diamond_shovel", "diamond");
		defaults.put("minecraft:diamond_hoe", "diamond");
		return defaults;
	}

	public static Map<String, Map<String, Integer>> defaultGradeEnchantments() {
		Map<String, Map<String, Integer>> defaults = new LinkedHashMap<>();
		defaults.put("wood", enchantMap(3, 3));
		defaults.put("stone", enchantMap(3, 3));
		defaults.put("iron", enchantMap(3, 3));
		defaults.put("diamond", enchantMap(3, 3));
		return defaults;
	}

	public static Map<String, Map<String, Integer>> defaultItemEnchantments() {
		return new LinkedHashMap<>();
	}

	private static Map<String, Integer> enchantMap(int efficiency, int unbreaking) {
		Map<String, Integer> values = new LinkedHashMap<>();
		values.put("minecraft:efficiency", Math.max(1, efficiency));
		values.put("minecraft:unbreaking", Math.max(1, unbreaking));
		return values;
	}

	public static List<String> defaultAutoEnchantToolIds() {
		return new ArrayList<>(Arrays.asList(
			"minecraft:wooden_pickaxe",
			"minecraft:wooden_axe",
			"minecraft:wooden_shovel",
			"minecraft:wooden_hoe",
			"minecraft:stone_pickaxe",
			"minecraft:stone_axe",
			"minecraft:stone_shovel",
			"minecraft:stone_hoe",
			"minecraft:iron_pickaxe",
			"minecraft:iron_axe",
			"minecraft:iron_shovel",
			"minecraft:iron_hoe",
			"minecraft:diamond_pickaxe",
			"minecraft:diamond_axe",
			"minecraft:diamond_shovel",
			"minecraft:diamond_hoe"
		));
	}

	public static Map<String, String> defaultStoneDropOverrides() {
		Map<String, String> defaults = new LinkedHashMap<>();
		defaults.put("minecraft:andesite", "minecraft:cobblestone");
		defaults.put("minecraft:diorite", "minecraft:cobblestone");
		defaults.put("minecraft:granite", "minecraft:cobblestone");
		defaults.put("minecraft:tuff", "minecraft:cobblestone");
		defaults.put("minecraft:calcite", "minecraft:cobblestone");
		defaults.put("minecraft:deepslate", "minecraft:cobblestone");
		defaults.put("minecraft:cobbled_deepslate", "minecraft:cobblestone");
		return defaults;
	}

	public static Map<String, List<ExtraDropEntry>> defaultAnimalExtraDrops() {
		Map<String, List<ExtraDropEntry>> defaults = new LinkedHashMap<>();

		ExtraDropEntry chickenFeather = new ExtraDropEntry();
		chickenFeather.itemId = "minecraft:feather";
		chickenFeather.minCount = 1;
		chickenFeather.maxCount = 2;
		chickenFeather.chance = 1.0;
		defaults.put("minecraft:chicken", new ArrayList<>(Collections.singletonList(chickenFeather)));

		ExtraDropEntry cowLeather = new ExtraDropEntry();
		cowLeather.itemId = "minecraft:leather";
		cowLeather.minCount = 1;
		cowLeather.maxCount = 2;
		cowLeather.chance = 1.0;
		defaults.put("minecraft:cow", new ArrayList<>(Collections.singletonList(cowLeather)));

		defaults.put("minecraft:pig", new ArrayList<>());
		defaults.put("minecraft:sheep", new ArrayList<>());
		return defaults;
	}

	public static TreeFeller defaultTreeFeller() {
		TreeFeller settings = new TreeFeller();
		settings.bonusAppleDropsEnabled = true;
		settings.extraAppleDropChance = 0.04;
		return settings;
	}

	public static SugarCane defaultSugarCane() {
		SugarCane settings = new SugarCane();
		settings.minNaturalHeight = 3;
		settings.maxNaturalHeight = 4;
		settings.nearbySpreadChance = 0.32;
		return settings;
	}

	public static LegacyMining defaultLegacyMining() {
		LegacyMining settings = new LegacyMining();
		settings.cycleTicks = 40;
		settings.attemptsPerPlayer = 6;
		settings.minY = 24;
		settings.maxY = 112;
		settings.replacementChance = 0.35;
		settings.seedSearchRadius = 4;
		settings.existingVeinBoostChance = 0.7;
		settings.existingVeinExtraMin = 2;
		settings.existingVeinExtraMax = 5;
		settings.newVeinMinSize = 3;
		settings.newVeinMaxSize = 7;
		settings.preferNearCaves = true;
		return settings;
	}

	public static DiamondLimit defaultDiamondLimit() {
		return new DiamondLimit();
	}
}
