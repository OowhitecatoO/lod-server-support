package dev.vox.lss.networking.server;

import dev.vox.lss.common.wire.V20ToNativeTranslator;
import dev.vox.lss.common.wire.WireSectionCursor;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.21.11-LINE FLAVOR — the D3 phase-7 CROSS-LINE arm of §9's fixture corpus (C6): every
 * checked-in {@code xver-live-corpus/} fixture is a v20 body captured from REAL played
 * terrain ON MC 26.2 by {@link XverLiveCorpusCaptureTool}, decoded here against THIS
 * line's (1.21.11) registries — the only automated coverage the actual issue-#85
 * scenario (cross-MC v20 serving) has anywhere. Verified at the D3 re-port
 * (2026-08-08): every 26.2-captured identity resolves STRICTLY on 1.21.11 — zero
 * fallbacks — so this arm keeps the strict resolvers and the voxel-level identity
 * comparison unchanged from main's same-line flavor; the one per-line divergence is the
 * count-short fold (this line's native carries ONE count short = the v20 pair summed —
 * see WireSectionCursor's NATIVE flavor — asserted below instead of pass-through). If a
 * future corpus re-capture (on a newer 26.2.x registry) introduces identities unknown
 * here, that is legitimate CROSS-LINE drift, not a broken capture: switch the affected
 * identities to the documented client fallback expectations ({@code unknownBlockFallback}
 * / ladder containment) rather than widening the strict resolvers silently.
 */
class XverLiveCorpusDecodeTest {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Map<String, Integer> blockIds;
    private static Map<String, Integer> biomeIds;
    private static int blockRegistrySize;
    private static int biomeRegistrySize;

    @BeforeAll
    static void setup() {
        blockIds = IdentityTables.blockIdsByIdentity();
        // The FULL vanilla biome lookup, exactly like the capture tool — the corpus
        // tests' minimal registry lacks live-terrain biomes (deep_dark, lush_caves…)
        // and a strict decode would red on its own line for the wrong reason.
        var provider = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var src = provider.lookupOrThrow(Registries.BIOME);
        var biomes = new net.minecraft.core.MappedRegistry<>(Registries.BIOME,
                com.mojang.serialization.Lifecycle.stable());
        src.listElements().forEach(ref -> biomes.register(ref.key(), ref.value(),
                net.minecraft.core.RegistrationInfo.BUILT_IN));
        biomes.freeze();
        var biomeTable = IdentityTables.biomeTable(biomes);
        biomeIds = biomeTable.idsByIdentity();
        blockRegistrySize = IdentityTables.blockIdentities().length;
        biomeRegistrySize = biomeTable.identities().length;
        biomeIdentityByIdCache = biomeTable.identities();
    }

    private static Path corpusDir() {
        Path dir = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 5 && dir != null; depth++, dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve("src/test/java/dev/vox/lss"))) {
                return dir.resolve("src/test/resources/xver-live-corpus");
            }
            Path nested = dir.resolve("fabric");
            if (Files.isDirectory(nested.resolve("src/test/java/dev/vox/lss"))) {
                return nested.resolve("src/test/resources/xver-live-corpus");
            }
        }
        throw new IllegalStateException("cannot locate the fabric module source tree");
    }

    private static List<Path> fixtures() throws Exception {
        var dir = corpusDir();
        assertTrue(Files.isDirectory(dir), "xver-live-corpus missing — run the capture tool"
                + " (XverLiveCorpusCaptureTool) and check the fixtures in");
        List<Path> out = new ArrayList<>();
        try (var s = Files.list(dir)) {
            s.filter(p -> p.getFileName().toString().endsWith(".bin")).sorted().forEach(out::add);
        }
        assertFalse(out.isEmpty(), "xver-live-corpus has no .bin fixtures");
        // Count pinned against the committed MANIFEST (pre-D3 review L3-22): the
        // non-empty check alone lets the corpus silently shrink to one fixture.
        long manifestEntries;
        try {
            manifestEntries = Files.readAllLines(dir.resolve("MANIFEST.txt")).stream()
                    .filter(l -> l.contains(".bin")).count();
        } catch (java.io.IOException e) {
            throw new AssertionError("xver-live-corpus MANIFEST.txt unreadable", e);
        }
        assertEquals(manifestEntries, out.size(),
                "fixture count must match MANIFEST.txt — a silently shrunk corpus decodes nothing");
        return out;
    }

    @Test
    void everyLiveFixtureDecodesStrictlyAgainstThisLinesRegistries() throws Exception {
        for (Path fixture : fixtures()) {
            byte[] v20 = Files.readAllBytes(fixture);
            var column = WireSectionCursor.parse(v20, WireSectionCursor.Layout.V20);
            assertTrue(column.dictionary().size() > 0, fixture + ": empty dictionary");

            // Strict resolvers, CROSS-LINE (1.21.11 flavor): the corpus was captured on
            // 26.2 and every identity is verified to resolve on 1.21.11 too (the D3
            // re-port measurement) — so a miss here is either registry drift on a
            // future 1.21.11-line bump or a re-captured corpus introducing genuinely
            // new 26.2 identities; only the latter may move to fallback expectations
            // (see the class javadoc), never a silent widening.
            byte[] nativeBody = V20ToNativeTranslator.translate(v20,
                    identity -> {
                        Integer id = blockIds.get(identity);
                        if (id == null) {
                            throw new AssertionError(fixture + ": block identity '"
                                    + identity + "' unknown on its OWN line");
                        }
                        return id;
                    },
                    identity -> {
                        Integer id = biomeIds.get(identity);
                        if (id == null) {
                            throw new AssertionError(fixture + ": biome identity '"
                                    + identity + "' unknown on its OWN line");
                        }
                        return id;
                    },
                    blockRegistrySize, biomeRegistrySize);

            var nativeColumn = WireSectionCursor.parse(nativeBody,
                    WireSectionCursor.Layout.NATIVE);
            assertEquals(column.sections().size(), nativeColumn.sections().size(),
                    fixture + ": section structure must survive the decode");

            // Identity-level content + count shorts (C6 review M-2): a packed-index
            // remap off-by-one produces the same section count and the same resolver
            // call set — only comparing the IDENTITY AT EVERY VOXEL catches it. The
            // v20 side reads identities through the dictionary; the native side maps
            // the decoded global ids back through this line's own tables.
            var dict = column.dictionary();
            String[] blockIdentities = IdentityTables.blockIdentities();
            for (int i = 0; i < column.sections().size(); i++) {
                var sv = column.sections().get(i);
                var sn = nativeColumn.sections().get(i);
                String at = fixture + " section " + i;
                assertEquals(sv.sectionY(), sn.sectionY(), at);
                // 1.21.11 line: the native body carries ONE count short = the v20
                // neutral pair summed (this line's vanilla recalc semantics), fluid 0.
                assertEquals(sv.nonEmptyBlockCount() + sv.fluidCount(), sn.nonEmptyBlockCount(),
                        at + ": the one native count short must be the folded v20 pair");
                assertEquals(0, sn.fluidCount(),
                        at + ": NATIVE parse reports fluid 0 on this line");
                int[] v20Blocks = resolvedValues(sv.blocks(), 4096);
                int[] nativeBlocks = resolvedValues(sn.blocks(), 4096);
                for (int v = 0; v < 4096; v++) {
                    String want = dict.get(v20Blocks[v]);
                    String got = blockIdentities[nativeBlocks[v]];
                    if (!want.equals(got)) {
                        throw new AssertionError(at + " voxel " + v + ": v20 identity '"
                                + want + "' decoded to '" + got + "'");
                    }
                }
                int[] v20Biomes = resolvedValues(sv.biomes(), 64);
                int[] nativeBiomes = resolvedValues(sn.biomes(), 64);
                for (int v = 0; v < 64; v++) {
                    String want = dict.get(v20Biomes[v]);
                    String got = biomeIdentityByIdCache[nativeBiomes[v]];
                    if (!want.equals(got)) {
                        throw new AssertionError(at + " biome voxel " + v + ": '"
                                + want + "' decoded to '" + got + "'");
                    }
                }
            }
        }
    }

    private static String[] biomeIdentityByIdCache;

    /** DIRECT-aware per-voxel resolve (the CrossRegistrySimulationTest helper's twin). */
    private static int[] resolvedValues(WireSectionCursor.WireContainer c, int entries) {
        if (c.bits() == 0) {
            int[] out = new int[entries];
            java.util.Arrays.fill(out, c.palette()[0]);
            return out;
        }
        int[] values = WireSectionCursor.unpack(c.data(), c.bits(), entries);
        if (c.palette() == null) {
            return values;
        }
        int[] out = new int[entries];
        for (int i = 0; i < entries; i++) {
            out[i] = c.palette()[values[i]];
        }
        return out;
    }
}
