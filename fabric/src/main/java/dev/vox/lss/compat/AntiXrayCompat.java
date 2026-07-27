package dev.vox.lss.compat;

import dev.vox.lss.common.LSSLogger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Compatibility surface for the DrexHD AntiXray mod (mod id {@code antixray}) — design:
 * docs/planning/antixray-compat-design.md.
 *
 * <p><b>1.21.11 support line: the 26.x crash shim is a deliberate pass-through here.</b>
 * On the 26.x lines AntiXray threads its obfuscation context through {@code ScopedValue}s
 * ({@code me.drex.antixray.common.util.Arguments}) whose raw {@code .get()} THROWS when
 * unbound, so LSS binds them to null around its serialize choke points. On 1.21.11 that
 * cannot happen and needs no shim: ScopedValue is a preview API on Java 21, and the mod's
 * 1.21.11 build (verified against antixray-fabric-1.4.14+1.21.11) declares every
 * {@code Arguments} field as a {@code ThreadLocal}, whose unset {@code .get()} returns
 * null — exactly the "no packet context" value the 26.x shim binds. LSS serialization on
 * threads AntiXray never touches therefore lands on the mod's own benign
 * {@code packetInfo == null} skip path with no help from us. The probe-path containment in
 * {@code RequestProcessingService.serializeProbeContained} remains the generic crash floor.
 *
 * <p>The <b>engine probe</b> below (per-world adoption of the mod's hidden list +
 * max-block-height for LOD masking) is unchanged from the 26.x lines: its reflective
 * surface ({@code Util.getBlockController}, {@code ChunkPacketBlockControllerAntiXray}'s
 * {@code obfuscateGlobal}/{@code maxBlockHeight}) exists identically in the 1.21.11 jar.
 * Zero compile-time dependency, mirroring {@code VoxyCompat}.
  * <p>Caveat for future changes: AntiXray 1.4.x's one NON-null-safe mixin path is the
 * {@code LevelChunkSection(ValueInput)} constructor wrap (raw ThreadLocal read, NPE if
 * reached outside AntiXray's own scope). LSS never constructs sections through it —
 * every LSS construction uses the (states, biomes) or (factory) ctor. Deserializing
 * sections via ValueInput would silently turn this pass-through into a crash path.
 */
public final class AntiXrayCompat {
    private AntiXrayCompat() {}

    /**
     * Runs {@code body} unmodified. Kept as the serialize choke-point hook so the call
     * sites stay identical to the 26.x lines (and a future AntiXray context change on this
     * line has one place to reinstate a binding); see the class doc for why no binding is
     * needed on 1.21.11.
     */
    public static <T> T callSerializing(Supplier<T> body) {
        return body.get();
    }

    // ------------------------------------------------------------------
    // Engine probe (design §3 Detection): per-world adoption of AntiXray's hidden list +
    // max-block-height — "mask exactly what the packet engine masks". Reflective surface:
    // Util.getBlockController(level) → controller; DisabledChunkPacketBlockController =
    // that world is anti-xray-off; else read ChunkPacketBlockControllerAntiXray's
    // obfuscateGlobal (Object2BooleanOpenHashMap<BlockState>, hidden = true entries) and
    // maxBlockHeight fields.
    // ------------------------------------------------------------------

    /** What the engine says about ONE world. ABSENT: no mod. DISABLED: mod present, this
     *  world off. ACTIVE: this world obfuscates {@code hiddenStates} below {@code maxBlockHeight}.
     *  UNREADABLE: mod present but its internals did not resolve — callers fall back to the
     *  LSS config keys (masking stays on rather than silently leaking). */
    public sealed interface EngineView {
        record Absent() implements EngineView {}
        record Disabled() implements EngineView {}
        record Active(List<BlockState> hiddenStates, int maxBlockHeight) implements EngineView {}
        record Unreadable() implements EngineView {}
    }

    private static final EngineView ABSENT = new EngineView.Absent();
    private static final EngineView DISABLED = new EngineView.Disabled();
    private static final EngineView UNREADABLE = new EngineView.Unreadable();

    /** Resolves the classes the probe reflects over — test seam for {@link #buildEngineProbe}. */
    @FunctionalInterface
    interface EngineClassResolver {
        Class<?> resolve(String name) throws ClassNotFoundException;
    }

    /** Per-level probe function; the latch-on-failure ladder lives in {@link #buildEngineProbe}. */
    @FunctionalInterface
    public interface EngineProbe {
        EngineView probe(Level level);
    }

    private static final class EngineHandles {
        final MethodHandle getBlockController;
        final Class<?> disabledClass;
        final Class<?> antiXrayBase;
        final MethodHandle obfuscateGlobalGetter;
        final MethodHandle maxBlockHeightGetter;

        EngineHandles(EngineClassResolver resolver) throws Exception {
            var lookup = MethodHandles.lookup();
            Class<?> util = resolver.resolve("me.drex.antixray.common.util.Util");
            Class<?> controllerInterface = resolver.resolve(
                    "me.drex.antixray.common.util.controller.ChunkPacketBlockController");
            this.disabledClass = resolver.resolve(
                    "me.drex.antixray.common.util.controller.DisabledChunkPacketBlockController");
            this.antiXrayBase = resolver.resolve(
                    "me.drex.antixray.common.util.controller.ChunkPacketBlockControllerAntiXray");
            this.getBlockController = lookup.findStatic(util, "getBlockController",
                            MethodType.methodType(controllerInterface, Level.class))
                    .asType(MethodType.methodType(Object.class, Level.class));
            // Non-public fields (private obfuscateGlobal, protected maxBlockHeight) —
            // setAccessible works because mods share the unnamed-module classpath.
            var obfuscateGlobal = this.antiXrayBase.getDeclaredField("obfuscateGlobal");
            obfuscateGlobal.setAccessible(true);
            this.obfuscateGlobalGetter = lookup.unreflectGetter(obfuscateGlobal)
                    .asType(MethodType.methodType(Object.class, Object.class));
            var maxBlockHeight = this.antiXrayBase.getDeclaredField("maxBlockHeight");
            maxBlockHeight.setAccessible(true);
            this.maxBlockHeightGetter = lookup.unreflectGetter(maxBlockHeight)
                    .asType(MethodType.methodType(int.class, Object.class));
        }
    }

    private static final EngineProbe ENGINE_PROBE = buildEngineProbeProduction();

    /** Per-world engine adoption entry point. Never throws; never logs per call. */
    public static EngineView engineForLevel(Level level) {
        return ENGINE_PROBE.probe(level);
    }

    private static EngineProbe buildEngineProbeProduction() {
        try {
            return buildEngineProbe(FabricLoader.getInstance().isModLoaded("antixray"), Class::forName);
        } catch (Throwable t) {
            return level -> ABSENT;   // same throw-free-initializer floor as the carrier
        }
    }

    /**
     * The probe ladder, injectable for tests: mod absent → a constant ABSENT probe
     * (silent); handle resolution failure → a constant UNREADABLE probe + one warning;
     * else a live probe whose PER-CALL failures latch to UNREADABLE with one warning (a
     * refactored controller shape must not warn once per world per serve).
     */
    static EngineProbe buildEngineProbe(boolean modLoaded, EngineClassResolver resolver) {
        if (!modLoaded) return level -> ABSENT;
        final EngineHandles handles;
        try {
            handles = new EngineHandles(resolver);
        } catch (Throwable t) {
            warnEngineUnreadable(t);
            return level -> UNREADABLE;
        }
        var latched = new java.util.concurrent.atomic.AtomicBoolean();
        return level -> {
            if (latched.get()) return UNREADABLE;
            try {
                Object controller = handles.getBlockController.invokeExact(level);
                if (controller == null) {
                    // "Not yet known to AntiXray" is not evidence the world is anti-xray-off:
                    // on a leak-prevention feature every unresolvable shape fails SAFE
                    // (LSS-keys masking), and this one skips the latch — a transient null
                    // must not disable engine adoption for the whole session.
                    return UNREADABLE;
                }
                if (handles.disabledClass.isInstance(controller)) {
                    return DISABLED;
                }
                if (!handles.antiXrayBase.isInstance(controller)) {
                    // An unknown controller flavor obfuscates in a way we cannot read —
                    // treat as unreadable (LSS-keys fallback), not as disabled.
                    throw new IllegalStateException("unknown controller " + controller.getClass().getName());
                }
                Object rawMap = handles.obfuscateGlobalGetter.invokeExact(controller);
                int maxBlockHeight = (int) handles.maxBlockHeightGetter.invokeExact(controller);
                var states = new ArrayList<BlockState>();
                for (var entry : ((java.util.Map<?, ?>) rawMap).entrySet()) {
                    if (Boolean.TRUE.equals(entry.getValue()) && entry.getKey() instanceof BlockState state) {
                        states.add(state);
                    }
                }
                return new EngineView.Active(List.copyOf(states), maxBlockHeight);
            } catch (Throwable t) {
                if (latched.compareAndSet(false, true)) {
                    warnEngineUnreadable(t);
                }
                return UNREADABLE;
            }
        };
    }

    private static void warnEngineUnreadable(Throwable t) {
        LSSLogger.warn("AntiXray is installed but its per-world obfuscation config could not "
                + "be read — LOD masking falls back to the LSS xrayHiddenBlocks/"
                + "xrayMaxBlockHeight config keys for every world.", t);
    }
}
