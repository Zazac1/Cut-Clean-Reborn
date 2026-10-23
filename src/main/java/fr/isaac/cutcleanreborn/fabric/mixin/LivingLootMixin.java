package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingLootMixin {
  @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
  private void ccr$before(ServerLevel level, DamageSource source, CallbackInfo ci) {
    if (CutCleanReborn.ready())
      CutCleanReborn.instance().loot().beginDeath((LivingEntity) (Object) this, source);
  }

  @Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
  private void ccr$after(ServerLevel level, DamageSource source, CallbackInfo ci) {
    if (CutCleanReborn.ready())
      CutCleanReborn.instance().loot().finishDeath((LivingEntity) (Object) this);
  }
}
