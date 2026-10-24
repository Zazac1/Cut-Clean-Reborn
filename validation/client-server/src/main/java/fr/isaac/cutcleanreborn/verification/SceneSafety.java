package fr.isaac.cutcleanreborn.verification;

import java.nio.file.Path;
import java.util.Set;

/** Pure guards for the destructive preparation operations of the QA helper. */
final class SceneSafety {
    private static final Set<String> SCENES = Set.of("timber", "ore", "cane", "mob", "stacks", "equipment");

    private SceneSafety() { }

    static boolean knownScene(String name) {
        return name != null && SCENES.contains(name);
    }

    static boolean isolatedRoot(Path root) {
        if (root == null) return false;
        Path normalized = root.toAbsolutePath().normalize();
        Path build = normalized.getParent();
        Path module = build == null ? null : build.getParent();
        return normalized.getFileName() != null && normalized.getFileName().toString().equals("client-test-server")
                && build != null && build.getFileName() != null && build.getFileName().toString().equals("build")
                && module != null && module.getFileName() != null
                && module.getFileName().toString().matches("(Paper|Purpur|Spigot|Bukkit)_(26\\.3|26\\.2|26\\.1\\.x|1\\.21\\.11|1\\.21\\.1)");
    }

    static boolean isolatedWorld(Path root, Path world, String name) {
        return isolatedRoot(root) && world != null && "client-qa-world".equals(name)
                && root.toAbsolutePath().normalize().resolve("client-qa-world")
                        .equals(world.toAbsolutePath().normalize());
    }
}
