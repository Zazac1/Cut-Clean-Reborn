package fr.isaac.cutcleanreborn.fabric;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuleConfigTest {
  @TempDir Path directory;

  private java.io.InputStream defaults() {
    return new ByteArrayInputStream(
        "tree-cutter:\n  enabled: true\nautomatic-enchantments:\n  enabled: true\n"
            .getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void roundTripKeepsPaperPathsAndUnknownKeys() {
    Path path = directory.resolve("config.yml");
    RuleConfig config = new RuleConfig(path, defaults());
    assertTrue(config.bool("tree-cutter.enabled", false));
    config.set(
        "mobs.COW.drops",
        List.of(Map.of("item", "minecraft:diamond", "minimum", 1, "maximum", 3, "chance", 45.5)));
    config.set("automatic-enchantments.tools.DIAMOND_SWORD.minecraft:sharpness", 3);
    config.set("custom-extension.value", "kept");
    RuleConfig reloaded = new RuleConfig(path, defaults());
    assertEquals(
        3,
        reloaded.integer(
            "automatic-enchantments.tools.DIAMOND_SWORD.minecraft:sharpness", 0, 0, 255));
    assertEquals(45.5, reloaded.entries("mobs.COW.drops").getFirst().get("chance"));
    assertEquals("kept", reloaded.text("custom-extension.value", ""));
    assertTrue(reloaded.bool("tree-cutter.enabled", false));
  }

  @Test
  void atomicBatchAndReset() {
    RuleConfig config = new RuleConfig(directory.resolve("config.yml"), defaults());
    config.batch(
        Map.of(
            "game-rules.stuff-rules.unique-mace.exists",
            true,
            "game-rules.stuff-rules.unique-mace.id",
            "test-id"));
    config.set("tree-cutter.enabled", false);
    assertFalse(config.bool("tree-cutter.enabled", true));
    config.set("tree-cutter", null);
    assertTrue(config.bool("tree-cutter.enabled", false));
    assertEquals("test-id", config.text("game-rules.stuff-rules.unique-mace.id", ""));
    assertFalse(Files.exists(directory.resolve("config.yml.tmp")));
  }

  @Test
  void copiedSectionsAndImmutableInputsRemainIndependent() {
    RuleConfig config = new RuleConfig(directory.resolve("config.yml"), defaults());
    config.set(
        "ore-drops.iron_ore",
        Map.of("nested", Map.of("value", 2), "drops", List.of(Map.of("chance", 100))));
    config.set("ore-drops.deepslate_iron_ore", config.section("ore-drops.iron_ore"));
    config.set("ore-drops.deepslate_iron_ore.nested.value", 5);
    assertEquals(2, config.integer("ore-drops.iron_ore.nested.value", 0, 0, 100));
    assertEquals(5, config.integer("ore-drops.deepslate_iron_ore.nested.value", 0, 0, 100));
    @SuppressWarnings("unchecked")
    var copiedDrops = (List<Map<String, Object>>) config.get("ore-drops.deepslate_iron_ore.drops");
    copiedDrops.getFirst().put("chance", 0);
    assertEquals(100, config.entries("ore-drops.iron_ore.drops").getFirst().get("chance"));
  }

  @Test
  void invalidYamlNeverReplaced() throws Exception {
    Path path = directory.resolve("config.yml");
    String invalid = "root: [unterminated";
    Files.writeString(path, invalid);
    assertThrows(IllegalStateException.class, () -> new RuleConfig(path, defaults()));
    assertEquals(invalid, Files.readString(path));
  }

  @Test
  void stableRuleEditsFollowIdentityAfterAnotherAdminDeletesEarlierRule() {
    RuleConfig config = new RuleConfig(directory.resolve("config.yml"), defaults());
    String path = "game-rules.stuff-rules.armor-rules";
    config.set(
        path,
        List.of(
            Map.of("id", "A", "maximum", 4),
            Map.of("id", "B", "maximum", 4),
            Map.of("id", "C", "maximum", 4)));
    config.removeEntryById(path, "A");
    assertTrue(config.editEntryById(path, "B", rule -> rule.put("maximum", 1)));
    assertEquals(1, config.entryById(path, "B").get("maximum"));
    assertEquals(4, config.entryById(path, "C").get("maximum"));
    assertTrue(config.removeEntryById(path, "B"));
    assertEquals(List.of("C"), config.entries(path).stream().map(rule -> rule.get("id")).toList());
  }

  @Test
  void deletedRuleIsNotRecreatedAndNeverEditsItsFormerNeighbor() {
    RuleConfig config = new RuleConfig(directory.resolve("config.yml"), defaults());
    String path = "game-rules.stuff-rules.enchantment-caps";
    config.set(path, List.of(Map.of("id", "B", "maximum", 4), Map.of("id", "C", "maximum", 5)));
    config.removeEntryById(path, "B");
    var before = config.snapshot();
    assertFalse(config.editEntryById(path, "B", rule -> rule.put("maximum", 2)));
    assertNull(config.entryById(path, "B"));
    assertEquals(before, config.snapshot());
    assertEquals(5, config.entryById(path, "C").get("maximum"));
  }

  @Test
  void importedMissingAndDuplicateRuleIdsAreAssignedOnlyOnce() {
    RuleConfig config = new RuleConfig(directory.resolve("config.yml"), defaults());
    String path = "game-rules.stuff-rules.armor-rules";
    config.set(
        path,
        List.of(
            Map.of("maximum", 4),
            Map.of("id", "same", "maximum", 3),
            Map.of("id", "same", "maximum", 2),
            Map.of("id", 19, "maximum", 1)));
    var first = config.identifiedEntries(path);
    assertEquals(4, first.stream().map(rule -> rule.get("id")).distinct().count());
    assertTrue(
        first.stream().allMatch(rule -> rule.get("id") instanceof String id && !id.isBlank()));
    assertEquals(List.of(4, 3, 2, 1), first.stream().map(rule -> rule.get("maximum")).toList());
    assertEquals(first, config.identifiedEntries(path));
    assertEquals(
        first, new RuleConfig(directory.resolve("config.yml"), defaults()).identifiedEntries(path));
  }

  @Test
  void nonObjectRootNeverReplaced() throws Exception {
    Path path = directory.resolve("config.yml");
    String invalid = "- not\n- an object\n";
    Files.writeString(path, invalid);
    assertThrows(IllegalStateException.class, () -> new RuleConfig(path, defaults()));
    assertEquals(invalid, Files.readString(path));
  }
}
