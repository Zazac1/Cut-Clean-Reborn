package fr.isaac.cutcleanreborn.mixin;

import fr.isaac.cutcleanreborn.feature.AnimalExtraDropManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class AnimalExtraDropMixin {
	@Inject(
		method = "dropLoot(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/damage/DamageSource;Z)V",
		at = @At("TAIL")
	)
	private void cutcleanreborn$dropConfiguredExtras(ServerWorld world, DamageSource damageSource, boolean causedByPlayer, CallbackInfo ci) {
		if (!((Object) this instanceof PassiveEntity)) {
			return;
		}

		AnimalExtraDropManager.dropExtras(world, (LivingEntity) (Object) this);
	}
}
