package dev.vox.lss.paper;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for {@code paper/src/main/resources/plugin.yml}, parsed with Bukkit's own
 * {@link YamlConfiguration} from the SOURCE tree: the classpath copy has {@code ${version}}
 * already expanded by processResources, which would make the placeholder pin vacuous.
 * (Expansion inside the built jar is release_check.py's job.) A typo in any of these fields
 * is invisible until a real Paper server refuses to load — or silently mis-loads — the
 * plugin: an unresolvable {@code main} or wrong {@code api-version} aborts plugin load, a
 * renamed plugin moves the {@code plugins/LodServerSupport/} data folder the config and
 * soak staging rely on, and {@code folia-supported} must stay {@code true} on this 1.21.11
 * support line — Folia publishes MC 1.21.11 builds and removing the flag silently drops
 * Folia support (unlike the 26.2 primary line, where the flag is deliberately absent).
 */
class PluginYmlContractTest {

    private static String rawText;
    private static YamlConfiguration yml;

    @BeforeAll
    static void load() throws Exception {
        rawText = Files.readString(locate("paper/src/main/resources/plugin.yml"));
        yml = new YamlConfiguration();
        // 'lss.admin' is a literal permission key; the default '.' separator would split it.
        yml.options().pathSeparator('/');
        yml.loadFromString(rawText);
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
    void mainClassResolvesToThePluginEntryPoint() throws Exception {
        String main = yml.getString("main");
        assertNotNull(main, "plugin.yml must declare main");
        Class<?> resolved = Class.forName(main);
        assertEquals(LSSPaperPlugin.class, resolved, "main must point at the real entry point");
        assertTrue(JavaPlugin.class.isAssignableFrom(resolved), "main must be a JavaPlugin");
    }

    @Test
    void pluginNameIsTheDataFolderContract() {
        // PaperConfig lives at plugins/LodServerSupport/lss-server-config.json and the soak
        // harness stages configs by that path; renaming the plugin silently orphans both.
        assertEquals("LodServerSupport", yml.getString("name"));
    }

    @Test
    void lsslodCommandIsDeclaredBehindTheAdminPermission() {
        assertNotNull(yml.getConfigurationSection("commands/lsslod"),
                "registerCommands() reads the declared command name from plugin.yml; without this "
                        + "section the command vanishes (the VSS repackage rewrites the key to vsslod)");
        assertEquals("lss.admin", yml.getString("commands/lsslod/permission"));
    }

    @Test
    void exactlyOneCommandIsDeclared() {
        // registerCommands() resolves the command name as getCommands().keySet().first(), so a
        // single declared command is what makes that deterministic (and lets the VSS repackage
        // rename the key without a code fork). A second command would make the pick ambiguous.
        assertEquals(1, yml.getConfigurationSection("commands").getKeys(false).size(),
                "the plugin must declare exactly one command (registerCommands picks the first)");
    }

    @Test
    void adminPermissionDefaultsToOp() {
        assertEquals("op", yml.getString("permissions/lss.admin/default"),
                "stats/diag expose server internals; the permission must not default to everyone");
    }

    @Test
    void apiVersionMatchesTheDevBundleMinecraftVersion() throws Exception {
        String apiVersion = yml.getString("api-version");
        assertNotNull(apiVersion);

        var props = new Properties();
        props.load(new StringReader(Files.readString(locate("gradle.properties"))));
        assertEquals(props.getProperty("minecraft_version"), apiVersion,
                "api-version must move in lockstep with the minecraft_version the build targets");

        var bundle = Pattern.compile("paperweight\\.paperDevBundle\\('([^']+)'\\)")
                .matcher(Files.readString(locate("paper/build.gradle")));
        assertTrue(bundle.find(), "paper/build.gradle must declare paperweight.paperDevBundle('...')");
        String devBundle = bundle.group(1);
        assertTrue(devBundle.startsWith(apiVersion + ".") || devBundle.startsWith(apiVersion + "-R"),
                "dev bundle " + devBundle + " must be a build of api-version " + apiVersion
                        + " (new '<mc>.build.N' or old '<mc>-R0.1-SNAPSHOT' scheme)");
    }

    @Test
    void foliaSupportedIsDeclared() {
        // Inverted from main's absence pin for the 1.21.11 support line (2026-07-26): Folia
        // publishes MC 1.21.11 builds and this line's Folia paths are soak-validated
        // (SOAK_PLATFORM=folia — the pump runs on GlobalRegionScheduler and lifecycle
        // ingress is mailboxed; FoliaWiringContractTest pins the wiring). Folia refuses to
        // load plugins without this flag, so removing it silently drops Folia support.
        assertTrue(yml.getBoolean("folia-supported"),
                "folia-supported: true is required for Folia to load the plugin on the 1.21.11 line");
    }

    @Test
    void versionIsTheProcessResourcesPlaceholder() {
        // The literal placeholder must survive in the source: processResources expands it at
        // build time, and release_check.py (HD-045) verifies the expansion in the built jar.
        assertEquals("${version}", yml.getString("version"));
        assertTrue(rawText.contains("version: '${version}'"),
                "the placeholder must stay single-quoted so the YAML stays parseable pre-expansion");
    }

    @Test
    void pluginYmlShipsOnTheClasspath() {
        assertNotNull(LSSPaperPlugin.class.getResource("/plugin.yml"),
                "plugin.yml must be packaged at the jar root or Paper will not recognize the plugin");
    }
}
