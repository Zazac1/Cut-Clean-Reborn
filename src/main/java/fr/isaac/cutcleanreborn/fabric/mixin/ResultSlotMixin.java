package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ResultSlot.class)
abstract class ResultSlotMixin {
  @Inject(method = "onTake", at = @At("HEAD"))
  private void ccr$crafted(Player player, ItemStack stack, CallbackInfo ci) {
    if (CutCleanReborn.ready() && player instanceof ServerPlayer serverPlayer)
      CutCleanReborn.instance().inventory().crafted(serverPlayer, stack);
  }
}
