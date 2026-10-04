package fr.isaac.cutcleanreborn.client;

import fr.isaac.cutcleanreborn.config.CutCleanConfig;
import fr.isaac.cutcleanreborn.feature.LegacyMiningManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class TreeAndHarvestSettingsScreen extends Screen {
	private final Screen parent;
	private final CutCleanConfig config;

	private static final int CONTENT_TOP = 36;
	private static final int CONTENT_BOTTOM = 28;
	private static final int SCROLL_STEP = 14;

	private int scrollOffset;
	private int maxScrollOffset;

	private ButtonWidget treeFellerButton;
	private ButtonWidget appleBonusButton;
	private ButtonWidget sugarCaneButton;
	private ButtonWidget sugarSpreadButton;
	private ButtonWidget legacyToggleButton;

	private TextFieldWidget appleChanceField;
	private MultilineTextWidget appleLabel;
	private MultilineTextWidget sugarSectionLabel;
	private MultilineTextWidget sugarMinLabel;
	private MultilineTextWidget sugarMaxLabel;
	private MultilineTextWidget sugarSpreadLabel;
	private MultilineTextWidget legacySectionLabel;
	private MultilineTextWidget legacyCycleLabel;
	private MultilineTextWidget legacyAttemptsLabel;
	private MultilineTextWidget legacyMinYLabel;
	private MultilineTextWidget legacyMaxYLabel;
	private MultilineTextWidget legacyReplacementLabel;
	private MultilineTextWidget legacySearchLabel;
	private MultilineTextWidget legacyExistingBoostLabel;
	private MultilineTextWidget legacyBoostMinLabel;
	private MultilineTextWidget legacyBoostMaxLabel;
	private MultilineTextWidget legacyNewVeinMinLabel;
	private MultilineTextWidget legacyNewVeinMaxLabel;
	private TextFieldWidget sugarMinHeightField;
	private TextFieldWidget sugarMaxHeightField;
	private TextFieldWidget sugarSpreadChanceField;
	private TextFieldWidget legacyCycleTicksField;
	private TextFieldWidget legacyAttemptsField;
	private TextFieldWidget legacyMinYField;
	private TextFieldWidget legacyMaxYField;
	private TextFieldWidget legacyReplacementChanceField;
	private TextFieldWidget legacySearchRadiusField;
	private TextFieldWidget legacyExistingBoostChanceField;
	private TextFieldWidget legacyExistingExtraMinField;
	private TextFieldWidget legacyExistingExtraMaxField;
	private TextFieldWidget legacyNewVeinMinField;
	private TextFieldWidget legacyNewVeinMaxField;

	public TreeAndHarvestSettingsScreen(Screen parent, CutCleanConfig config) {
		super(Text.literal("Cleaner Harvest"));
		this.parent = parent;
		this.config = config;
	}

	@Override
	protected void init() {
		int left = width / 2 - 140;
		int right = width / 2 + 140;
		int contentWidth = right - left;
		int contentTop = CONTENT_TOP;
		int contentBottom = height - 40 - CONTENT_BOTTOM;

		MultilineTextWidget titleWidget = new MultilineTextWidget(left, 12, title, textRenderer);
		titleWidget.setMaxWidth(contentWidth);
		titleWidget.setCentered(true);
		addDrawableChild(titleWidget);

		boolean treeEnabled = config.features.breakWholeTreeFromOneLog;
		treeFellerButton = ButtonWidget.builder(Text.literal("Tree Feller: " + (treeEnabled ? "ON" : "OFF")), b -> {
			config.features.breakWholeTreeFromOneLog = !config.features.breakWholeTreeFromOneLog;
			clearAndInit();
		}).dimensions(left, contentTop, contentWidth, 18).build();
		addDrawableChild(treeFellerButton);

		boolean appleEnabled = config.treeFeller.bonusAppleDropsEnabled;
		appleBonusButton = ButtonWidget.builder(Text.literal("Apple Bonus: " + (appleEnabled ? "ON" : "OFF")), b -> {
			config.treeFeller.bonusAppleDropsEnabled = !config.treeFeller.bonusAppleDropsEnabled;
			clearAndInit();
		}).dimensions(left, 0, contentWidth, 18).build();
		addDrawableChild(appleBonusButton);

		appleLabel = createLabel(left, 0, 200, "Apple chance (0.0-1.0)", 1);

		appleChanceField = new TextFieldWidget(textRenderer, left, 0, 70, 20, Text.literal("0.04"));
		appleChanceField.setText(String.valueOf(config.treeFeller.extraAppleDropChance));
		addDrawableChild(appleChanceField);

		boolean caneEnabled = config.features.boostSugarCaneDrops;
		sugarCaneButton = ButtonWidget.builder(Text.literal("Sugar Cane Natural Height (3-4): " + (caneEnabled ? "ON" : "OFF")), b -> {
			config.features.boostSugarCaneDrops = !config.features.boostSugarCaneDrops;
			clearAndInit();
		}).dimensions(left, 0, contentWidth, 18).build();
		addDrawableChild(sugarCaneButton);

		boolean spreadCaneEnabled = config.features.spreadSugarCaneNearExisting;
		sugarSpreadButton = ButtonWidget.builder(Text.literal("Spread Nearby Sugar Cane: " + (spreadCaneEnabled ? "ON" : "OFF")), b -> {
			config.features.spreadSugarCaneNearExisting = !config.features.spreadSugarCaneNearExisting;
			clearAndInit();
		}).dimensions(left, 0, contentWidth, 18).build();
		addDrawableChild(sugarSpreadButton);

		sugarSectionLabel = createLabel(left, 0, contentWidth, "Sugar cane tuning", 1);

		sugarMinHeightField = addSmallField(left, 0, 70, "3", String.valueOf(config.sugarCane.minNaturalHeight));
		sugarMinLabel = createLabel(left + 76, 0, 110, "Min height", 1);
		sugarMaxHeightField = addSmallField(left + 156, 0, 70, "4", String.valueOf(config.sugarCane.maxNaturalHeight));
		sugarMaxLabel = createLabel(left + 232, 0, 90, "Max height", 1);

		sugarSpreadChanceField = addSmallField(left, 0, 70, "0.2", String.valueOf(config.sugarCane.nearbySpreadChance));
		sugarSpreadLabel = createLabel(left + 76, 0, 160, "Nearby spread chance", 1);

		boolean hasLocalServer = client != null && client.getServer() != null;
		boolean legacyEnabled = hasLocalServer
			? LegacyMiningManager.isEnabledForServer(client.getServer())
			: config.features.legacyPre118Mining;
		String legacyLabel = hasLocalServer
			? "Legacy Pre-1.18 Mining (This World): "
			: "Legacy Pre-1.18 Mining (Default for New Worlds): ";
		legacyToggleButton = ButtonWidget.builder(Text.literal(legacyLabel + (legacyEnabled ? "ON" : "OFF")), b -> {
			boolean newValue = !legacyEnabled;
			if (client != null && client.getServer() != null) {
				LegacyMiningManager.setEnabledForServer(client.getServer(), newValue);
			} else {
				config.features.legacyPre118Mining = newValue;
			}
			clearAndInit();
		}).dimensions(left, 0, contentWidth, 18).build();
		addDrawableChild(legacyToggleButton);

		legacySectionLabel = createLabel(left, 0, contentWidth, "Legacy mining tuning", 1);

		legacyCycleTicksField = addSmallField(left, 0, 70, "40", String.valueOf(config.legacyMining.cycleTicks));
		legacyCycleLabel = createLabel(left + 76, 0, 110, "Cycle ticks", 1);
		legacyAttemptsField = addSmallField(left + 156, 0, 70, "6", String.valueOf(config.legacyMining.attemptsPerPlayer));
		legacyAttemptsLabel = createLabel(left + 232, 0, 110, "Attempts/player", 1);

		legacyMinYField = addSmallField(left, 0, 70, "24", String.valueOf(config.legacyMining.minY));
		legacyMinYLabel = createLabel(left + 76, 0, 90, "Min Y", 1);
		legacyMaxYField = addSmallField(left + 156, 0, 70, "112", String.valueOf(config.legacyMining.maxY));
		legacyMaxYLabel = createLabel(left + 232, 0, 90, "Max Y", 1);

		legacyReplacementChanceField = addSmallField(left, 0, 70, "0.35", String.valueOf(config.legacyMining.replacementChance));
		legacyReplacementLabel = createLabel(left + 76, 0, 130, "Replacement chance", 1);
		legacySearchRadiusField = addSmallField(left + 156, 0, 70, "4", String.valueOf(config.legacyMining.seedSearchRadius));
		legacySearchLabel = createLabel(left + 232, 0, 110, "Search radius", 1);

		legacyExistingBoostChanceField = addSmallField(left, 0, 70, "0.7", String.valueOf(config.legacyMining.existingVeinBoostChance));
		legacyExistingBoostLabel = createLabel(left + 76, 0, 150, "Existing vein boost", 1);
		legacyExistingExtraMinField = addSmallField(left + 156, 0, 70, "2", String.valueOf(config.legacyMining.existingVeinExtraMin));
		legacyBoostMinLabel = createLabel(left + 232, 0, 110, "Boost min", 1);

		legacyExistingExtraMaxField = addSmallField(left, 0, 70, "5", String.valueOf(config.legacyMining.existingVeinExtraMax));
		legacyBoostMaxLabel = createLabel(left + 76, 0, 110, "Boost max", 1);
		legacyNewVeinMinField = addSmallField(left + 156, 0, 70, "3", String.valueOf(config.legacyMining.newVeinMinSize));
		legacyNewVeinMinLabel = createLabel(left + 232, 0, 110, "New vein min", 1);

		legacyNewVeinMaxField = addSmallField(left, 0, 70, "7", String.valueOf(config.legacyMining.newVeinMaxSize));
		legacyNewVeinMaxLabel = createLabel(left + 76, 0, 110, "New vein max", 1);

		refreshScrollBounds(left, contentTop, contentBottom);
		repositionContent(left, contentTop);

		addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> {
			applyFields();
			client.setScreen(parent);
		}).dimensions(width / 2 - 104, height - 30, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(parent))
			.dimensions(width / 2 + 4, height - 30, 100, 20).build());
	}

	private void applyFields() {
		try {
			double value = Double.parseDouble(appleChanceField.getText().trim());
			config.treeFeller.extraAppleDropChance = Math.max(0.0, Math.min(1.0, value));
		} catch (NumberFormatException ignored) {
		}

		try {
			int value = Integer.parseInt(sugarMinHeightField.getText().trim());
			config.sugarCane.minNaturalHeight = Math.max(1, value);
		} catch (NumberFormatException ignored) {
		}

		try {
			int value = Integer.parseInt(sugarMaxHeightField.getText().trim());
			config.sugarCane.maxNaturalHeight = Math.max(config.sugarCane.minNaturalHeight, value);
		} catch (NumberFormatException ignored) {
		}

		try {
			double value = Double.parseDouble(sugarSpreadChanceField.getText().trim());
			config.sugarCane.nearbySpreadChance = Math.max(0.0, Math.min(1.0, value));
		} catch (NumberFormatException ignored) {
		}

		try {
			int value = Integer.parseInt(legacyCycleTicksField.getText().trim());
			config.legacyMining.cycleTicks = Math.max(1, value);
		} catch (NumberFormatException ignored) {
		}

		try {
			int value = Integer.parseInt(legacyAttemptsField.getText().trim());
			config.legacyMining.attemptsPerPlayer = Math.max(1, value);
		} catch (NumberFormatException ignored) {
		}

		try {
			int value = Integer.parseInt(legacyMinYField.getText().trim());
			config.legacyMining.minY = value;
		} catch (NumberFormatException ignored) {
		}

		try {
			int value = Integer.parseInt(legacyMaxYField.getText().trim());
			config.legacyMining.maxY = Math.max(config.legacyMining.minY, value);
		} catch (NumberFormatException ignored) {
		}

		try {
			double value = Double.parseDouble(legacyReplacementChanceField.getText().trim());
			config.legacyMining.replacementChance = Math.max(0.0, Math.min(1.0, value));
		} catch (NumberFormatException ignored) {
		}

		try {
			int value = Integer.parseInt(legacySearchRadiusField.getText().trim());
			config.legacyMining.seedSearchRadius = Math.max(1, value);
		} catch (NumberFormatException ignored) {
		}

		try {
			double value = Double.parseDouble(legacyExistingBoostChanceField.getText().trim());
			config.legacyMining.existingVeinBoostChance = Math.max(0.0, Math.min(1.0, value));
		} catch (NumberFormatException ignored) {
		}

		try {
			int value = Integer.parseInt(legacyExistingExtraMinField.getText().trim());
			config.legacyMining.existingVeinExtraMin = Math.max(1, value);
		} catch (NumberFormatException ignored) {
		}

		try {
			int value = Integer.parseInt(legacyExistingExtraMaxField.getText().trim());
			config.legacyMining.existingVeinExtraMax = Math.max(config.legacyMining.existingVeinExtraMin, value);
		} catch (NumberFormatException ignored) {
		}

		try {
			int value = Integer.parseInt(legacyNewVeinMinField.getText().trim());
			config.legacyMining.newVeinMinSize = Math.max(1, value);
		} catch (NumberFormatException ignored) {
		}

		try {
			int value = Integer.parseInt(legacyNewVeinMaxField.getText().trim());
			config.legacyMining.newVeinMaxSize = Math.max(config.legacyMining.newVeinMinSize, value);
		} catch (NumberFormatException ignored) {
		}

	}

	private TextFieldWidget addSmallField(int x, int y, int width, String placeholder, String initialValue) {
		TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 18, Text.literal(placeholder));
		field.setText(initialValue);
		addDrawableChild(field);
		return field;
	}

	private void addLabel(int x, int y, int width, String value) {
		MultilineTextWidget label = new MultilineTextWidget(x, y, Text.literal(value), textRenderer);
		label.setMaxWidth(width);
		label.setMaxRows(1);
		addDrawableChild(label);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (maxScrollOffset <= 0) {
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}

		int delta = (int) Math.signum(verticalAmount) * SCROLL_STEP;
		if (delta == 0) {
			return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
		}

		scrollOffset = Math.max(0, Math.min(maxScrollOffset, scrollOffset - delta));
		repositionContent(width / 2 - 140, CONTENT_TOP);
		return true;
	}

	private void refreshScrollBounds(int left, int top, int bottom) {
		int visibleHeight = bottom - top;
		int totalHeight = 410;
		maxScrollOffset = Math.max(0, totalHeight - visibleHeight);
		scrollOffset = Math.max(0, Math.min(maxScrollOffset, scrollOffset));
	}

	private void repositionContent(int left, int top) {
		int contentWidth = 280;
		int y = top - scrollOffset;
		moveButton(treeFellerButton, left, y, contentWidth); y += 20;
		moveButton(appleBonusButton, left, y, contentWidth); y += 20;
		moveLabel(appleLabel, left, y, 200);
		y += 18;
		moveWidget(appleChanceField, left, y);
		y += 30;

		moveButton(sugarCaneButton, left, y, contentWidth); y += 20;
		moveButton(sugarSpreadButton, left, y, contentWidth); y += 20;
		moveLabel(sugarSectionLabel, left, y);
		y += 20;
		moveFieldWithLabel(sugarMinHeightField, sugarMinLabel, left, left + 76, y, 70, 110);
		moveFieldWithLabel(sugarMaxHeightField, sugarMaxLabel, left + 156, left + 232, y, 70, 90);
		y += 22;
		moveFieldWithLabel(sugarSpreadChanceField, sugarSpreadLabel, left, left + 76, y, 70, 160);
		y += 30;

		moveButton(legacyToggleButton, left, y, contentWidth); y += 20;
		moveLabel(legacySectionLabel, left, y);
		y += 20;
		moveFieldWithLabel(legacyCycleTicksField, legacyCycleLabel, left, left + 76, y, 70, 110);
		moveFieldWithLabel(legacyAttemptsField, legacyAttemptsLabel, left + 156, left + 232, y, 70, 110);
		y += 22;
		moveFieldWithLabel(legacyMinYField, legacyMinYLabel, left, left + 76, y, 70, 90);
		moveFieldWithLabel(legacyMaxYField, legacyMaxYLabel, left + 156, left + 232, y, 70, 90);
		y += 22;
		moveFieldWithLabel(legacyReplacementChanceField, legacyReplacementLabel, left, left + 76, y, 70, 130);
		moveFieldWithLabel(legacySearchRadiusField, legacySearchLabel, left + 156, left + 232, y, 70, 110);
		y += 22;
		moveFieldWithLabel(legacyExistingBoostChanceField, legacyExistingBoostLabel, left, left + 76, y, 70, 150);
		moveFieldWithLabel(legacyExistingExtraMinField, legacyBoostMinLabel, left + 156, left + 232, y, 70, 110);
		y += 22;
		moveFieldWithLabel(legacyExistingExtraMaxField, legacyBoostMaxLabel, left, left + 76, y, 70, 110);
		moveFieldWithLabel(legacyNewVeinMinField, legacyNewVeinMinLabel, left + 156, left + 232, y, 70, 110);
		y += 22;
		moveFieldWithLabel(legacyNewVeinMaxField, legacyNewVeinMaxLabel, left, left + 76, y, 70, 110);
	}

	private void moveButton(ButtonWidget button, int x, int y, int width) {
		if (button != null) {
			button.setX(x);
			button.setY(y);
			button.setWidth(width);
		}
	}

	private void moveWidget(TextFieldWidget widget, int x, int y) {
		if (widget != null) {
			widget.setX(x);
			widget.setY(y);
		}
	}

	private void moveLabel(MultilineTextWidget label, int x, int y) {
		if (label != null) {
			label.setX(x);
			label.setY(y);
		}
	}

	private void moveLabel(MultilineTextWidget label, int x, int y, int width) {
		if (label != null) {
			label.setX(x);
			label.setY(y);
			label.setMaxWidth(width);
		}
	}

	private void moveFieldWithLabel(TextFieldWidget field, MultilineTextWidget label, int fieldX, int labelX, int y, int fieldWidth, int labelWidth) {
		if (field != null) {
			field.setX(fieldX);
			field.setY(y);
			field.setWidth(fieldWidth);
		}
		if (label != null) {
			label.setX(labelX);
			label.setY(y + 4);
			label.setMaxWidth(labelWidth);
		}
	}

	private MultilineTextWidget createLabel(int x, int y, int width, String value, int rows) {
		MultilineTextWidget label = new MultilineTextWidget(x, y, Text.literal(value), textRenderer);
		label.setMaxWidth(width);
		label.setMaxRows(rows);
		addDrawableChild(label);
		return label;
	}
}
