package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class PlayerEquipmentMixin {
  @Inject(method = "setItemSlot", at = @At("RETURN"))
  private void ccr$equipment(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
    if (CutCleanReborn.ready() && (Object) this instanceof ServerPlayer player)
      CutCleanReborn.instance().inventory().schedule(player);
  }
}
