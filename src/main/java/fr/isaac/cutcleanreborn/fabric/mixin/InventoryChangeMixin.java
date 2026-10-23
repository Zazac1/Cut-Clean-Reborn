package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
abstract class InventoryChangeMixin {
  @Shadow @Final public Player player;

  @Inject(method = "setChanged", at = @At("RETURN"))
  private void ccr$changed(CallbackInfo ci) {
    schedule();
  }

  @Inject(method = "setItem", at = @At("RETURN"))
  private void ccr$set(int slot, ItemStack stack, CallbackInfo ci) {
    schedule();
  }

  private void schedule() {
    if (CutCleanReborn.ready() && player instanceof ServerPlayer serverPlayer)
      CutCleanReborn.instance().inventory().schedule(serverPlayer);
  }
}
