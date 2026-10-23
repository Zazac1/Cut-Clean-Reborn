package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
abstract class ItemLifecycleMixin {
  @Inject(method = "onDestroyed", at = @At("HEAD"))
  private void ccr$destroyed(ItemEntity entity, CallbackInfo ci) {
    if (CutCleanReborn.runtime != null && !entity.level().isClientSide())
      CutCleanReborn.runtime.inventory.destroyed((ItemStack) (Object) this);
  }

  @Inject(
      method = {
        "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
        "applyDamage(ILnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V"
      },
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
  private void ccr$broken(CallbackInfo ci) {
    if (CutCleanReborn.runtime != null)
      CutCleanReborn.runtime.inventory.destroyed((ItemStack) (Object) this);
  }
}
