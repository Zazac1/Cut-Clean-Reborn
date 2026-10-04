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
import java.util.function.Supplier;

public class KeyValueListScreen extends Screen {
	private final Screen parent;
	private final String titleLabel;
	private final List<Map.Entry<String, String>> entries;
	private final Consumer<Map<String, String>> onSave;
	private final String featureLabel;
	private final Supplier<Boolean> featureGetter;
	private final Consumer<Boolean> featureSetter;

	private TextFieldWidget keyField;
	private TextFieldWidget valueField;
	private int scroll = 0;

	public KeyValueListScreen(Screen parent, String titleLabel, Map<String, String> source, Consumer<Map<String, String>> onSave) {
		this(parent, titleLabel, source, onSave, null, null, null);
	}

	public KeyValueListScreen(
		Screen parent,
		String titleLabel,
		Map<String, String> source,
		Consumer<Map<String, String>> onSave,
		String featureLabel,
		Supplier<Boolean> featureGetter,
		Consumer<Boolean> featureSetter
	) {
		super(Text.literal(titleLabel));
		this.parent = parent;
		this.titleLabel = titleLabel;
		this.onSave = onSave;
		this.entries = new ArrayList<>(source.entrySet());
		this.featureLabel = featureLabel;
		this.featureGetter = featureGetter;
		this.featureSetter = featureSetter;
	}

	@Override
	protected void init() {
		int left = 12;
		int right = width - 12;
		int controlsY = 28;
		int rowStart = 52;
		int rowHeight = 20;
		int listBottomMargin = 78;
		if (featureGetter != null && featureSetter != null && featureLabel != null) {
			listBottomMargin = 78;
		}

		if (featureGetter != null && featureSetter != null && featureLabel != null) {
			boolean enabled = Boolean.TRUE.equals(featureGetter.get());
			addDrawableChild(ButtonWidget.builder(Text.literal(featureLabel + ": " + (enabled ? "ON" : "OFF")), b -> {
				featureSetter.accept(!enabled);
				clearAndInit();
			}).dimensions(left, controlsY, right - left, 18).build());
			controlsY += 24;
			rowStart += 24;
		}

		int listHeight = height - (rowStart + listBottomMargin);
		int maxVisible = Math.max(1, listHeight / rowHeight);
		int maxScroll = Math.max(0, entries.size() - maxVisible);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		MultilineTextWidget titleWidget = new MultilineTextWidget(left, 10, Text.literal(titleLabel), textRenderer);
		titleWidget.setMaxWidth(width - 24);
		titleWidget.setCentered(true);
		addDrawableChild(titleWidget);

		int keyWidth = (right - left - 8) / 2;
		keyField = new TextFieldWidget(textRenderer, left, controlsY, keyWidth, 18, Text.literal("Input"));
		keyField.setPlaceholder(Text.literal("minecraft:item_source"));
		addDrawableChild(keyField);

		valueField = new TextFieldWidget(textRenderer, left + keyWidth + 8, controlsY, keyWidth, 18, Text.literal("Output"));
		valueField.setPlaceholder(Text.literal("minecraft:item_target"));
		addDrawableChild(valueField);

		addDrawableChild(ButtonWidget.builder(Text.literal("Add"), b -> addEntry())
			.dimensions(right - 80, height - 72, 80, 20).build());

		for (int i = scroll; i < Math.min(entries.size(), scroll + maxVisible); i++) {
			int y = rowStart + (i - scroll) * rowHeight;
			Map.Entry<String, String> entry = entries.get(i);
			String label = entry.getKey() + " -> " + entry.getValue();
			MultilineTextWidget row = new MultilineTextWidget(left + 4, y + 6, Text.literal(label), textRenderer);
			row.setMaxWidth(right - left - 70);
			row.setMaxRows(1);
			addDrawableChild(row);

			int idx = i;
			addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
				entries.remove(idx);
				clearAndInit();
			}).dimensions(right - 24, y + 1, 20, 18).build());
		}

		addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> saveAndClose())
			.dimensions(width / 2 - 104, height - 30, 100, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> closeWithoutSave())
			.dimensions(width / 2 + 4, height - 30, 100, 20).build());
	}

	private void addEntry() {
		String key = keyField.getText().trim();
		String value = valueField.getText().trim();
		if (key.isEmpty() || value.isEmpty()) {
			return;
		}

		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i).getKey().equals(key)) {
				entries.set(i, Map.entry(key, value));
				keyField.setText("");
				valueField.setText("");
				clearAndInit();
				return;
			}
		}

		entries.add(Map.entry(key, value));
		keyField.setText("");
		valueField.setText("");
		clearAndInit();
	}

	private void saveAndClose() {
		Map<String, String> result = new LinkedHashMap<>();
		for (Map.Entry<String, String> entry : entries) {
			if (!entry.getKey().isBlank() && !entry.getValue().isBlank()) {
				result.put(entry.getKey(), entry.getValue());
			}
		}
		onSave.accept(result);
		client.setScreen(parent);
	}

	private void closeWithoutSave() {
		client.setScreen(parent);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int rowStart = 52;
		if (featureGetter != null && featureSetter != null && featureLabel != null) {
			rowStart += 24;
		}
		int maxVisible = Math.max(1, (height - (rowStart + 78)) / 20);
		int maxScroll = Math.max(0, entries.size() - maxVisible);
		scroll = Math.max(0, Math.min(scroll - (int) verticalAmount, maxScroll));
		clearAndInit();
		return true;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		int panelTop = featureGetter != null && featureSetter != null && featureLabel != null ? 74 : 50;
		context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
		context.fill(10, panelTop, width - 10, height - 78, 0x80101010);
		super.render(context, mouseX, mouseY, delta);
	}
}
