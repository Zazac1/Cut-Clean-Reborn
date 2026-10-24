package fr.isaac.cutcleanreborn.fabric;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Shared policy only: bounded input, Paper drop rules, categories and stable mapping cycles. */
public final class RuleMath {
  private RuleMath() {}

  public static Integer integer(String input, int min, int max) {
    try {
      int n = Integer.parseInt(input.trim());
      return n >= min && n <= max ? n : null;
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  public static Double percentage(String input) {
    try {
      double n = Double.parseDouble(input.trim().replace("%", "").replace(',', '.'));
      return Double.isFinite(n) && n >= 0 && n <= 100 ? n : null;
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  public static AmountRange range(String input, int min, int max) {
    String[] values = input.trim().split("-", -1);
    if (values.length < 1 || values.length > 2) return null;
    Integer a = integer(values[0], min, max), b = integer(values[values.length - 1], min, max);
    return a != null && b != null && b >= a ? new AmountRange(a, b) : null;
  }

  public record AmountRange(int minimum, int maximum) {}

  public static double percent(double value) {
    return Double.isFinite(value) ? Math.clamp(value, 0, 100) : 0;
  }

  public static int fortune(int level, RandomGenerator random) {
    return level <= 0 ? 1 : Math.max(1, random.nextInt(Math.clamp(level, 0, 255) + 2));
  }

  public static int looting(int level, RandomGenerator random) {
    return level <= 0 ? 0 : random.nextInt(Math.clamp(level, 0, 255) + 1);
  }

  public static int roll(Map<String, Object> rule, RandomGenerator random) {
    int minimum = number(rule.get("minimum"), 1), maximum = number(rule.get("maximum"), minimum);
    double chance = rule.get("chance") instanceof Number n ? n.doubleValue() : 100;
    if (minimum < 0
        || maximum < minimum
        || maximum > 64
        || !Double.isFinite(chance)
        || chance < 0
        || chance > 100) return 0;
    return random.nextDouble(100) < chance ? random.nextInt(minimum, maximum + 1) : 0;
  }

  public static int number(Object value, int fallback) {
    return value instanceof Number n ? n.intValue() : fallback;
  }

  public static boolean matches(String target, String item) {
    String name = RuleConfig.material(item), normalized = target.toUpperCase(Locale.ROOT);
    return switch (normalized) {
      case "ALL" -> true;
      case "ARMOR" -> armor(name);
      case "SWORDS" -> name.endsWith("_SWORD");
      case "TOOLS" -> tool(name);
      case "LEATHER", "CHAINMAIL", "IRON", "GOLDEN", "DIAMOND", "NETHERITE", "COPPER" ->
          armor(name) && name.startsWith(normalized + "_");
      default ->
          List.of(normalized.split(",")).stream()
              .map(String::trim)
              .map(RuleConfig::material)
              .anyMatch(name::equals);
    };
  }

  public static boolean armor(String item) {
    return item.endsWith("_HELMET")
        || item.endsWith("_CHESTPLATE")
        || item.endsWith("_LEGGINGS")
        || item.endsWith("_BOOTS");
  }

  public static boolean tool(String item) {
    return List.of("_SWORD", "_PICKAXE", "_AXE", "_SHOVEL", "_HOE").stream()
            .anyMatch(item::endsWith)
        || List.of(
                "SHEARS",
                "FISHING_ROD",
                "TRIDENT",
                "BOW",
                "CROSSBOW",
                "MACE",
                "BRUSH",
                "FLINT_AND_STEEL")
            .contains(item);
  }

  public static String category(String item) {
    if (item.endsWith("_PLANKS")
        || item.endsWith("_LOG")
        || List.of("CRIMSON_STEM", "WARPED_STEM", "STRIPPED_CRIMSON_STEM", "STRIPPED_WARPED_STEM")
            .contains(item)) return "wood";
    return List.of(
                "STONE",
                "COBBLESTONE",
                "DEEPSLATE",
                "COBBLED_DEEPSLATE",
                "GRANITE",
                "DIORITE",
                "ANDESITE",
                "TUFF",
                "CALCITE",
                "DRIPSTONE_BLOCK")
            .contains(item)
        ? "stone"
        : null;
  }

  public static String normalised(
      String initial, java.util.function.Function<String, String> next) {
    java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
    String item = initial;
    while (seen.add(item)) {
      String target = next.apply(item);
      if (target == null || target.equals(item)) return item;
      if (seen.contains(target))
        return seen.stream()
            .dropWhile(v -> !v.equals(target))
            .min(String::compareTo)
            .orElse(target);
      item = target;
    }
    return item;
  }
}
