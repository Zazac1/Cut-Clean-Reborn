package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
abstract class EntityLootMixin {
  @Inject(
      method =
          "spawnAtLocation(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
      at = @At("HEAD"),
      cancellable = true)
  private void ccr$capture(
      ServerLevel level, ItemStack stack, float offset, CallbackInfoReturnable<ItemEntity> cir) {
    if (CutCleanReborn.runtime != null
        && CutCleanReborn.runtime.loot.capture((Entity) (Object) this, stack))
      cir.setReturnValue(null);
  }

  @Inject(method = "onBelowWorld", at = @At("HEAD"))
  private void ccr$void(CallbackInfo ci) {
    if (CutCleanReborn.runtime != null
        && (Object) this instanceof ItemEntity entity
        && !entity.level().isClientSide())
      CutCleanReborn.runtime.inventory.destroyed(entity.getItem());
  }

  @Inject(method = "setRemoved", at = @At("HEAD"))
  private void ccr$killed(Entity.RemovalReason reason, CallbackInfo ci) {
    if (reason == Entity.RemovalReason.KILLED
        && CutCleanReborn.runtime != null
        && (Object) this instanceof ItemEntity entity
        && !entity.level().isClientSide())
      CutCleanReborn.runtime.inventory.destroyed(entity.getItem());
  }
}
