package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class EquipmentChangesMixin {
  @Inject(method = "setItemSlot", at = @At("RETURN"))
  private void ccr$equipped(CallbackInfo ci) {
    if (CutCleanReborn.runtime != null && (Object) this instanceof ServerPlayer player)
      CutCleanReborn.runtime.requestCorrection(player);
  }
}
