package fr.isaac.cutcleanreborn.client;

import fr.isaac.cutcleanreborn.config.CutCleanConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Paged, 7 by 3 selector for every vanilla passive spawn group. */
public class AnimalDropsScreen extends Screen {
	static final int COLUMNS = 7;
	static final int ROWS = 3;
	static final int PAGE_SIZE = COLUMNS * ROWS;
	private final Screen parent;
	private final CutCleanConfig config;
	private final List<Identifier> passiveMobs = new ArrayList<>();
	private int page;

	public AnimalDropsScreen(Screen parent, CutCleanConfig config) {
		super(Text.literal("Cleaner Mobs Drops"));
		this.parent = parent;
		this.config = config;
		for (Identifier id : Registries.ENTITY_TYPE.getIds()) {
			EntityType<?> type = Registries.ENTITY_TYPE.get(id);
			SpawnGroup group = type.getSpawnGroup();
			if (group == SpawnGroup.CREATURE || group == SpawnGroup.AXOLOTLS || group == SpawnGroup.WATER_CREATURE || group == SpawnGroup.WATER_AMBIENT) passiveMobs.add(id);
		}
		passiveMobs.sort(Comparator.comparing(Identifier::toString));
	}

	@Override protected void init() {
		int left = 12, right = width - 12, top = 50, gap = 4;
		int cellWidth = Math.max(52, (right - left - gap * 6) / COLUMNS), cellHeight = 38;
		int pages = Math.max(1, (passiveMobs.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		page = Math.max(0, Math.min(page, pages - 1));
		boolean enabled = config.features.customAnimalExtraDrops;
		addDrawableChild(ButtonWidget.builder(Text.literal("Custom mob drops: " + (enabled ? "ON" : "OFF")), b -> { config.features.customAnimalExtraDrops = !enabled; clearAndInit(); }).dimensions(left, 28, right - left, 16).build());
		for (int n = 0; n < PAGE_SIZE && page * PAGE_SIZE + n < passiveMobs.size(); n++) {
			Identifier id = passiveMobs.get(page * PAGE_SIZE + n);
			int x = left + (n % COLUMNS) * (cellWidth + gap), y = top + (n / COLUMNS) * (cellHeight + gap);
			String name = Registries.ENTITY_TYPE.get(id).getName().getString();
			addDrawableChild(ButtonWidget.builder(Text.literal(shorten(name, 12)), b -> client.setScreen(new AnimalDropEntriesScreen(this, config, id))).dimensions(x, y, cellWidth, cellHeight).build());
		}
		ButtonWidget previous = addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> { page--; clearAndInit(); }).dimensions(left, height - 54, 28, 20).build()); previous.active = page > 0;
		ButtonWidget next = addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> { page++; clearAndInit(); }).dimensions(right - 28, height - 54, 28, 20).build()); next.active = page < pages - 1;
		ButtonWidget pageLabel = addDrawableChild(ButtonWidget.builder(Text.literal("Page " + (page + 1) + "/" + pages), b -> {}).dimensions(width / 2 - 50, height - 54, 100, 20).build()); pageLabel.active = false;
		addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(parent)).dimensions(width / 2 - 50, height - 28, 100, 20).build());
	}

	static String shorten(String value, int maximum) { return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…"; }
	@Override public void render(DrawContext context, int mouseX, int mouseY, float delta) { context.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010); context.fill(10, 48, width - 10, height - 62, 0x80101010); context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF); super.render(context, mouseX, mouseY, delta); }
}
