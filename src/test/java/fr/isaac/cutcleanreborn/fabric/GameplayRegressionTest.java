package fr.isaac.cutcleanreborn.fabric;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GameplayRegressionTest {
  @TempDir Path temp;

  @Test
  void frenchPercentagesAndZeroAreAccepted() {
    assertEquals(4.5, GameplayMath.percentage(" 4,5 % "));
    assertEquals(0.0, GameplayMath.percentage("0"));
    assertEquals(100.0, GameplayMath.percentage("100%"));
  }

  @Test
  void nonFiniteAndOutOfRangeRejected() {
    for (String text : new String[] {"NaN", "Infinity", "-1", "100.01", "", "apple"})
      assertNull(GameplayMath.percentage(text), text);
    assertEquals(0.5, GameplayMath.percent(Double.NaN, 0.5));
  }

  @Test
  void quantityRangesValidated() {
    assertEquals(new GameplayMath.AmountRange(2, 5), GameplayMath.range("2 - 5", 0, 64));
    assertEquals(new GameplayMath.AmountRange(0, 0), GameplayMath.range("0", 0, 64));
    for (String text : new String[] {"5-2", "1-65", "1-2-3", "-1", "", "2147483647"})
      assertNull(GameplayMath.range(text, 0, 64), text);
    assertNull(GameplayMath.range("0", 1, 64));
  }

  @Test
  void fortuneCanProduceFourTimes() {
    Random random = new Random(34);
    HashSet<Integer> seen = new HashSet<>();
    for (int attempt = 0; attempt < 1000; attempt++)
      seen.add(GameplayMath.fortuneMultiplier(3, random));
    assertEquals(java.util.Set.of(1, 2, 3, 4), seen);
    assertEquals(1, GameplayMath.fortuneMultiplier(0, random));
  }

  @Test
  void lootingAddsNotMultiplies() {
    Random random = new Random(73);
    HashSet<Integer> seen = new HashSet<>();
    for (int attempt = 0; attempt < 1000; attempt++) seen.add(GameplayMath.lootingBonus(3, random));
    assertEquals(java.util.Set.of(0, 1, 2, 3), seen);
    assertEquals(0, GameplayMath.lootingBonus(0, random));
  }

  @Test
  void xpSaturates() {
    assertEquals(Integer.MAX_VALUE, GameplayMath.addXp(Integer.MAX_VALUE - 2, 100));
    assertEquals(12, GameplayMath.addXp(7, 5));
  }

  @Test
  void legacyYamlPathsRoundTrip() throws Exception {
    Settings config = new Settings(temp.resolve("config.yml"));
    config.load();
    config.set("mobs.COW.cooked-food", false);
    config.set("automatic-enchantments.tools.DIAMOND_AXE.minecraft:unbreaking", 3);
    config.set("clean-stacks.players.00000000-0000-0000-0000-000000000001", true);
    config.save();
    config.load();
    assertFalse(config.bool("mobs.COW.cooked-food", true));
    assertEquals(
        3,
        config.integer("automatic-enchantments.tools.DIAMOND_AXE.minecraft:unbreaking", 0, 0, 255));
    assertTrue(config.bool("clean-stacks.players.00000000-0000-0000-0000-000000000001", false));
  }

  @Test
  void inheritedDefaultsSurvivePartialConfig() throws Exception {
    Settings config = new Settings(temp.resolve("config.yml"));
    config.load();
    config.set("tree-cutter", null);
    assertTrue(config.bool("tree-cutter.enabled", false));
    assertEquals(256, config.integer("tree-cutter.max-logs", 0, 2, 2048));
  }

  @Test
  void snapshotsAreIndependent() throws Exception {
    Settings config = new Settings(temp.resolve("config.yml"));
    config.load();
    Map<String, Object> snapshot = config.snapshot();
    config.set("tree-cutter.enabled", false);
    config.restore(snapshot);
    assertTrue(config.bool("tree-cutter.enabled", false));
  }

  @Test
  void invalidYamlLeavesOriginalFileIntact() throws Exception {
    Path file = temp.resolve("config.yml");
    java.nio.file.Files.writeString(file, "mobs: [\n");
    Settings config = new Settings(file);
    assertThrows(RuntimeException.class, config::load);
    assertEquals("mobs: [\n", java.nio.file.Files.readString(file));
  }

  @Test
  void numericReadersBoundUntrustedValues() throws Exception {
    Settings config = new Settings(temp.resolve("config.yml"));
    config.load();
    config.set("test.number", Double.NaN);
    assertEquals(0.5, config.decimal("test.number", 0.5, 0, 100));
    config.set("test.integer", 9999);
    assertEquals(64, config.integer("test.integer", 1, 1, 64));
  }

  @Test
  void listEntriesAreDetachedBeforeEdit() throws Exception {
    Settings config = new Settings(temp.resolve("config.yml"));
    config.load();
    config.set("test.rules", java.util.List.of(Map.of("minimum", 1)));
    var copy = config.entries("test.rules");
    copy.getFirst().put("minimum", 64);
    assertEquals(1, Settings.number(config.entries("test.rules").getFirst(), "minimum", 0));
  }

  @Test
  void copiedNestedSectionsAreIndependentAndMutable() throws Exception {
    Settings config = new Settings(temp.resolve("config.yml"));
    config.load();
    config.set(
        "ore-drops.iron_ore",
        Map.of("drops", java.util.List.of(Map.of("item", "diamond", "minimum", 1))));
    config.set("ore-drops.deepslate_iron_ore", config.section("ore-drops.iron_ore"));
    config.set("ore-drops.deepslate_iron_ore.extra.value", 7);
    assertFalse(config.contains("ore-drops.iron_ore.extra.value"));
    config.set("nested.source", Map.of("child", Map.of("level", 2)));
    config.set("nested.destination", config.section("nested.source"));
    config.set("nested.destination.child.level", 5);
    assertEquals(2, config.integer("nested.source.child.level", 0, 0, 255));
    assertEquals(5, config.integer("nested.destination.child.level", 0, 0, 255));
  }

  @Test
  void newSoloWorldDoesNotInheritAnotherMace() throws Exception {
    Settings config = new Settings(temp.resolve("config.yml"));
    config.load();
    config.set("game-rules.stuff-rules.unique-mace.exists", true);
    config.set("game-rules.stuff-rules.unique-mace.id", "world-one");
    WorldState first = new WorldState(temp.resolve("world-one/data/state.json"));
    first.save(config);
    new WorldState(temp.resolve("world-two/data/state.json")).load(config, false);
    assertFalse(config.bool("game-rules.stuff-rules.unique-mace.exists", true));
    first.load(config, false);
    assertTrue(config.bool("game-rules.stuff-rules.unique-mace.exists", false));
    assertEquals("world-one", config.text("game-rules.stuff-rules.unique-mace.id", ""));
  }

  @Test
  void dedicatedLegacyMaceStateMigratesOnce() throws Exception {
    Settings config = new Settings(temp.resolve("config.yml"));
    config.load();
    config.set("game-rules.stuff-rules.unique-mace.exists", true);
    config.set("game-rules.stuff-rules.unique-mace.id", "legacy-mace");
    WorldState state = new WorldState(temp.resolve("world/data/state.json"));
    state.load(config, true);
    assertTrue(config.bool("game-rules.stuff-rules.unique-mace.exists", false));
    state.save(config);
    config.set("game-rules.stuff-rules.unique-mace.exists", false);
    state.load(config, true);
    assertTrue(config.bool("game-rules.stuff-rules.unique-mace.exists", false));
  }
}
