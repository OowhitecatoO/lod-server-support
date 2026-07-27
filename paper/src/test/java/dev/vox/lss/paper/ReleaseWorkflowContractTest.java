package dev.vox.lss.paper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for the SUPPORT-LINE workflow files ({@code .github/workflows/}), read from
 * the source tree like {@link PluginYmlContractTest}. The dominant regression vector on a
 * support branch is a forward merge from main auto-resolving these YAML files back to their
 * 26.2 shape: compilation catches none of that, and release.yml gates an IRREVERSIBLE
 * publish (GitHub release + Modrinth uploads). This lives in {@code :paper:test} because
 * both build.yml and release.yml run {@code :paper:test} BEFORE any publish step, so a
 * regression here physically blocks the tag run that would have shipped it.
 *
 * <p>Comment lines are stripped before asserting, so documentation comments stay free to
 * change (main's file is quoted in several comments here — e.g. the Modrinth style note).
 */
class ReleaseWorkflowContractTest {

    // ---- the line's expected constants (the ONLY block that differs between support branches) ----
    private static final String LINE_TAG_GLOB = "v*+mc26.1*";
    private static final String FABRIC_MODRINTH_SUFFIX = "+fabric+mc26.1";
    private static final String PAPER_MODRINTH_SUFFIX = "+paper+mc26.1.2";
    private static final String[] GAME_VERSION_LINES = {"26.1", "26.1.1", "26.1.2"};
    /** The other line's MC token: must not appear outside comments anywhere in release.yml. */
    private static final String FORBIDDEN_LINE_TOKEN = "26.2";

    private static String releaseYml;   // comment-stripped
    private static String buildYml;     // comment-stripped

    @BeforeAll
    static void load() throws Exception {
        releaseYml = stripComments(Files.readString(locate(".github/workflows/release.yml")));
        buildYml = stripComments(Files.readString(locate(".github/workflows/build.yml")));
    }

    private static String stripComments(String yaml) {
        return yaml.lines().filter(l -> !l.strip().startsWith("#"))
                .reduce(new StringBuilder(), (b, l) -> b.append(l).append('\n'), StringBuilder::append)
                .toString();
    }

    /** Walks up from the working dir (paper/ under Gradle, the repo root elsewhere). */
    private static Path locate(String repoRelative) {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve(repoRelative);
            if (Files.exists(candidate)) return candidate;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " above " + Path.of("").toAbsolutePath());
    }

    @Test
    void vssChannelIsNotPublishedFromThisSupportLine() {
        // The VSS Modrinth project (84zcagOb) tracks main's 26.2 line ONLY. Restoring main's
        // two upload steps here would irreversibly publish wrong-line jars to that project,
        // and nothing else would notice before the upload ran.
        assertFalse(releaseYml.contains("84zcagOb"),
                "release.yml must not reference the VSS Modrinth project on a support line");
        assertFalse(releaseYml.contains("voxy-server-side-"),
                "release.yml must not upload/attach VSS jars on a support line "
                        + "(the vssJar tasks still BUILD them — only publishing is out)");
    }

    @Test
    void releaseIsNeverMarkedLatest() {
        // Main's release.yml has no make_latest key at all, so an auto-merge deletes this
        // line silently — and the support release then steals the GitHub "Latest" badge.
        assertTrue(releaseYml.contains("make_latest: false"),
                "support-line releases must carry make_latest: false");
    }

    @Test
    void modVersionStripsTheTagBuildMetadata() {
        // Tags here carry +mc build metadata (v0.7.3+mc26.1). Without the strip, Gradle's
        // mod_version keeps the '+suffix' and corrupts jar names, release_check --version,
        // and the Modrinth version strings.
        assertTrue(releaseYml.contains("MOD_VERSION=\"${MOD_VERSION%%+*}\""),
                "release.yml must derive MOD_VERSION by stripping the tag's +mc suffix");
        assertFalse(releaseYml.contains("-Pmod_version=${GITHUB_REF_NAME#v}"),
                "main's tag-derived mod_version form must not come back on a support line");
    }

    @Test
    void prevTagLookupIsScopedToThisLine() {
        // Both PREV_TAG lookups (notes fallback + compare link) must only ever see this
        // line's tags — the bare 'v*' glob picks mainline's newest tag and builds a
        // cross-line Full Changelog link.
        Matcher m = Pattern.compile("git tag -l '([^']+)'").matcher(releaseYml);
        List<String> globs = new ArrayList<>();
        while (m.find()) globs.add(m.group(1));
        assertTrue(globs.size() >= 2, "expected both PREV_TAG lookups, found: " + globs);
        for (String glob : globs) {
            assertEquals(LINE_TAG_GLOB, glob, "every tag lookup must be scoped to this line");
        }
    }

    @Test
    void modrinthCoordinatesTargetThisLine() {
        // Modrinth version ids must stay unique project-wide across release lines; a
        // reverted id either collides with an already-published mainline version or
        // mislabels this line's artifact.
        assertTrue(releaseYml.contains(FABRIC_MODRINTH_SUFFIX),
                "fabric Modrinth version must carry " + FABRIC_MODRINTH_SUFFIX);
        assertTrue(releaseYml.contains(PAPER_MODRINTH_SUFFIX),
                "paper Modrinth version must carry " + PAPER_MODRINTH_SUFFIX);
        assertFalse(releaseYml.contains(FORBIDDEN_LINE_TOKEN),
                "release.yml must not reference MC " + FORBIDDEN_LINE_TOKEN
                        + " outside comments on this line");
    }

    @Test
    void foliaLoaderIsAdvertised() {
        // plugin.yml's folia-supported flag is pinned by PluginYmlContractTest; this pins
        // the ADVERTISING half (main's paper step lists paper+purpur only, so an auto-merge
        // silently drops folia from the Modrinth listing while the jar still supports it).
        assertTrue(Pattern.compile("loaders:\\s*\\|\\s*paper\\s+purpur\\s+folia").matcher(releaseYml).find(),
                "the paper Modrinth step must advertise paper, purpur AND folia on this line");
    }

    @Test
    void gameVersionsMatchTheLine() {
        for (String v : GAME_VERSION_LINES) {
            assertTrue(Pattern.compile("(?m)^\\s+" + Pattern.quote(v) + "\\s*$").matcher(releaseYml).find(),
                    "release.yml must list game version " + v + " on its own line");
        }
    }

    @Test
    void buildWorkflowRunsOnSupportBranches() {
        // Main's build.yml triggers on [main] only. If an auto-merge reverts the branch
        // filter, CI silently stops running on this branch entirely — this test then only
        // fires in the local pre-flight, which is exactly why it must exist.
        long hits = Pattern.compile("'support/\\*\\*'").matcher(buildYml).results().count();
        assertTrue(hits >= 2,
                "build.yml must keep 'support/**' in both push and pull_request branch lists"
                        + " (found " + hits + ")");
    }
}
