package fr.isaac.cutcleanreborn.mixin;

import fr.isaac.cutcleanreborn.feature.CookedDropConverter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class LivingEntityDropMixin {
	@ModifyVariable(
		method = "dropStack(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemStack;)Lnet/minecraft/entity/ItemEntity;",
		at = @At("HEAD"),
		argsOnly = true
	)
	private ItemStack cutcleanreborn$convertAnimalRawFood(ItemStack stack) {
		if (!((Object) this instanceof AnimalEntity)) {
			return stack;
		}

		return CookedDropConverter.convert(stack);
	}
}
