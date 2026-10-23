package fr.isaac.cutcleanreborn.fabric.qamixin;

import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Test-only native age control; remaps with Minecraft rather than relying on reflection names. */
@Mixin(ItemEntity.class)
public interface QaItemEntityAge {
  @Accessor("age")
  void ccr$setAge(int value);
}
