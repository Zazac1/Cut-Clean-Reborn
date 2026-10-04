package fr.isaac.cutcleanreborn.client;

import fr.isaac.cutcleanreborn.config.CutCleanConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Displays configured drops in a 7 by 3 grid. Right-click a cell to remove it. */
public class AnimalDropEntriesScreen extends Screen {
	private final Screen parent;
	private final Identifier entityId;
	private final List<CutCleanConfig.ExtraDropEntry> drops;
	private int page;
	private TextFieldWidget item, min, max, chance;

	public AnimalDropEntriesScreen(Screen parent, CutCleanConfig config, Identifier entityId) {
		super(Text.literal("Drops: " + Registries.ENTITY_TYPE.get(entityId).getName().getString()));
		this.parent = parent;
		this.entityId = entityId;
		this.drops = config.animalExtraDrops.computeIfAbsent(entityId.toString(), ignored -> new ArrayList<>());
	}

	@Override protected void init() {
		int left = 12, right = width - 12, gap = 4, top = 50;
		int cellWidth = Math.max(52, (right - left - gap * 6) / AnimalDropsScreen.COLUMNS), cellHeight = 38;
		int pages = Math.max(1, (drops.size() + AnimalDropsScreen.PAGE_SIZE - 1) / AnimalDropsScreen.PAGE_SIZE);
		page = Math.max(0, Math.min(page, pages - 1));
		item = addDrawableChild(field(left, 28, 170, "minecraft:leather"));
		min = addDrawableChild(field(left + 176, 28, 42, "1"));
		max = addDrawableChild(field(left + 224, 28, 42, "2"));
		chance = addDrawableChild(field(left + 272, 28, 58, "1.0"));
		addDrawableChild(ButtonWidget.builder(Text.literal("Add"), b -> addDrop()).dimensions(right - 58, 27, 58, 20).build());
		for (int n = 0; n < AnimalDropsScreen.PAGE_SIZE && page * AnimalDropsScreen.PAGE_SIZE + n < drops.size(); n++) {
			CutCleanConfig.ExtraDropEntry drop = drops.get(page * AnimalDropsScreen.PAGE_SIZE + n);
			int x = left + (n % AnimalDropsScreen.COLUMNS) * (cellWidth + gap), y = top + (n / AnimalDropsScreen.COLUMNS) * (cellHeight + gap);
			String id = drop.itemId == null ? "invalid" : drop.itemId.replace("minecraft:", "");
			ButtonWidget cell = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {}).dimensions(x, y, cellWidth, cellHeight).build());
			cell.active = false;
		}
		ButtonWidget previous = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page--; clearAndInit(); }).dimensions(left, height - 54, 28, 20).build()); previous.active = page > 0;
		ButtonWidget next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page++; clearAndInit(); }).dimensions(right - 28, height - 54, 28, 20).build()); next.active = page < pages - 1;
		ButtonWidget pageLabel = addDrawableChild(ButtonWidget.builder(Text.literal("Page " + (page + 1) + "/" + pages), b -> {}).dimensions(width / 2 - 50, height - 54, 100, 20).build()); pageLabel.active = false;
		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(parent)).dimensions(width / 2 - 50, height - 28, 100, 20).build());
	}

	private TextFieldWidget field(int x, int y, int width, String placeholder) { TextFieldWidget field = new TextFieldWidget(textRenderer, x, y, width, 18, Text.empty()); field.setPlaceholder(Text.literal(placeholder)); return field; }
	private void addDrop() { if (item.getText().trim().isEmpty()) return; CutCleanConfig.ExtraDropEntry entry = new CutCleanConfig.ExtraDropEntry(); entry.itemId = item.getText().trim(); entry.minCount = Math.max(0, integer(min.getText(), 1)); entry.maxCount = Math.max(entry.minCount, integer(max.getText(), entry.minCount)); entry.chance = Math.max(0, Math.min(1, decimal(chance.getText(), 1))); drops.add(entry); clearAndInit(); }
	private static int integer(String value, int fallback) { try { return Integer.parseInt(value.trim()); } catch (NumberFormatException ignored) { return fallback; } }
	private static double decimal(String value, double fallback) { try { return Double.parseDouble(value.trim()); } catch (NumberFormatException ignored) { return fallback; } }

	@Override public boolean mouseClicked(Click click, boolean doubledClick) {
		if (click.button() == 1) {
			double mouseX = click.x(), mouseY = click.y();
			int left = 12, right = width - 12, gap = 4, top = 50, cellHeight = 38;
			int cellWidth = Math.max(52, (right - left - gap * 6) / AnimalDropsScreen.COLUMNS);
			if (mouseX >= left && mouseY >= top && mouseY < top + AnimalDropsScreen.ROWS * (cellHeight + gap)) {
				int column = (int) ((mouseX - left) / (cellWidth + gap)), row = (int) ((mouseY - top) / (cellHeight + gap));
				if (column >= 0 && column < AnimalDropsScreen.COLUMNS && row >= 0 && row < AnimalDropsScreen.ROWS && mouseX < left + column * (cellWidth + gap) + cellWidth && mouseY < top + row * (cellHeight + gap) + cellHeight) {
					int index = page * AnimalDropsScreen.PAGE_SIZE + row * AnimalDropsScreen.COLUMNS + column;
					if (index < drops.size()) { drops.remove(index); clearAndInit(); return true; }
				}
			}
		}
		return super.mouseClicked(click, doubledClick);
	}

	@Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
		context.fill(10, 48, width - 10, height - 62, 0x80101010);
		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
		context.drawCenteredTextWithShadow(textRenderer, "Right-click a drop to remove it", width / 2, height - 76, 0xAAAAAA);
		super.render(context, mouseX, mouseY, delta);
		int left = 12, right = width - 12, gap = 4, top = 50, cellHeight = 38;
		int cellWidth = Math.max(52, (right - left - gap * 6) / AnimalDropsScreen.COLUMNS);
		for (int n = 0; n < AnimalDropsScreen.PAGE_SIZE && page * AnimalDropsScreen.PAGE_SIZE + n < drops.size(); n++) {
			CutCleanConfig.ExtraDropEntry drop = drops.get(page * AnimalDropsScreen.PAGE_SIZE + n);
			int x = left + (n % AnimalDropsScreen.COLUMNS) * (cellWidth + gap), y = top + (n / AnimalDropsScreen.COLUMNS) * (cellHeight + gap);
			String id = drop.itemId == null ? "invalid" : drop.itemId.replace("minecraft:", "");
			context.drawCenteredTextWithShadow(textRenderer, AnimalDropsScreen.shorten(id, 11), x + cellWidth / 2, y + 8, 0xFFFFFF);
			context.drawCenteredTextWithShadow(textRenderer, drop.minCount + "-" + drop.maxCount + " | " + Math.round(drop.chance * 100) + "%", x + cellWidth / 2, y + 20, 0xAAAAAA);
		}
	}
}
