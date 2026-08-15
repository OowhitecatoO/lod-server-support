package dev.vox.lss.mixin;

import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.util.thread.StrictQueue;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link IOWorker}'s private {@code mailbox} and {@code storage} so the
 * background-priority read path (see {@code ChunkDiskReader}) can schedule a BACKGROUND-priority
 * region read on the same single-threaded executor vanilla loads chunks through, reading straight
 * from {@link RegionFileStorage} — the thread-safe target of IOWorker's own reads.
 *
 * <p>1.21.1 line: the IOWorker executor here is the {@code ProcessorMailbox<StrictQueue.IntRunnable>}
 * named {@code mailbox} (26.x renamed/reshaped it into {@code PriorityConsecutiveExecutor}
 * {@code consecutiveExecutor}); same single-thread + int-priority semantics, submit via
 * {@code tell(new StrictQueue.IntRunnable(priority, task))}.
 *
 * <p>Methods are {@code lss$}-prefixed because mixin adds them to the target class: unprefixed
 * names could collide with vanilla methods.
 */
@Mixin(IOWorker.class)
public interface AccessorIOWorker {
    @Accessor("mailbox")
    ProcessorMailbox<StrictQueue.IntRunnable> lss$getConsecutiveExecutor();

    @Accessor("storage")
    RegionFileStorage lss$getStorage();
}
