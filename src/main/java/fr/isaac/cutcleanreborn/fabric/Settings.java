package fr.isaac.cutcleanreborn.fabric;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Bukkit-compatible paths without depending on a Bukkit implementation. */
public final class Settings {
  private final Path path;
  private Map<String, Object> values = new LinkedHashMap<>();
  private Map<String, Object> defaults = new LinkedHashMap<>();

  public Settings(Path path) {
    this.path = path;
  }

  public synchronized void load() throws IOException {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setMaxAliasesForCollections(30);
    Yaml yaml = new Yaml(new SafeConstructor(options));
    try (InputStream input = Settings.class.getResourceAsStream("/config.yml")) {
      defaults = map(yaml.load(input));
    }
    if (Files.exists(path)) {
      try (var input = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
        values = map(yaml.load(input));
      }
    } else {
      values = deepCopy(defaults);
      save();
    }
  }

  public synchronized void save() throws IOException {
    Files.createDirectories(path.getParent());
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    Path temp = path.resolveSibling(path.getFileName() + ".tmp");
    Files.writeString(temp, new Yaml(options).dump(values), StandardCharsets.UTF_8);
    try {
      Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
      Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public synchronized Object get(String path) {
    Object value = lookup(values, path);
    return value == null ? lookup(defaults, path) : value;
  }

  public synchronized boolean contains(String path) {
    return lookup(values, path) != null;
  }

  public boolean bool(String path, boolean fallback) {
    return get(path) instanceof Boolean b ? b : fallback;
  }

  public int integer(String path, int fallback, int min, int max) {
    return Math.clamp(get(path) instanceof Number n ? n.intValue() : fallback, min, max);
  }

  public double decimal(String path, double fallback, double min, double max) {
    double value = get(path) instanceof Number n ? n.doubleValue() : fallback;
    return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
  }

  public String text(String path, String fallback) {
    return get(path) instanceof String s ? s : fallback;
  }

  public Map<String, Object> section(String path) {
    return map(get(path));
  }

  public List<Map<String, Object>> entries(String path) {
    List<Map<String, Object>> result = new ArrayList<>();
    if (get(path) instanceof List<?> list)
      for (Object entry : list) if (entry instanceof Map<?, ?>) result.add(map(entry));
    return result;
  }

  public synchronized void set(String path, Object value) {
    String[] parts = path.split("\\.");
    Map<String, Object> current = values;
    for (int index = 0; index < parts.length - 1; index++) {
      Object next = current.get(parts[index]);
      if (!(next instanceof Map)) {
        next = new LinkedHashMap<String, Object>();
        current.put(parts[index], next);
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> nested = (Map<String, Object>) next;
      current = nested;
    }
    if (value == null) current.remove(parts[parts.length - 1]);
    else current.put(parts[parts.length - 1], mutableCopy(value));
  }

  private static Object mutableCopy(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, entry) -> result.put(String.valueOf(key), mutableCopy(entry)));
      return result;
    }
    if (value instanceof List<?> list) {
      List<Object> result = new ArrayList<>();
      for (Object entry : list) result.add(mutableCopy(entry));
      return result;
    }
    return value;
  }

  public synchronized Map<String, Object> snapshot() {
    return deepCopy(values);
  }

  synchronized void restore(Map<String, Object> snapshot) {
    values = deepCopy(snapshot);
  }

  private static Object lookup(Map<String, Object> root, String path) {
    Object current = root;
    for (String part : path.split("\\.")) {
      if (!(current instanceof Map<?, ?> map)) return null;
      current = map.get(part);
    }
    return current;
  }

  public static Map<String, Object> map(Object value) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (value instanceof Map<?, ?> map)
      map.forEach((key, item) -> result.put(String.valueOf(key), item));
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> deepCopy(Map<String, Object> map) {
    return new Yaml(new SafeConstructor(new LoaderOptions())).load(new Yaml().dump(map));
  }

  public static int number(Map<String, Object> map, String key, int fallback) {
    return map.get(key) instanceof Number n ? n.intValue() : fallback;
  }

  public static double number(Map<String, Object> map, String key, double fallback) {
    return map.get(key) instanceof Number n ? n.doubleValue() : fallback;
  }

  public static boolean enabled(Map<String, Object> map) {
    return Boolean.TRUE.equals(map.get("enabled"));
  }

  public static String materialKey(String id) {
    return id.replace("minecraft:", "").toUpperCase(Locale.ROOT);
  }
}
