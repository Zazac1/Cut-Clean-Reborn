package fr.isaac.cutcleanreborn.fabric;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** World-local, persistent origin markers. Never treats newly grown cane as player-placed. */
public final class Origins {
  private final Path path;
  private final Map<String, Set<Long>> positions = new HashMap<>();
  private boolean dirty;

  Origins(Path path) {
    this.path = path;
  }

  public void load() throws IOException {
    positions.clear();
    if (!Files.exists(path)) return;
    try (var input = Files.newBufferedReader(path)) {
      JsonObject root = new Gson().fromJson(input, JsonObject.class);
      if (root != null)
        for (var entry : root.entrySet()) {
          Set<Long> values = new HashSet<>();
          for (var value : entry.getValue().getAsJsonArray()) values.add(value.getAsLong());
          positions.put(entry.getKey(), values);
        }
    }
  }

  private static String key(Level level, String kind) {
    return level.dimension().location() + "/" + kind;
  }

  public boolean contains(Level level, BlockPos pos, String kind) {
    return positions.getOrDefault(key(level, kind), Set.of()).contains(pos.asLong());
  }

  public void add(Level level, BlockPos pos, String kind) {
    if (positions.computeIfAbsent(key(level, kind), ignored -> new HashSet<>()).add(pos.asLong()))
      dirty = true;
  }

  public boolean remove(Level level, BlockPos pos, String kind) {
    Set<Long> set = positions.get(key(level, kind));
    boolean changed = set != null && set.remove(pos.asLong());
    dirty |= changed;
    return changed;
  }

  public void save() throws IOException {
    if (!dirty) return;
    Files.createDirectories(path.getParent());
    Path temp = path.resolveSibling(path.getFileName() + ".tmp");
    Files.writeString(temp, new GsonBuilder().setPrettyPrinting().create().toJson(positions));
    try {
      Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
      Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
    }
    dirty = false;
  }
}
