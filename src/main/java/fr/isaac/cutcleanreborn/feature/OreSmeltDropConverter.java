package fr.isaac.cutcleanreborn.feature;

import fr.isaac.cutcleanreborn.config.CutCleanConfigManager;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.recipe.BlastingRecipe;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class OreSmeltDropConverter {
	private static Map<String, String> oreSmeltOverrides = Collections.emptyMap();
	private static final Map<String, Float> XP_FALLBACKS = buildXpFallbacks();

	private OreSmeltDropConverter() {
	}

	public static void reloadFromConfig() {
		oreSmeltOverrides = new LinkedHashMap<>(CutCleanConfigManager.getConfig().oreSmeltOverrides);
	}

	public static ItemStack convertAndAwardExperience(World world, BlockPos pos, ItemStack originalStack) {
		if (!CutCleanConfigManager.getConfig().features.autoSmeltOreDrops || originalStack.isEmpty()) {
			return originalStack;
		}

		Identifier rawId = Registries.ITEM.getId(originalStack.getItem());
		if (rawId == null) {
			return originalStack;
		}

		String cookedIdString = oreSmeltOverrides.get(rawId.toString());
		if (cookedIdString == null || cookedIdString.isBlank()) {
			return originalStack;
		}

		Item cookedItem = ItemIdResolver.resolveItem(cookedIdString);
		if (cookedItem == null || cookedItem == originalStack.getItem()) {
			return originalStack;
		}

		if (world instanceof ServerWorld serverWorld) {
			awardSmeltingExperience(serverWorld, pos, originalStack);
		}

		return new ItemStack(cookedItem, originalStack.getCount());
	}

	private static void awardSmeltingExperience(ServerWorld world, BlockPos pos, ItemStack sourceStack) {
		float xpPerItem = getSmeltingExperiencePerItem(world, sourceStack);
		if (xpPerItem <= 0.0F) {
			return;
		}

		float exactXp = xpPerItem * sourceStack.getCount();
		int totalXp = MathHelper.floor(exactXp);
		if (world.random.nextFloat() < exactXp - totalXp) {
			totalXp++;
		}

		if (totalXp > 0) {
			ExperienceOrbEntity.spawn(world, Vec3d.ofCenter(pos), totalXp);
		}
	}

	private static float getSmeltingExperiencePerItem(ServerWorld world, ItemStack sourceStack) {
		ItemStack single = sourceStack.copyWithCount(1);
		SingleStackRecipeInput input = new SingleStackRecipeInput(single);
		Optional<RecipeEntry<SmeltingRecipe>> recipe = world.getRecipeManager().getFirstMatch(RecipeType.SMELTING, input, world);
		if (recipe.isPresent()) {
			return recipe.get().value().getExperience();
		}

		Optional<RecipeEntry<BlastingRecipe>> blasting = world.getRecipeManager().getFirstMatch(RecipeType.BLASTING, input, world);
		if (blasting.isPresent()) {
			return blasting.get().value().getExperience();
		}

		Identifier sourceId = Registries.ITEM.getId(sourceStack.getItem());
		if (sourceId != null) {
			Float fallback = XP_FALLBACKS.get(sourceId.toString());
			if (fallback != null) {
				return fallback;
			}
		}

		return 0.0F;
	}

	private static Map<String, Float> buildXpFallbacks() {
		Map<String, Float> map = new LinkedHashMap<>();
		map.put("minecraft:iron_ore", 0.7F);
		map.put("minecraft:deepslate_iron_ore", 0.7F);
		map.put("minecraft:raw_iron", 0.7F);
		map.put("minecraft:copper_ore", 0.7F);
		map.put("minecraft:deepslate_copper_ore", 0.7F);
		map.put("minecraft:raw_copper", 0.7F);
		map.put("minecraft:gold_ore", 1.0F);
		map.put("minecraft:deepslate_gold_ore", 1.0F);
		map.put("minecraft:raw_gold", 1.0F);
		map.put("minecraft:nether_gold_ore", 1.0F);
		return map;
	}
}
