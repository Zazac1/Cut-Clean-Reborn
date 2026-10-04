package fr.isaac.cutcleanreborn.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class EnchantRuleListScreen extends Screen {
	private final Screen parent;
	private final String titleLabel;
	private final Consumer<Map<String, Map<String, Integer>>> onSave;
	private final List<String> lines = new ArrayList<>();

	private TextFieldWidget scopeField;
	private TextFieldWidget enchantField;
	private TextFieldWidget levelField;
	private int scroll = 0;

	public EnchantRuleListScreen(Screen parent, String titleLabel, Map<String, Map<String, Integer>> source, Consumer<Map<String, Map<String, Integer>>> onSave) {
		super(Text.literal(titleLabel));
		this.parent = parent;
		this.titleLabel = titleLabel;
		this.onSave = onSave;
		for (Map.Entry<String, Map<String, Integer>> scopeEntry : source.entrySet()) {
			for (Map.Entry<String, Integer> enchantEntry : scopeEntry.getValue().entrySet()) {
				lines.add(scopeEntry.getKey() + "|" + enchantEntry.getKey() + "|" + Math.max(1, enchantEntry.getValue()));
			}
		}
	}

	@Override
	protected void init() {
		int left = 12;
		int right = width - 12;
		int rowStart = 76;
		int rowHeight = 20;
		int listHeight = height - 152;
		int maxVisible = Math.max(1, listHeight / rowHeight);
		int maxScroll = Math.max(0, lines.size() - maxVisible);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		MultilineTextWidget titleWidget = new MultilineTextWidget(left, 10, Text.literal(titleLabel), textRenderer);
		titleWidget.setMaxWidth(width - 24);
		titleWidget.setCentered(true);
		addDrawableChild(titleWidget);

		MultilineTextWidget hint = new MultilineTextWidget(left, 30, Text.literal("Format: scope + enchant id + level"), textRenderer);
		hint.setMaxWidth(width - 24);
		hint.setCentered(true);
		hint.setMaxRows(1);
		addDrawableChild(hint);

		scopeField = new TextFieldWidget(textRenderer, left, 50, 140, 18, Text.literal("scope"));
		scopeField.setPlaceholder(Text.literal("wood or minecraft:iron_pickaxe"));
		addDrawableChild(scopeField);
		enchantField = new TextFieldWidget(textRenderer, left + 146, 50, 170, 18, Text.literal("enchant"));
		enchantField.setPlaceholder(Text.literal("minecraft:efficiency"));
		addDrawableChild(enchantField);
		levelField = new TextFieldWidget(textRenderer, left + 322, 50, 52, 18, Text.literal("level"));
		levelField.setPlaceholder(Text.literal("3"));
		addDrawableChild(levelField);

		addDrawableChild(ButtonWidget.builder(Text.literal("Add"), b -> addRule())
			.dimensions(right - 80, 50, 80, 18).build());

		for (int i = scroll; i < Math.min(lines.size(), scroll + maxVisible); i++) {
			int y = rowStart + (i - scroll) * rowHeight;
			MultilineTextWidget row = new MultilineTextWidget(left + 4, y + 6, Text.literal(lines.get(i)), textRenderer);
			row.setMaxWidth(right - left - 70);
			row.setMaxRows(1);
			addDrawableChild(row);

			int idx = i;
			addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
				lines.remove(idx);
				clearAndInit();
			}).dimensions(right - 24, y + 1, 20, 18).build());
		}

		addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> saveAndClose())
			.dimensions(width / 2 - 104, height - 30, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(parent))
			.dimensions(width / 2 + 4, height - 30, 100, 20).build());
	}

	private void addRule() {
		String scope = scopeField.getText().trim();
		String enchant = enchantField.getText().trim();
		if (scope.isEmpty() || enchant.isEmpty()) {
			return;
		}
		int level = parseInt(levelField.getText().trim(), 1);
		lines.add(scope + "|" + enchant + "|" + Math.max(1, level));
		scopeField.setText("");
		enchantField.setText("");
		levelField.setText("");
		clearAndInit();
	}

	private void saveAndClose() {
		Map<String, Map<String, Integer>> rebuilt = new LinkedHashMap<>();
		for (String line : lines) {
			String[] split = line.split("\\|", 3);
			if (split.length < 3) {
				continue;
			}
			String scope = split[0].trim();
			String enchant = split[1].trim();
			if (scope.isEmpty() || enchant.isEmpty()) {
				continue;
			}
			int level = Math.max(1, parseInt(split[2].trim(), 1));
			rebuilt.computeIfAbsent(scope, k -> new LinkedHashMap<>()).put(enchant, level);
		}
		onSave.accept(rebuilt);
		client.setScreen(parent);
	}

	private static int parseInt(String raw, int fallback) {
		try {
			return Integer.parseInt(raw);
		} catch (NumberFormatException exception) {
			return fallback;
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int maxVisible = Math.max(1, (height - 152) / 20);
		int maxScroll = Math.max(0, lines.size() - maxVisible);
		scroll = Math.max(0, Math.min(scroll - (int) verticalAmount, maxScroll));
		clearAndInit();
		return true;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
		context.fill(10, 74, width - 10, height - 78, 0x80101010);
		super.render(context, mouseX, mouseY, delta);
	}
}
