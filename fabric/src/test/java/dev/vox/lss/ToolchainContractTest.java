package dev.vox.lss;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the 1.21.11 line's EFFECTIVE Java target, JVM-independently. The branch builds with
 * {@code options.release = 21} while dev machines run Java 25 — so a forward merge reverting
 * the release level (or the mixin compatibility level) passes every LOCAL tier on a 25 JDK
 * and only dies on the Java 21 CI runner / a real 1.21.11 server. That exact failure already
 * bit this branch once: {@code lss.mixins.json} carried {@code JAVA_25}, parsed fine locally,
 * and failed CI at loader boot. These tests read the artifacts the build ACTUALLY produced
 * (compiled class bytes + the classpath mixin config), not the build script text.
 */
class ToolchainContractTest {

    /** Java 21's class-file major version (Java N = 44 + N). */
    private static final int EXPECTED_CLASS_MAJOR = 65;

    static int classFileMajor(Class<?> anchor, String resource) throws Exception {
        try (InputStream in = anchor.getResourceAsStream(resource)) {
            assertNotNull(in, "class bytes not found for " + resource);
            byte[] header = in.readNBytes(8);
            // magic(4) minor(2) major(2), big-endian
            return ((header[6] & 0xff) << 8) | (header[7] & 0xff);
        }
    }

    @Test
    void compiledClassFileTargetsJava21() throws Exception {
        assertEquals(EXPECTED_CLASS_MAJOR, classFileMajor(LSSMod.class, "/dev/vox/lss/LSSMod.class"),
                "fabric must compile at --release 21 on the 1.21.11 line (Java N = major - 44); "
                        + "a higher target passes locally on a 25 JDK and breaks CI + real servers");
    }

    @Test
    void mixinCompatibilityLevelMatchesTheCompiledTarget() throws Exception {
        // BOTH mixin configs since v0.10.0 — the tracer's non-required lss-trace.mixins.json
        // regresses just as silently on a forward merge as the required one.
        int major = classFileMajor(LSSMod.class, "/dev/vox/lss/LSSMod.class");
        for (String config : new String[] {"/lss.mixins.json", "/lss-trace.mixins.json"}) {
            JsonObject mixins;
            try (InputStream in = LSSMod.class.getResourceAsStream(config)) {
                assertNotNull(in, config + " not on the classpath");
                mixins = JsonParser.parseReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            assertEquals("JAVA_" + (major - 44), mixins.get("compatibilityLevel").getAsString(),
                    config + " compatibilityLevel must match the compiled class target — "
                            + "JAVA_25 parses on a local 25 JVM but Mixin refuses it on the Java 21 "
                            + "CI runner and on real Java 21 servers (loader boot failure)");
        }
    }
}
