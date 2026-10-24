package fr.isaac.cutcleanreborn.verification;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SceneSafetyTest {
    @Test void onlyKnownScenesAreAccepted() {
        for (String scene : new String[] {"timber", "ore", "cane", "mob", "stacks", "equipment"})
            assertTrue(SceneSafety.knownScene(scene), scene);
        assertFalse(SceneSafety.knownScene(null));
        assertFalse(SceneSafety.knownScene(""));
        assertFalse(SceneSafety.knownScene("equipement"));
        assertFalse(SceneSafety.knownScene("../equipment"));
    }

    @Test void acceptsAllTwentyPluginQARoots() {
        for (String loader : new String[] {"Paper", "Purpur", "Spigot", "Bukkit"})
            for (String version : new String[] {"1.21.1", "1.21.11", "26.1.x", "26.2", "26.3"}) {
                Path root = Path.of("workspace", loader + "_" + version, "build", "client-test-server");
                assertTrue(SceneSafety.isolatedRoot(root), root.toString());
                assertTrue(SceneSafety.isolatedWorld(root, root.resolve("client-qa-world"), "client-qa-world"));
            }
    }

    @Test void refusesUserServerAndLookalikeDirectories() {
        assertFalse(SceneSafety.isolatedRoot(null));
        for (String root : new String[] {"/", "workspace", "Paper_1.21.1/run-server",
                "Paper_1.21.1/client-test-server", "Paper_1.21.1/run-server/build/client-test-server",
                "Fabric_1.21.1/build/client-test-server", "Other_26.3/build/client-test-server",
                "Paper_1.21.10/build/client-test-server", "Paper_26.3/build/client-test-server-old"})
            assertFalse(SceneSafety.isolatedRoot(Path.of(root)), root);
    }

    @Test void refusesOtherWorldsAndNormalizedEscapes() {
        Path root = Path.of("workspace", "Paper_1.21.1", "build", "client-test-server");
        assertFalse(SceneSafety.isolatedWorld(root, null, "client-qa-world"));
        assertFalse(SceneSafety.isolatedWorld(root, root.resolve("world"), "world"));
        assertFalse(SceneSafety.isolatedWorld(root, root.resolve("client-qa-world_nether"), "client-qa-world_nether"));
        assertFalse(SceneSafety.isolatedWorld(root, root.resolve("../client-qa-world"), "client-qa-world"));
        assertFalse(SceneSafety.isolatedWorld(root, root.resolve("client-qa-world"), "survival"));
        assertFalse(SceneSafety.isolatedWorld(root, root.resolve("client-qa-world/../elsewhere"), "client-qa-world"));
    }
}
