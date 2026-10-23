package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
abstract class SlotMixin {
  @Shadow
  public abstract ItemStack getItem();

  @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
  private void ccr$unique(Player player, CallbackInfoReturnable<Boolean> cir) {
    if (CutCleanReborn.ready()
        && (Object) this instanceof ResultSlot
        && getItem().is(Items.MACE)
        && CutCleanReborn.instance().inventory().maceBlocked()) cir.setReturnValue(false);
  }
}
