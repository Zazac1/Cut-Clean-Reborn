package fr.isaac.cutcleanreborn.mixin;

import fr.isaac.cutcleanreborn.feature.LegacyMiningManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
	protected CreateWorldScreenMixin(Text title) {
		super(title);
	}

	@Unique
	private boolean cutcleanreborn$legacyMiningEnabled;

	@Inject(method = "init", at = @At("TAIL"))
	private void cutcleanreborn$addLegacyMiningToggle(CallbackInfo ci) {
		LegacyMiningManager.clearPendingNewWorldOverride();
		cutcleanreborn$legacyMiningEnabled = LegacyMiningManager.getNewWorldDefault();

		int buttonWidth = 260;
		int x = this.width / 2 - buttonWidth / 2;
		int y = this.height - 56;

		addDrawableChild(ButtonWidget.builder(Text.literal(buildLabel()), button -> {
			cutcleanreborn$legacyMiningEnabled = !cutcleanreborn$legacyMiningEnabled;
			LegacyMiningManager.setPendingNewWorldOverride(cutcleanreborn$legacyMiningEnabled);
			button.setMessage(Text.literal(buildLabel()));
		}).dimensions(x, y, buttonWidth, 20).build());
	}

	@Unique
	private String buildLabel() {
		return "Legacy Pre-1.18 Mining (World): " + (cutcleanreborn$legacyMiningEnabled ? "ON" : "OFF");
	}
}
