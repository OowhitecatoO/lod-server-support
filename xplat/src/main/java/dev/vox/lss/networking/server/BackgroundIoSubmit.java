package dev.vox.lss.networking.server;

import dev.vox.lss.mixin.AccessorIOWorker;
import dev.vox.lss.mixin.AccessorSimpleRegionStorage;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.util.thread.StrictQueue;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The background-read SUBMIT seam (V-3/S4, version-port-isolation-plan.md §S4 —
 * scope-capped): the per-MC-line vanilla-IOWorker facts — executor TYPE, submission
 * shape, priority ordinal, accessor resolution — extracted so a support port flavors
 * THIS file instead of threading a new executor type through {@code ChunkDiskReader}'s
 * signatures (this file IS the 1.21.1 flavor). The read ladder, latch, throttle, and
 * warn stay in {@code ChunkDiskReader} — the plan's §7 cap: if the seam wants the
 * ladder, stop.
 */
final class BackgroundIoSubmit {
    private BackgroundIoSubmit() {}

    // IOWorker$Priority is package-private and cannot be named here, so the ordinal is
    // pinned. Verified against the 1.21.1 jar (this support line — same ordering as
    // 26.2): FOREGROUND(0), BACKGROUND(1), SHUTDOWN(2). The SerializerParityGameTests
    // byte-parity test exercises this path end-to-end but does NOT pin the enum order
    // (any in-range ordinal returns identical bytes); a vanilla reorder must be
    // re-verified by hand.
    //
    // Where this lands us on the shared per-dimension executor: vanilla's chunk loads
    // (loadAsync -> submitTask) run FOREGROUND, and vanilla's chunk saves
    // (storePendingChunk) run BACKGROUND. So LOD reads at BACKGROUND sit strictly below
    // the loads players wait on, and tie with saves — an improvement for both, since
    // chunkMap.read used to put LOD reads at FOREGROUND, level with vanilla's loads and
    // ahead of its saves.
    static final int PRIORITY_BACKGROUND = 1;

    /**
     * The IOWorker handles the background path needs; either may be null on an
     * incompatible server. Opaque to {@code ChunkDiskReader} — the executor type is this
     * line's fact and must not leak back into shared signatures.
     */
    record Handles(ProcessorMailbox<StrictQueue.IntRunnable> executor, RegionFileStorage storage) {}

    /**
     * Resolve the IOWorker's [executor, storage] handles from the chunk map — the
     * accessor-resolution site (per-line: the accessor mixins' targets move with MC).
     * Throws whatever the accessors throw; the caller treats any throw like null handles.
     */
    static Handles resolve(ChunkMap chunkMap) {
        var worker = (AccessorIOWorker) ((AccessorSimpleRegionStorage) chunkMap).lss$getWorker();
        return new Handles(worker.lss$getConsecutiveExecutor(), worker.lss$getStorage());
    }

    /**
     * Schedule {@code body} on this line's IOWorker executor at BACKGROUND priority.
     * 1.21.1 shape: mailbox {@code tell()} replaces 26.x's {@code scheduleWithResult} —
     * same executor, same int priority; the future is completed by the task body. A
     * mailbox already closed at shutdown silently drops the task and the read burns its
     * 10 s timeout (matches vanilla's own submitTask exposure on this line).
     */
    static <T> CompletableFuture<T> schedule(Handles handles, Consumer<CompletableFuture<T>> body) {
        var f = new CompletableFuture<T>();
        handles.executor().tell(new StrictQueue.IntRunnable(PRIORITY_BACKGROUND, () -> body.accept(f)));
        return f;
    }
}
