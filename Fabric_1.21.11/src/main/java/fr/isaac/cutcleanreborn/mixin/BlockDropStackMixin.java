package fr.isaac.cutcleanreborn.mixin;

import fr.isaac.cutcleanreborn.feature.OreSmeltDropConverter;
import fr.isaac.cutcleanreborn.feature.StoneDropNormalizer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(net.minecraft.block.Block.class)
public abstract class BlockDropStackMixin {
	@ModifyVariable(
		method = "dropStack(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/item/ItemStack;)V",
		at = @At("HEAD"),
		argsOnly = true
	)
	private static ItemStack cutcleanreborn$normalizeStoneDrops(ItemStack stack, World world, BlockPos pos) {
		ItemStack normalized = StoneDropNormalizer.normalize(stack);
		return OreSmeltDropConverter.convertAndAwardExperience(world, pos, normalized);
	}
}
