package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentMenu.class)
abstract class EnchantingMixin {
  @Unique private int ccr$before;
  @Unique private Item ccr$input;

  @Inject(method = "clickMenuButton", at = @At("HEAD"))
  private void ccr$before(Player player, int button, CallbackInfoReturnable<Boolean> cir) {
    ccr$before = player.experienceLevel;
    ccr$input = ((AbstractContainerMenu) (Object) this).getSlot(0).getItem().getItem();
  }

  @Inject(method = "clickMenuButton", at = @At("RETURN"))
  private void ccr$after(Player player, int button, CallbackInfoReturnable<Boolean> cir) {
    if (CutCleanReborn.runtime != null
        && cir.getReturnValue()
        && player instanceof ServerPlayer serverPlayer) {
      var stack = ((AbstractContainerMenu) (Object) this).getSlot(0).getItem();
      var removed = CutCleanReborn.runtime.inventory.enforceCaps(stack, ccr$input);
      CutCleanReborn.runtime.inventory.refund(
          serverPlayer,
          ccr$input,
          removed,
          Math.min(button + 1, Math.max(0, ccr$before - player.experienceLevel)));
    }
  }
}
