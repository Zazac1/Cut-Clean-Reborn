package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import fr.isaac.cutcleanreborn.fabric.InventoryRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
abstract class CraftPreviewMixin {
  @Inject(method = "slotChangedCraftingGrid", at = @At("RETURN"))
  private static void ccr$planks(
      AbstractContainerMenu menu,
      ServerLevel level,
      Player player,
      CraftingContainer input,
      ResultContainer result,
      @Coerce Object recipe,
      CallbackInfo ci) {
    if (CutCleanReborn.runtime == null
        || !(player instanceof ServerPlayer serverPlayer)
        || !CutCleanReborn.runtime.inventory.cleanEnabled(serverPlayer)) return;
    var stack = result.getItem(0);
    if (!stack.isEmpty() && InventoryRules.id(stack.getItem()).endsWith("_planks")) {
      var output = CutCleanReborn.runtime.inventory.clean(stack);
      if (output != stack) {
        result.setItem(0, output);
        menu.broadcastChanges();
      }
    }
  }
}
