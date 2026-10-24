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

/** Paper-compatible paths, safe YAML, atomic writes. World state is a separate file. */
public final class RuleConfig {
  private final Path path;
  private Map<String, Object> root = new LinkedHashMap<>();
  private final Map<String, Object> defaults;
  private final Yaml yaml;
  private Runnable changed = () -> {};

  public RuleConfig(Path path, InputStream defaultYaml) {
    this.path = path;
    LoaderOptions loader = new LoaderOptions();
    loader.setAllowDuplicateKeys(false);
    DumperOptions dumper = new DumperOptions();
    dumper.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    yaml =
        new Yaml(
            new SafeConstructor(loader),
            new org.yaml.snakeyaml.representer.Representer(dumper),
            dumper,
            loader);
    defaults = defaultYaml == null ? new LinkedHashMap<>() : map(yaml.load(defaultYaml));
    load();
  }

  public synchronized void load() {
    try {
      if (Files.exists(path)) {
        Object loaded = yaml.load(Files.readString(path, StandardCharsets.UTF_8));
        if (loaded != null && !(loaded instanceof Map<?, ?>))
          throw new IllegalArgumentException("YAML root must be an object");
        root = map(loaded);
      } else {
        root = deepCopy(defaults);
        save();
      }
    } catch (IOException | RuntimeException failure) {
      throw new IllegalStateException(
          "Cannot load CCR configuration " + path + "; existing file retained", failure);
    }
  }

  public synchronized void save() {
    try {
      Files.createDirectories(path.toAbsolutePath().getParent());
      Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
      Files.writeString(temporary, yaml.dump(root), StandardCharsets.UTF_8);
      for (int attempt = 0; ; attempt++) {
        try {
          try {
            Files.move(
                temporary,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
          } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
          }
          break;
        } catch (java.nio.file.AccessDeniedException locked) {
          // Windows scanners can briefly hold a freshly replaced YAML file. Never delete the old
          // file.
          if (attempt >= 5) throw locked;
          try {
            Thread.sleep(10L * (attempt + 1));
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException(
                "Interrupted while retaining the previous configuration", interrupted);
          }
        }
      }
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot save CCR configuration " + path, failure);
    }
  }

  public Object get(String path) {
    Object value = lookup(root, path);
    return value == null ? lookup(defaults, path) : value;
  }

  public synchronized Map<String, Object> snapshot() {
    return deepCopy(root);
  }

  public synchronized void restore(Map<String, Object> snapshot) {
    root = deepCopy(snapshot);
    save();
    changed.run();
  }

  public boolean has(String path) {
    return lookup(root, path) != null;
  }

  public boolean contains(String path) {
    return has(path);
  }

  public double decimal(String path, double fallback, double min, double max) {
    return Math.clamp(decimal(path, fallback), min, max);
  }

  public static boolean enabled(Map<String, Object> map) {
    return Boolean.TRUE.equals(map.get("enabled"));
  }

  public static String materialKey(String id) {
    return material(id);
  }

  public static int number(Map<String, Object> map, String key, int fallback) {
    return map.get(key) instanceof Number n ? n.intValue() : fallback;
  }

  public boolean bool(String path, boolean fallback) {
    Object value = get(path);
    return value instanceof Boolean b ? b : fallback;
  }

  public int integer(String path, int fallback, int min, int max) {
    Object value = get(path);
    return Math.clamp(value instanceof Number n ? n.intValue() : fallback, min, max);
  }

  public double decimal(String path, double fallback) {
    Object value = get(path);
    double d = value instanceof Number n ? n.doubleValue() : fallback;
    return Double.isFinite(d) ? d : fallback;
  }

  public String text(String path, String fallback) {
    Object value = get(path);
    return value == null ? fallback : value.toString();
  }

  public Map<String, Object> section(String path) {
    return map(get(path));
  }

