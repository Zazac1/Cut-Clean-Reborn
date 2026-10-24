package fr.isaac.cutcleanreborn.fabric.mixin;

import fr.isaac.cutcleanreborn.fabric.CutCleanReborn;
import fr.isaac.cutcleanreborn.fabric.InventoryRules;
import fr.isaac.cutcleanreborn.fabric.NativeAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
abstract class MenuChangesMixin {
  @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
  private void ccr$unique(int slot, int button, ContainerInput type, Player player, CallbackInfo ci) {
    var mod = CutCleanReborn.runtime;
    var menu = (AbstractContainerMenu) (Object) this;
    if (mod != null
        && player instanceof ServerPlayer
        && slot == 0
        && (menu instanceof CraftingMenu || menu instanceof InventoryMenu)
        && menu.getSlot(0).getItem().is(Items.MACE)
        && mod.config.bool(InventoryRules.ROOT + ".enabled", false)
        && mod.config.bool(InventoryRules.ROOT + ".unique-mace.enabled", false)
        && (type != ContainerInput.PICKUP || !menu.getCarried().isEmpty())) {
      NativeAccess.overlay(
          player,
          Component.literal("§eCCR • Fabriquez la masse unique par clic normal, curseur vide."));
      ci.cancel();
    }
  }

  @Inject(method = "clicked", at = @At("RETURN"))
  private void ccr$changed(int slot, int button, ContainerInput type, Player player, CallbackInfo ci) {
    if (CutCleanReborn.runtime != null && player instanceof ServerPlayer serverPlayer)
      CutCleanReborn.runtime.requestCorrection(serverPlayer);
  }
}
