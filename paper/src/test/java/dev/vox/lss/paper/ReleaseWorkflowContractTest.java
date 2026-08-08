package dev.vox.lss.paper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for the SUPPORT-LINE workflow files ({@code .github/workflows/}), read from
 * the source tree like {@link PluginYmlContractTest} — this is the 1.21.11 line's flavor
 * (fresh v0.10.0 re-port branch {@code support/mc1.21.11-v0.10}; the frozen v0.8.0-era
 * {@code support/mc1.21.11} carried the same shape). The dominant regression vector on a
 * support branch is a forward merge from main auto-resolving these YAML files back to their
 * 26.2 shape: compilation catches none of that, and release.yml gates an IRREVERSIBLE
 * publish (GitHub release + Modrinth uploads). This lives in {@code :paper:test} because
 * both build.yml and release.yml run {@code :paper:test} BEFORE any publish step, so a
 * regression here physically blocks the tag run that would have shipped it.
 *
 * <p>Assertions are scoped to their STEP block wherever a value could be satisfied by the
 * wrong step (review finding: a half-merge that dropped the Paper jar from the gh-release
 * assets, or moved {@code make_latest} into a Modrinth step, passed the earlier file-global
 * checks). FULL-LINE comments are stripped before asserting; trailing inline comments are
 * not, so avoid other-line tokens in those.
 */
class ReleaseWorkflowContractTest {

    // ---- the line's expected values (differ between support branches; everything else
    // in this file is branch-invariant) ----
    private static final String LINE_TAG_GLOB = "v*+mc1.21.11*";
    // NOTE: GitHub's filter-pattern language treats '+' as a quantifier, so a pattern
    // containing '*+' is INVALID — it phantom-fails every push and a real tag triggers
    // NOTHING. The glob swallows the literal '+' with '*'; the exact-suffix check is the
    // shell guard step, where '+' is plain text.
    private static final String TRIGGER_LINE = "tags: ['v*mc1.21.11*']";
    private static final String FABRIC_MODRINTH_VERSION = "version: v${{ env.MOD_VERSION }}+fabric+mc1.21.11";
    private static final String PAPER_MODRINTH_VERSION = "version: v${{ env.MOD_VERSION }}+paper+mc1.21.11";
    private static final String[] FABRIC_GAME_VERSIONS = {"1.21.11"};
    private static final String[] PAPER_GAME_VERSIONS = {"1.21.11"};
    /** The other lines' MC tokens: must not appear outside comments anywhere in release.yml. */
    private static final String[] FORBIDDEN_LINE_TOKENS = {"26.2", "26.1"};

    private static String releaseYml;   // comment-stripped
    private static String buildYml;     // comment-stripped

    @BeforeAll
    static void load() throws Exception {
        releaseYml = stripComments(Files.readString(locate(".github/workflows/release.yml")));
        buildYml = stripComments(Files.readString(locate(".github/workflows/build.yml")));
    }

