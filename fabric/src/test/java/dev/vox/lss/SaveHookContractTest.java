package dev.vox.lss;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the dirty-detection save hook's compat contract (issue #69).
 *
 * <p>The regression class this guards, in two halves:
 * <ul>
 *   <li><b>The target.</b> 1.21.1 line: this MC has no {@code SerializableChunkData}
 *   (1.21.2+), so the issue-#69 retarget lands on {@code ChunkSerializer.write} — this
 *   line's static serialize-for-saving choke point, which vanilla's {@code ChunkMap.save}
 *   calls (census-verified below). The pre-fix hook targeted {@code ChunkMap.save}
 *   itself, which Moonrise @Overwrites into a throw-only stub: the injector matched ZERO
 *   targets and, under the mixin config's {@code defaultRequire = 1}, crashed the server
 *   fatally during world load. Retargeting to a method a chunk-system overhaul stubs out
 *   again would resurrect exactly that. NOTE: whether Moonrise/C2ME's 1.21.1 pipelines
 *   route through ChunkSerializer.write is FLAGGED for per-line bytecode verification
 *   (the D1 checklist row) — it is deliberately not pinned here.</li>
 *   <li><b>The crash guard.</b> {@code require = 0}: if a future overhaul bypasses even
 *   write, the miss must degrade to "no save-driven dirty detection" (edits refresh on
 *   rejoin), never to a crash. And the vanilla method must still EXIST with the expected
 *   shape, so the next MC bump that renames it turns THIS red instead of require=0
 *   silently unhooking dirty detection everywhere.</li>
 * </ul>
 *
 * <p>Reads the SOURCE file (the {@code LanHookContractTest} idiom): classes in a defined
 * mixin package refuse direct classloading under fabric-loader-junit's mixin bootstrap.
 */
class SaveHookContractTest {

    // Arg-order- and line-break-tolerant (DOTALL), and tolerant of ONE nested paren level
    // (the at = @At(...) member): a pure reformat must not red this test.
    private static final Pattern INJECT =
            Pattern.compile("@Inject\\(((?:[^()]|\\([^()]*\\))*)\\)", Pattern.DOTALL);
    private static final Pattern METHOD = Pattern.compile("method\\s*=\\s*\"([^\"]+)\"");

    /** Survives both the Gradle CWD (module dir) and an IDE repo-root CWD. */
    private static Path mixinSource() {
        var moduleRelative = Path.of("src/main/java/dev/vox/lss/mixin/ChunkSaveDataHook.java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("fabric").resolve(moduleRelative);
    }

    @Test
    void hookTargetsTheSharedWriteChokePointAndIsOptional() throws Exception {
        String source = Files.readString(mixinSource());
        var matcher = INJECT.matcher(source);
        assertTrue(matcher.find(), "the mixin declares exactly one @Inject");
        String annotationBody = matcher.group(1);
        assertFalse(matcher.find(), "exactly one @Inject expected");

        var method = METHOD.matcher(annotationBody);
        assertTrue(method.find(), "the @Inject declares a method target");
        assertEquals("write", method.group(1),
                "the hook must target ChunkSerializer.write — this line's save choke "
                        + "point; ChunkMap.save is a Moonrise throw-stub and re-targeting "
                        + "it resurrects issue #69's crash");
        assertTrue(Pattern.compile("require\\s*=\\s*0").matcher(annotationBody).find(),
                "the injection must carry require = 0 — with the config's "
                        + "defaultRequire=1, an overhaul mod that bypasses the target is "
                        + "otherwise a fatal InjectionError at world load (issue #69)");
        assertTrue(Pattern.compile("@At\\(\\s*\"RETURN\"\\s*\\)").matcher(annotationBody).find(),
                "the injection point is RETURN — it guarantees the snapshot actually "
                        + "completed (a HEAD drift would mark on snapshots that then throw)");
        assertTrue(source.contains("@Mixin(ChunkSerializer.class)"),
                "the mixin targets ChunkSerializer (1.21.1 line)");
        assertTrue(Pattern.compile("private\\s+static\\s+void\\s+lss\\$onChunkSaveData").matcher(source).find(),
                "the handler is static — a non-static handler on a static target is a "
                        + "fatal mixin apply error");
    }

    @Test
    void vanillaSavePathRoutesThroughWrite() throws Exception {
        // V-2/S7 (version-port-isolation-plan.md §3): the uncovered gap between the two
        // pins above — "write still EXISTS but the platform save path no longer ROUTES
        // through it" (require = 0 hides a dead hook; dirty detection dies silently).
        // ASM invoke-census over ChunkMap's save method(s), the MoveTraceHookContractTest
        // idiom: named-namespace vanilla bytecode via getResourceAsStream. The
        // Moonrise/C2ME arms cannot be censused from Tier 1 (reflective-only, off the
        // classpath) — they stay hand-verified per line as D1 checklist rows.
        try (var in = net.minecraft.server.level.ChunkMap.class.getResourceAsStream(
                "/net/minecraft/server/level/ChunkMap.class")) {
            org.junit.jupiter.api.Assertions.assertNotNull(in,
                    "ChunkMap bytecode must be resource-loadable in the named namespace");
            var node = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(in).accept(node,
                    org.objectweb.asm.ClassReader.SKIP_DEBUG
                            | org.objectweb.asm.ClassReader.SKIP_FRAMES);
            int writeCalls = 0;
            var sites = new java.util.ArrayList<String>();
            for (var mn : node.methods) {
                // Whole-class census, deliberately not save-scoped (V-2 review): the
                // real 1.21.1 class has exactly one ChunkSerializer.write INVOKESTATIC
                // anywhere (in save — javap-verified at the port), so this is exact
                // today — and a future line hoisting the call into a lambda$save$N
                // helper keeps the pin GREEN there, correctly: the mixin injects into
                // write itself, so a lambda-hosted call still fires the hook (a
                // save-scoped census would false-alarm dead-hook).
                for (var insn : mn.instructions) {
                    if (insn instanceof org.objectweb.asm.tree.MethodInsnNode call
                            && call.getOpcode() == org.objectweb.asm.Opcodes.INVOKESTATIC
                            && call.owner.equals(
                                    "net/minecraft/world/level/chunk/storage/ChunkSerializer")
                            && call.name.equals("write")) {
                        writeCalls++;
                        sites.add(mn.name + mn.desc);
                    }
                }
            }
            assertEquals(1, writeCalls,
                    "ChunkMap must invoke ChunkSerializer.write exactly once — "
                            + "zero means the vanilla save path stopped routing through the "
                            + "hook's target (require=0 would hide that as silently dead "
                            + "dirty detection); more than one means the snapshot choke "
                            + "point split and the whole targeting scheme needs "
                            + "re-verification. Sites: " + sites);
        }
    }

    @Test
    void vanillaWriteTargetStillExists() throws Exception {
        // The other half of the require=0 contract: an MC bump that renames or reshapes
        // write must turn this red instead of silently killing dirty detection on EVERY
        // server (only the Tier-2 dirty gametests / dirty-broadcast soak would notice
        // later). The hook's handler signature mirrors these exact parameters.
        var write = ChunkSerializer.class.getDeclaredMethod(
                "write", ServerLevel.class, ChunkAccess.class);
        assertTrue(Modifier.isStatic(write.getModifiers()),
                "write is static (the hook handler is static to match)");
        assertEquals(CompoundTag.class, write.getReturnType());
    }
}
