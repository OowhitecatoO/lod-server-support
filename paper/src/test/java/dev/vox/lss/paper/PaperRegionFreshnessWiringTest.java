package dev.vox.lss.paper;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The v0.12.0 B.0 wiring pins (v0.12.0-release-plan.md Phase B.0): the P1 header
 * freshness rung's PAPER wiring had no gate on any support line while living in the
 * single most-drifted file of the backport — a conflict resolution that drops the
 * {@code attachRegionStamps} call or mis-roots the region-dir resolver ships a line
 * where the headline warm-rejoin feature is silently dead (every doubt shape degrades
 * fail-safe to NEVER_CLEAN — no crash, no log storm). The 1.21.1 line has ALREADY
 * shipped the resolver half of that bug once (world/DIM-1 does not exist under the
 * unified layout — its own port comment records it).
 *
 * <p>PORTS: the resolver pin's EXPECTATIONS are per-line (surfaces row 17): THIS
 * line (1.21.11, Bukkit SPLIT world dirs) expects the per-level
 * {@code getWorldFolder()} re-root with {@code getStorageFolder} nesting nether/end
 * as {@code DIM-1}/{@code DIM1} under their own Bukkit folders; the 26.x lines
 * instead expect the unified {@code dimensions/minecraft/<dim>/region} layout under
 * the server worldRoot. Adapt the expected paths WITH the resolver — a port that
 * leaves either behind reds here.
 */
class PaperRegionFreshnessWiringTest {

    @org.junit.jupiter.api.BeforeAll
    static void setup() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static ServerLevel level(ResourceKey<Level> key, Path bukkitFolder) {
        var l = mock(ServerLevel.class);
        when(l.dimension()).thenReturn(key);
        // 1.21.x line: the resolver re-roots per level via the Bukkit world folder
        // (ServerLevel.getWorld() returns the concrete CraftWorld on this line).
        var w = mock(org.bukkit.craftbukkit.CraftWorld.class);
        when(w.getWorldFolder()).thenReturn(bukkitFolder.toFile());
        when(l.getWorld()).thenReturn(w);
        return l;
    }

    @Test
    void resolverMapsAllThreeVanillaDimensionsToTheirRegionDirs() throws Exception {
        Path root = Files.createTempDirectory("lss-resolver-pin");
        var server = mock(MinecraftServer.class);
        // Bukkit SPLIT layout (row 17): three separate world folders.
        var overworld = level(Level.OVERWORLD, root.resolve("world"));
        var nether = level(Level.NETHER, root.resolve("world_nether"));
        var end = level(Level.END, root.resolve("world_the_end"));
        when(server.getAllLevels()).thenReturn(List.of(overworld, nether, end));

        var dirs = PaperRequestProcessingService.resolveRegionDirs(server, root);

        assertEquals(3, dirs.size(), "all three vanilla dimensions must resolve");
        Path ow = dirs.get("minecraft:overworld");
        Path ne = dirs.get("minecraft:the_nether");
        Path en = dirs.get("minecraft:the_end");
        assertNotNull(ow, "overworld resolved");
        assertNotNull(ne, "nether resolved — the shipped-once failure shape was a"
                + " nether/end dir that does not exist, silently degrading every"
                + " non-overworld dim to NEVER_CLEAN");
        assertNotNull(en, "end resolved");
        // 1.21.x SPLIT layout (row 17): the overworld sits at its own Bukkit
        // folder's region dir; nether/end nest DIM-1/DIM1 under THEIR folders
        // (CraftBukkit's on-disk layout — getStorageFolder against the per-level
        // root). These expectations change WITH the resolver form per line.
        assertEquals(root.resolve("world/region").normalize(), ow,
                "overworld = <bukkit world folder>/region on the split layout");
        // EXACT nether/end pins (panel fold): the overworld leg cannot distinguish
        // levelRoot.resolve("region") from getStorageFolder (they coincide there), so
        // the DIM-1/DIM1 nesting is the ONE fact only these two assertions protect —
        // a resolver simplified to levelRoot.resolve("region") reads region files
        // from directories that do not exist and every non-overworld dim silently
        // degrades to NEVER_CLEAN (the shipped-once failure class).
        assertEquals(root.resolve("world_nether/DIM-1/region").normalize(), ne,
                "nether = <bukkit nether folder>/DIM-1/region on the split layout: " + ne);
        assertEquals(root.resolve("world_the_end/DIM1/region").normalize(), en,
                "end = <bukkit end folder>/DIM1/region on the split layout: " + en);
        assertEquals(3, java.util.Set.of(ow, ne, en).size(),
                "the three dimensions' region dirs must be DISTINCT — a resolver that"
                + " collapses them serves cross-dimension freshness claims");

    }

