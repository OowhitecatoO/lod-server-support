package dev.vox.lss;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Support-line contract for {@code fabric.mod.json} + {@code gradle.properties}, read from
 * the SOURCE tree (the fabric mirror of paper's
 * {@code PluginYmlContractTest.apiVersionMatchesTheDevBundleMinecraftVersion} lockstep pin).
 * The regression vector is a forward merge from main auto-resolving these files back to the
 * 26.2 shape: the LOWER Minecraft bound is enforced by fabric-loader in every gametest
 * launch, but a loosened UPPER bound is caught by nothing at build time — and this line's
 * jar genuinely breaks on 26.x (its pinned {@code publishServer} mixin descriptor matches
 * neither 26.2 overload, and the public {@code ChunkPos.x}/{@code .z} FIELDS this branch
 * compiles against are accessors there), so the exact pin is load-bearing, not cosmetic.
 */
class FabricModJsonContractTest {

    // ---- the line's expected constants (the ONLY block that differs between support branches) ----
    private static final String EXPECTED_MINECRAFT_DEPENDS = "1.21.11";
    private static final String EXPECTED_MINECRAFT_VERSION_PREFIX = "1.21.11";

    private static JsonObject modJson;
    private static Properties gradleProps;

    @BeforeAll
    static void load() throws Exception {
        modJson = JsonParser.parseString(
                Files.readString(locate("fabric/src/main/resources/fabric.mod.json"))).getAsJsonObject();
        gradleProps = new Properties();
        gradleProps.load(new StringReader(Files.readString(locate("gradle.properties"))));
    }

    /** Walks up from the working dir (fabric/ under Gradle, the repo root elsewhere). */
    private static Path locate(String repoRelative) {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(repoRelative);
            if (Files.exists(candidate)) return candidate;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " above " + Path.of("").toAbsolutePath());
    }

    @Test
    void dependsMinecraftPinsThisLineBothWays() {
        assertEquals(EXPECTED_MINECRAFT_DEPENDS,
                modJson.getAsJsonObject("depends").get("minecraft").getAsString(),
                "fabric.mod.json must pin the line's Minecraft range exactly — the upper "
                        + "bound is what keeps this jar off incompatible newer lines");
    }

    @Test
    void gradlePropertiesTargetsTheSameLine() {
        String mc = gradleProps.getProperty("minecraft_version", "");
        // equals-or-dot: a bare prefix match would also accept e.g. 26.10, which the
        // depends range above would exclude — the two must move in lockstep.
        assertTrue(mc.equals(EXPECTED_MINECRAFT_VERSION_PREFIX)
                        || mc.startsWith(EXPECTED_MINECRAFT_VERSION_PREFIX + "."),
                "gradle.properties minecraft_version (" + mc + ") must stay on the "
                        + EXPECTED_MINECRAFT_VERSION_PREFIX + " line");
    }
}
