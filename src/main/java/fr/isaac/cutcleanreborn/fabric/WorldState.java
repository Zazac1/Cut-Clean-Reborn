package fr.isaac.cutcleanreborn.fabric;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/** A different solo world must not inherit the unique mace claim of the previous world. */
final class WorldState {
  private final Path path;

  WorldState(Path path) {
    this.path = path;
  }

  void load(Settings settings, boolean importLegacyDedicated) throws IOException {
    boolean exists =
        importLegacyDedicated && settings.bool(InventoryRules.ROOT + ".unique-mace.exists", false);
    String id =
        importLegacyDedicated ? settings.text(InventoryRules.ROOT + ".unique-mace.id", "") : "";
    if (Files.exists(path))
      try (var input = Files.newBufferedReader(path)) {
        var data = new Gson().fromJson(input, com.google.gson.JsonObject.class);
        exists = data.has("maceExists") && data.get("maceExists").getAsBoolean();
        id = data.has("maceId") ? data.get("maceId").getAsString() : "";
      }
    settings.set(InventoryRules.ROOT + ".unique-mace.exists", exists);
    settings.set(InventoryRules.ROOT + ".unique-mace.id", id.isBlank() ? null : id);
  }

  void save(Settings settings) throws IOException {
    Files.createDirectories(path.getParent());
    Path temp = path.resolveSibling(path.getFileName() + ".tmp");
    Files.writeString(
        temp,
        new Gson()
            .toJson(
                Map.of(
                    "maceExists",
                    settings.bool(InventoryRules.ROOT + ".unique-mace.exists", false),
                    "maceId",
                    settings.text(InventoryRules.ROOT + ".unique-mace.id", ""))));
    try {
      Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
      Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
    }
  }
}