  public List<Map<String, Object>> entries(String path) {
    List<Map<String, Object>> values = new ArrayList<>();
    if (get(path) instanceof List<?> list)
      for (Object value : list) if (value instanceof Map<?, ?>) values.add(map(value));
    return values;
  }

  /** Imported rules may lack IDs; assign them once without changing the configured behavior. */
  public synchronized List<Map<String, Object>> identifiedEntries(String path) {
    List<Map<String, Object>> values = entries(path);
    java.util.Set<String> used = new java.util.HashSet<>();
    boolean updated = false;
    for (Map<String, Object> rule : values) {
      Object value = rule.get("id");
      String id = value instanceof String string ? string : "";
      if (id.isBlank() || !used.add(id)) {
        id = java.util.UUID.randomUUID().toString();
        rule.put("id", id);
        used.add(id);
        updated = true;
      }
    }
    if (updated) set(path, values);
    return values;
  }

  public Map<String, Object> entryById(String path, String id) {
    if (id == null || id.isBlank()) return null;
    return entries(path).stream()
        .filter(rule -> id.equals(rule.get("id")))
        .findFirst()
        .orElse(null);
  }

  public synchronized boolean editEntryById(
      String path, String id, java.util.function.Consumer<Map<String, Object>> edit) {
    if (id == null || id.isBlank()) return false;
    List<Map<String, Object>> values = entries(path);
    for (Map<String, Object> rule : values) {
      if (!id.equals(rule.get("id"))) continue;
      edit.accept(rule);
      rule.put("id", id);
      set(path, values);
      return true;
    }
    return false;
  }

  public synchronized boolean removeEntryById(String path, String id) {
    if (id == null || id.isBlank()) return false;
    List<Map<String, Object>> values = entries(path);
    if (!values.removeIf(rule -> id.equals(rule.get("id")))) return false;
    set(path, values);
    return true;
  }

  public synchronized void set(String path, Object value) {
    put(path, value);
    save();
    changed.run();
  }

  public synchronized void batch(Map<String, ?> values) {
    values.forEach(this::put);
    save();
    changed.run();
  }

  public void onChange(Runnable changed) {
    this.changed = changed;
  }

  @SuppressWarnings("unchecked")
  private void put(String path, Object value) {
    String[] parts = path.split("\\.");
    Map<String, Object> parent = root;
    for (int i = 0; i < parts.length - 1; i++) {
      Object next = parent.get(parts[i]);
      if (!(next instanceof Map<?, ?>)) {
        next = new LinkedHashMap<String, Object>();
        parent.put(parts[i], next);
      }
      parent = (Map<String, Object>) next;
    }
    if (value == null) parent.remove(parts[parts.length - 1]);
    else parent.put(parts[parts.length - 1], mutableCopy(value));
  }

  private static Object mutableCopy(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, child) -> result.put(String.valueOf(key), mutableCopy(child)));
      return result;
    }
    if (value instanceof List<?> list) {
      List<Object> result = new ArrayList<>();
      list.forEach(child -> result.add(mutableCopy(child)));
      return result;
    }
    return value;
  }

  private static Object lookup(Map<String, Object> values, String path) {
    if (path.isEmpty()) return values;
    Object current = values;
    for (String part : path.split("\\.")) {
      if (!(current instanceof Map<?, ?> map)) return null;
      current = map.get(part);
    }
    return current;
  }

  public static Map<String, Object> map(Object object) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (object instanceof Map<?, ?> values)
      values.forEach((key, value) -> result.put(String.valueOf(key), value));
    return result;
  }

  private Map<String, Object> deepCopy(Map<String, Object> input) {
    return map(yaml.load(yaml.dump(input)));
  }

  public static String material(String id) {
    return id.toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
  }

  public static String itemId(String id) {
    String normalized = id.toLowerCase(Locale.ROOT);
    return normalized.contains(":") ? normalized : "minecraft:" + normalized;
  }
}
