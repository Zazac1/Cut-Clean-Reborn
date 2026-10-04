package fr.isaac.cutcleanreborn.client;

import fr.isaac.cutcleanreborn.config.CutCleanConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.text.Text;

public class ToolEnchantingScreen extends Screen {
	private final Screen parent;
	private final CutCleanConfig config;

	public ToolEnchantingScreen(Screen parent, CutCleanConfig config) {
		super(Text.literal("Auto Enchant"));
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void init() {
		int left = width / 2 - 140;
		int right = width / 2 + 140;

		MultilineTextWidget titleWidget = new MultilineTextWidget(left, 12, title, textRenderer);
		titleWidget.setMaxWidth(280);
		titleWidget.setCentered(true);
		addDrawableChild(titleWidget);

		boolean enabled = config.features.autoEnchantCraftedTools;
		addDrawableChild(ButtonWidget.builder(Text.literal("Auto Enchant: " + (enabled ? "ON" : "OFF")), b -> {
			config.features.autoEnchantCraftedTools = !enabled;
			clearAndInit();
		}).dimensions(left, 42, right - left, 20).build());

		MultilineTextWidget help = new MultilineTextWidget(left, 66, Text.literal("Configure item grades and enchant rules."), textRenderer);
		help.setMaxWidth(right - left);
		help.setCentered(true);
		help.setMaxRows(1);
		addDrawableChild(help);

		addDrawableChild(ButtonWidget.builder(Text.literal("Edit Item -> Grade"), b -> {
			client.setScreen(new KeyValueListScreen(this, "Item to grade", config.toolEnchanting.itemGrades, v -> config.toolEnchanting.itemGrades = v));
		}).dimensions(left, 90, right - left, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Edit Grade Enchants"), b -> {
			client.setScreen(new EnchantRuleListScreen(this, "Grade enchant rules", config.toolEnchanting.gradeEnchantments, v -> config.toolEnchanting.gradeEnchantments = v));
		}).dimensions(left, 114, right - left, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Edit Item Enchants"), b -> {
			client.setScreen(new EnchantRuleListScreen(this, "Item enchant overrides", config.toolEnchanting.itemEnchantments, v -> config.toolEnchanting.itemEnchantments = v));
		}).dimensions(left, 138, right - left, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> {
			client.setScreen(parent);
		}).dimensions(width / 2 - 104, height - 30, 100, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(parent))
			.dimensions(width / 2 + 4, height - 30, 100, 20).build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
		super.render(context, mouseX, mouseY, delta);
	}
}
