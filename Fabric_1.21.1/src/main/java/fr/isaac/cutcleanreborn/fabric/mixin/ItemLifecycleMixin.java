package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStack.class)
abstract class ItemLifecycleMixin {
  @Inject(method = "onDestroyed", at = @At("HEAD"))
  private void ccr$destroyed(ItemEntity entity, CallbackInfo ci) {
    if (CutCleanReborn.ready() && !entity.level().isClientSide)
      CutCleanReborn.instance().inventory().destroyed((ItemStack) (Object) this);
  }

  @Inject(
      method =
          "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;Ljava/util/function/Consumer;)V",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
  private void ccr$broken(
      int damage,
      ServerLevel level,
      ServerPlayer player,
      Consumer<Item> consumer,
      CallbackInfo ci) {
    if (CutCleanReborn.ready())
      CutCleanReborn.instance().inventory().destroyed((ItemStack) (Object) this);
  }
}
