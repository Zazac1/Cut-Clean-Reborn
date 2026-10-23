package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
  @Shadow
  public abstract ItemStack getItem();

  @Shadow
  public abstract void setItem(ItemStack stack);

  @Inject(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V",
              ordinal = 1))
  private void ccr$despawn(CallbackInfo ci) {
    if (CutCleanReborn.runtime != null && !((ItemEntity) (Object) this).level().isClientSide())
      CutCleanReborn.runtime.inventory.destroyed(getItem());
  }

  @Inject(method = "playerTouch", at = @At("HEAD"))
  private void ccr$pickup(Player player, CallbackInfo ci) {
    if (CutCleanReborn.runtime != null && player instanceof ServerPlayer serverPlayer) {
      ItemStack stack = getItem();
      if (CutCleanReborn.runtime.inventory.cleanEnabled(serverPlayer))
        stack = CutCleanReborn.runtime.inventory.clean(stack);
      CutCleanReborn.runtime.inventory.auto(stack);
      setItem(stack);
    }
  }

  @Inject(method = "playerTouch", at = @At("RETURN"))
  private void ccr$pickedUp(Player player, CallbackInfo ci) {
    if (CutCleanReborn.runtime != null && player instanceof ServerPlayer serverPlayer)
      CutCleanReborn.runtime.requestCorrection(serverPlayer);
  }
}
