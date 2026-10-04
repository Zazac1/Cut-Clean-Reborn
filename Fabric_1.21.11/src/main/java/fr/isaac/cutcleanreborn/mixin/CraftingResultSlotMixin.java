package fr.isaac.cutcleanreborn.mixin;

import fr.isaac.cutcleanreborn.feature.CraftedToolEnchanter;
import org.spongepowered.asm.mixin.Final;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.CraftingResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingResultSlot.class)
public abstract class CraftingResultSlotMixin {
	@Shadow @Final private PlayerEntity player;

	@Inject(
		method = "onCrafted(Lnet/minecraft/item/ItemStack;)V",
		at = @At("HEAD")
	)
	private void cutcleanreborn$enchantCraftedTools(ItemStack stack, CallbackInfo ci) {
		CraftedToolEnchanter.enchantCraftedToolInPlace(stack, this.player);
	}
}