    @Test
    void exoticDimensionDegradesThatDimensionOnlyNeverServiceStart() {
        var server = mock(MinecraftServer.class);
        var overworld = level(Level.OVERWORLD, Path.of("world"));
        var exotic = mock(ServerLevel.class);
        when(exotic.dimension()).thenThrow(new IllegalStateException("exotic dimension"));
        // Line-exclusive throw source (panel fold): on the split-dir lines the belt
        // also covers getWorld() — a CraftBukkit-only accessor invoked at enable for
        // EVERY level, store-less servers included. It must stay INSIDE the per-level
        // try; a hoisted "resolve the folder once" cleanup would let one unattachable
        // level abort service start, and only this mock catches that.
        var unattachable = mock(ServerLevel.class);
        when(unattachable.dimension()).thenReturn(ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.Identifier.parse("lss_test:unattachable")));
        when(unattachable.getWorld()).thenThrow(new IllegalStateException("no CraftWorld"));
        when(server.getAllLevels()).thenReturn(List.of(overworld, exotic, unattachable));

        var dirs = assertDoesNotThrow(() -> PaperRequestProcessingService
                        .resolveRegionDirs(server, Path.of("root")),
                "the per-level belt: one exotic dimension must never take down start");
        assertNotNull(dirs.get("minecraft:overworld"), "the healthy dimension still resolves");
        assertNull(dirs.get("lss_test:unattachable"),
                "the unattachable level degrades to absent (UNKNOWN downstream), never a throw");
    }

    /** The ATTACH half (source-scan — the contract-test idiom; the production wiring
     *  builder needs a full NMS server, and the test Wiring ctor deliberately
     *  bypasses it): the stamp table must be constructed from the resolver and
     *  attached to the disk reader UNCONDITIONALLY (outside any store branch — the
     *  rung is load-bearing store-LESS), and the dirty tracker's mark listener must
     *  bump the region latch. A port that drops any of the three lines ships the
     *  silent-dead-feature shape this test exists for. */
    @Test
    void wiringSourceCarriesTheAttachAndTheMarkListenerBump() throws Exception {
        Path src = Path.of("src/main/java/dev/vox/lss/paper/PaperRequestProcessingService.java");
        if (!Files.exists(src)) {
            src = Path.of("paper").resolve(src);
        }
        String body = Files.readString(src);

        int resolve = body.indexOf("resolveRegionDirs(server, worldRoot)");
        int attach = body.indexOf("diskReader.attachRegionStamps(regionStamps)");
        int bump = body.indexOf(".bumpLiveSaveMark(");
        int stampSource = body.indexOf("setUpToDateStampSource(");
        int storeBranch = body.indexOf("if (storeMode != dev.vox.lss.common.store.LodStoreMode.OFF)");
        assertTrue(resolve > 0, "the wiring builder must construct the region dirs via"
                + " resolveRegionDirs (the extracted, per-line-swappable site)");
        assertTrue(attach > 0, "the disk reader must get attachRegionStamps — without it"
                + " the header rung is silently dead (disk.header_hits frozen at 0)");
        assertTrue(bump > 0, "the mark listener must bump the region live-save latch");
        assertTrue(stampSource > 0, "the stamped-up_to_date predicate must be installed"
                + " (setUpToDateStampSource) — without it every up_to_date ships"
                + " unstamped and the heal feature is silently dead on Paper (the"
                + " Phase B review: this lane has NO live gate on Paper — wrs's floors"
                + " were racy and removed; this scan is the port protection)");
        assertTrue(storeBranch > 0, "census anchor: the store-mode branch exists");
        assertTrue(attach < storeBranch, "attachRegionStamps must sit BEFORE (outside)"
                + " the store branch — the rung is load-bearing on store-LESS servers"
                + " (the compiled store default is off)");
        assertTrue(bump < storeBranch, "the latch bump wiring must also be store-independent");
    }
}
