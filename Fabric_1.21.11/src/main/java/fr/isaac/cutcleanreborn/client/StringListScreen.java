package fr.isaac.cutcleanreborn.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StringListScreen extends Screen {
	private final Screen parent;
	private final String titleLabel;
	private final List<String> entries;
	private final Consumer<List<String>> onSave;

	private TextFieldWidget valueField;
	private int scroll = 0;

	public StringListScreen(Screen parent, String titleLabel, List<String> source, Consumer<List<String>> onSave) {
		super(Text.literal(titleLabel));
		this.parent = parent;
		this.titleLabel = titleLabel;
		this.onSave = onSave;
		this.entries = new ArrayList<>(source);
	}

	@Override
	protected void init() {
		int left = 12;
		int right = width - 12;
		int rowStart = 52;
		int rowHeight = 20;
		int listHeight = height - 128;
		int maxVisible = Math.max(1, listHeight / rowHeight);
		int maxScroll = Math.max(0, entries.size() - maxVisible);
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		MultilineTextWidget titleWidget = new MultilineTextWidget(left, 10, Text.literal(titleLabel), textRenderer);
		titleWidget.setMaxWidth(width - 24);
		titleWidget.setCentered(true);
		addDrawableChild(titleWidget);

		valueField = new TextFieldWidget(textRenderer, left, 28, right - left - 84, 18, Text.literal("Valeur"));
		valueField.setPlaceholder(Text.literal("minecraft:wooden_pickaxe"));
		addDrawableChild(valueField);

		addDrawableChild(ButtonWidget.builder(Text.literal("Add"), b -> addEntry())
			.dimensions(right - 80, 28, 80, 18).build());

		for (int i = scroll; i < Math.min(entries.size(), scroll + maxVisible); i++) {
			int y = rowStart + (i - scroll) * rowHeight;
			String value = entries.get(i);
			MultilineTextWidget row = new MultilineTextWidget(left + 4, y + 6, Text.literal(value), textRenderer);
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
		String value = valueField.getText().trim();
		if (value.isEmpty() || entries.contains(value)) {
			return;
		}
		entries.add(value);
		valueField.setText("");
		clearAndInit();
	}

	private void saveAndClose() {
		onSave.accept(new ArrayList<>(entries));
		client.setScreen(parent);
	}

	private void closeWithoutSave() {
		client.setScreen(parent);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		int maxVisible = Math.max(1, (height - 128) / 20);
		int maxScroll = Math.max(0, entries.size() - maxVisible);
		scroll = Math.max(0, Math.min(scroll - (int) verticalAmount, maxScroll));
		clearAndInit();
		return true;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
		context.fill(10, 50, width - 10, height - 78, 0x80101010);
		super.render(context, mouseX, mouseY, delta);
	}
}