    private static String stripComments(String yaml) {
        return yaml.lines().filter(l -> !l.strip().startsWith("#"))
                .collect(Collectors.joining("\n"));
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

    /** The step block starting at {@code marker}, ending at the next sibling step header. */
    private static String stepBlock(String marker) {
        int start = releaseYml.indexOf(marker);
        assertTrue(start >= 0, "release.yml lost the step '" + marker + "'");
        int end = releaseYml.indexOf("\n      - ", start + marker.length());
        return end < 0 ? releaseYml.substring(start) : releaseYml.substring(start, end);
    }

    @Test
    void triggerIsScopedToThisLine() {
        // A bare v* tag accidentally pushed from this branch must not publish: the workflow
        // at the tag's commit decides, so the trigger itself carries the line suffix.
        assertTrue(releaseYml.contains(TRIGGER_LINE),
                "release.yml must trigger only on this line's tags (" + TRIGGER_LINE + ")");
        assertFalse(releaseYml.contains("tags: ['v*']"),
                "main's broad v* trigger must not come back on a support line");
        assertFalse(Pattern.compile("tags:.*\\*\\+").matcher(releaseYml).find(),
                "no tag filter may contain '*+' — GitHub treats '+' as a quantifier, the "
                        + "pattern is invalid, and a real tag push would trigger NOTHING");
    }

    @Test
    void guardStepRefusesWrongLineTags() {
        // The trigger glob cannot express the literal '+', so the exact-suffix scoping is
        // a first-step shell guard: only v*+mc1.21.11* tags may publish from this workflow.
        String guard = stepBlock("- name: Refuse wrong-line tags");
        assertTrue(guard.contains("v*+mc1.21.11*)") && guard.contains("exit 1"),
                "the guard step must pass only this line's +mc1.21.11 tags and fail the rest");
        assertTrue(releaseYml.indexOf("- name: Refuse wrong-line tags")
                        < releaseYml.indexOf("- uses: actions/checkout"),
                "the guard must be the FIRST step — before checkout, builds, or any publish");
    }

    @Test
    void vssChannelIsNotPublishedFromThisSupportLine() {
        // The VSS Modrinth project (84zcagOb) stopped receiving versions at v0.8.0 on every
        // line. Restoring upload steps here would irreversibly resume the retired channel —
        // with wrong-line jars, at that.
        assertFalse(releaseYml.contains("84zcagOb"),
                "release.yml must not reference the VSS Modrinth project on a support line");
        assertFalse(releaseYml.contains("voxy-server-side-"),
                "release.yml must not upload/attach VSS jars on a support line "
                        + "(the vssJar tasks still BUILD them — only publishing is out)");
    }

    @Test
    void githubReleaseStepShipsExactlyTheLssPair() {
        String gh = stepBlock("- uses: softprops/action-gh-release");
        // POSITIVE pins first (review finding: asserting only VSS absence let a half-merge
        // drop the Paper glob and ship a Fabric-only release, green).
        assertTrue(gh.contains("fabric/build/libs/lod-server-support-fabric-*.jar"),
                "the gh-release assets must include the LSS Fabric jar");
        assertTrue(gh.contains("paper/build/libs/lod-server-support-paper-*.jar"),
                "the gh-release assets must include the LSS Paper jar");
        assertTrue(gh.contains("make_latest: false"),
                "make_latest: false must sit on the gh-release step itself — a support-line "
                        + "release must never steal the Latest badge from main");
        assertTrue(gh.contains("fail_on_unmatched_files: true"),
                "fail_on_unmatched_files guards against publishing an empty release");
        assertFalse(gh.contains("voxy-server-side-"),
                "no VSS jar may be attached to the GitHub release");
    }

    @Test
    void modVersionStripsTheTagBuildMetadata() {
        // Tags here carry +mc build metadata; without the strip, Gradle's mod_version keeps
        // the '+suffix' and corrupts jar names, release_check --version, and Modrinth ids.
        assertTrue(releaseYml.contains("MOD_VERSION=\"${MOD_VERSION%%+*}\""),
                "release.yml must derive MOD_VERSION by stripping the tag's +mc suffix");
        assertFalse(releaseYml.contains("-Pmod_version=${GITHUB_REF_NAME#v}"),
                "main's tag-derived mod_version form must not come back on a support line");
    }

    @Test
    void prevTagLookupIsScopedToThisLine() {
        Matcher m = Pattern.compile("git tag -l '([^']+)'").matcher(releaseYml);
        List<String> globs = new ArrayList<>();
        while (m.find()) globs.add(m.group(1));
        assertTrue(globs.size() >= 2, "expected both PREV_TAG lookups, found: " + globs);
        for (String glob : globs) {
            assertEquals(LINE_TAG_GLOB, glob, "every tag lookup must be scoped to this line");
        }
    }

    @Test
    void fabricModrinthStepTargetsThisLine() {
        String step = stepBlock("- name: Upload Fabric to Modrinth");
        assertTrue(step.contains(FABRIC_MODRINTH_VERSION),
                "the fabric Modrinth version id must be built from the bare MOD_VERSION "
                        + "(main's github.ref_name form would publish a malformed id)");
        assertTrue(step.contains("loaders: fabric"), "fabric step must advertise the fabric loader");
        for (String v : FABRIC_GAME_VERSIONS) {
            assertTrue(Pattern.compile("(?m)^\\s+" + Pattern.quote(v) + "\\s*$").matcher(step).find(),
                    "fabric step must list game version " + v + " on its own line");
        }
    }

    @Test
    void paperModrinthStepTargetsThisLine() {
        String step = stepBlock("- name: Upload Paper to Modrinth");
        assertTrue(step.contains(PAPER_MODRINTH_VERSION),
                "the paper Modrinth version id must be built from the bare MOD_VERSION");
        // Folia stays advertised on this line: Folia publishes real 1.21.11 builds and
        // plugin.yml declares folia-supported (PluginYmlContractTest pins the presence).
        assertTrue(Pattern.compile("loaders:\\s*\\|\\s*paper\\s+purpur\\s+folia").matcher(step).find(),
                "the paper step must advertise paper, purpur AND folia on this line");
        for (String v : PAPER_GAME_VERSIONS) {
            assertTrue(Pattern.compile("(?m)^\\s+" + Pattern.quote(v) + "\\s*$").matcher(step).find(),
                    "paper step must list game version " + v + " on its own line");
        }
    }

    @Test
    void otherLineTokensAbsentOutsideComments() {
        for (String token : FORBIDDEN_LINE_TOKENS) {
            assertFalse(releaseYml.contains(token),
                    "release.yml must not reference MC " + token
                            + " outside comments on this line");
        }
    }

    @Test
    void buildWorkflowRunsOnSupportBranches() {
        // Main's build.yml and the support lines share the identical 2-entry filter, which
        // keeps the recurring main→support merges conflict-free and means a support branch
        // pushed before its own build.yml edit still gets CI. Pin the exact lists, not a
        // floating token count.
        long hits = Pattern.compile(Pattern.quote("branches: [main, 'support/**']"))
                .matcher(buildYml).results().count();
        assertEquals(2, hits,
                "build.yml must keep branches: [main, 'support/**'] on push AND pull_request");
    }
}
