package dev.vox.lss.paper;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Paper twin of the fabric {@code ToolchainContractTest}: pins the 1.21.11 line's effective
 * {@code options.release = 21}, JVM-independently. The paperweight dev bundle's codebook
 * cannot parse class files newer than Java 21, and a reverted release level passes every
 * local tier on a Java 25 JDK — the compiled bytes are the ground truth, not the build
 * script text or the JVM the tests happen to run on.
 */
class ToolchainContractTest {

    /** Java 21's class-file major version (Java N = 44 + N). */
    private static final int EXPECTED_CLASS_MAJOR = 65;

    @Test
    void compiledClassFileTargetsJava21() throws Exception {
        try (InputStream in = LSSPaperPlugin.class.getResourceAsStream(
                "/dev/vox/lss/paper/LSSPaperPlugin.class")) {
            assertNotNull(in, "class bytes not found for LSSPaperPlugin");
            byte[] header = in.readNBytes(8);
            int major = ((header[6] & 0xff) << 8) | (header[7] & 0xff);
            assertEquals(EXPECTED_CLASS_MAJOR, major,
                    "paper must compile at --release 21 on the 1.21.11 line (Java N = major - 44)");
        }
    }
}
