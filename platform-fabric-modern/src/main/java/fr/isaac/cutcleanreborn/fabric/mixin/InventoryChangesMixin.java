package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
abstract class InventoryChangesMixin {
  @Inject(
      method = {"setItem", "setChanged"},
      at = @At("RETURN"))
  private void ccr$changed(CallbackInfo ci) {
    if (CutCleanReborn.runtime != null
        && ((Inventory) (Object) this).player instanceof ServerPlayer player)
      CutCleanReborn.runtime.requestCorrection(player);
  }
}
