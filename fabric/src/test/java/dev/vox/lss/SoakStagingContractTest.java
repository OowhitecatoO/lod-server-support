package dev.vox.lss;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the 1.21.11-specific soak-staging shape of {@code scripts/soak.sh}. Honest framing:
 * these are text pins — but the plausible regression is a forward merge taking main's
 * soak.sh wholesale, which stages {@code world/} only. On this line Bukkit platforms use
 * the legacy split layout ({@code world_nether}/{@code world_the_end}), so main's shape
 * silently drops the End from the base snapshot and {@code dimension-trip} then runs
 * against a freshly regenerated End — the scenario's premise weakens instead of failing.
 */
class SoakStagingContractTest {

    private static String soakSh;

    @BeforeAll
    static void load() throws Exception {
        soakSh = Files.readString(locate("scripts/soak.sh"));
    }

    private static Path locate(String repoRelative) {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(repoRelative);
            if (Files.exists(candidate)) return candidate;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " above " + Path.of("").toAbsolutePath());
    }

    @Test
    void stagingCopiesEveryWorldDirViaTheGlob() {
        assertTrue(soakSh.contains("cp -r \"$BASE_WORLD_DIR\"/world* \"$SERVER_RUN_DIR\"/"),
                "staging must glob-copy world* (split world_nether/world_the_end on 1.21.11)");
        assertTrue(soakSh.contains("cp -r \"$SERVER_RUN_DIR\"/world* \"$BASE_WORLD_DIR\"/"),
                "the base-world save must glob-copy world* back");
    }

    @Test
    void allSplitDirsAreClearedBeforeEachCopy() {
        // Without the save-side rm of ALL three dirs, the glob copy nests
        // world_nether/world_nether and the snapshot silently keeps a STALE End/Nether —
        // the exact failure the split-world handling exists to prevent.
        assertTrue(soakSh.contains(
                        "rm -rf \"$SERVER_RUN_DIR\"/world \"$SERVER_RUN_DIR\"/world_nether \"$SERVER_RUN_DIR\"/world_the_end"),
                "staging must clear all three split world dirs");
        assertTrue(soakSh.contains(
                        "rm -rf \"$BASE_WORLD_DIR\"/world \"$BASE_WORLD_DIR\"/world_nether \"$BASE_WORLD_DIR\"/world_the_end"),
                "the base-world save must clear all three split world dirs first");
    }

    @Test
    void staleBaseWorldGuardRunsBeforeTheAutoBackfillCheck() {
        assertTrue(soakSh.contains("WORLD_VERSION_MARKER=\"$BASE_WORLD_DIR/mc-version\""),
                "the base-world mc-version marker must be defined");
        assertTrue(soakSh.contains("printf '%s' \"1.21.11\" > \"$WORLD_VERSION_MARKER\""),
                "fresh-backfill must stamp the marker when it saves a base world");
        int guard = soakSh.indexOf("!= \"1.21.11\"");
        int step1 = soakSh.indexOf("# Step 1: Auto-run fresh-backfill");
        assertTrue(guard >= 0 && step1 >= 0 && guard < step1,
                "the stale-base clear (a 26.x world will not downgrade) must run BEFORE the "
                        + "Step-1 auto-backfill existence check, or a stale base is booted as-is");
    }
}
