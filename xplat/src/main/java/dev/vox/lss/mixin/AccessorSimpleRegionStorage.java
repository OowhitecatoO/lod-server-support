package dev.vox.lss.mixin;

import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private {@code worker} field so LSS can reach the IOWorker that {@link
 * net.minecraft.server.level.ChunkMap} reads through, to schedule BACKGROUND-priority
 * reads on the same executor vanilla uses.
 *
 * <p>1.21.10 line (the 1.21.1 line's exact adaptation): on this MC {@code ChunkMap
 * extends ChunkStorage} and the worker field lives on {@link ChunkStorage} directly
 * ({@code SimpleRegionStorage} exists here but is NOT in ChunkMap's hierarchy — that
 * superclass move is 1.21.11+/26.x, where this accessor targets it; the class NAME is
 * kept so the mixin listing and the cross-line call sites stay textually stable).
 * Failure shapes split (surfaces row 1): a MISSING field on the target class is a
 * LOUD mixin apply error at load (defaultRequire = 1); a wrong-class resolve at
 * runtime (the shape this port hit: the parent line's cast CCE'd) is caught by
 * resolveBackgroundHandles' catch-all, latches backgroundIncompatible, and the
 * C2ME-style throttle fallback engages — the silent inertness the Tier-2 raw_serves
 * receipt caught at the port.
 *
 * <p>The method is {@code lss$}-prefixed because mixin adds it to the target class: an
 * unprefixed {@code getWorker()} could collide with a vanilla method of that name.
 */
@Mixin(ChunkStorage.class)
public interface AccessorSimpleRegionStorage {
    @Accessor("worker")
    IOWorker lss$getWorker();
}
