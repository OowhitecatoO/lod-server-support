package dev.vox.lss.mixin;

import dev.vox.lss.networking.server.LSSServerNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dirty-detection source: fires whenever a chunk save actually proceeds, on the thread
 * that owns the live chunk.
 *
 * <p>1.21.1 line: this MC has NO {@code SerializableChunkData} (that snapshot type is
 * 1.21.2+), so the issue-#69 retarget lands on {@code ChunkSerializer.write(ServerLevel,
 * ChunkAccess)} RETURN — the static "serialize the live chunk for saving" choke point of
 * this line, the direct ancestor of {@code SerializableChunkData.copyOf}. Vanilla's
 * {@code ChunkMap.save} funnels every save through it. Modded-chunk-system routing
 * bytecode-verified for this line (the 26.2 copyOf method, 2026-08-14): vanilla
 * (intermediary {@code class_2852.method_12410} = this target; javap invoke census),
 * Moonrise 1.21.1 ({@code NewChunkHolder.saveChunk} invokes it directly — and Fabric
 * Moonrise's {@code supportsAsyncChunkSave} compiles to {@code return false}, so the
 * sync path is the live one), and C2ME 1.21.1 ({@code SerializerAccess} /
 * ReadFromDiskAsync delegate to it). A future chunk-system rewrite that bypasses it
 * degrades to no save-driven dirty detection, never a crash (require = 0).
 * The hook body lives in {@link LSSServerNetworking#onChunkSaveData} (mixin classes
 * refuse classloading under fabric-loader-junit, so logic in the mixin is untestable
 * logic).
 */
@Mixin(ChunkSerializer.class)
public class ChunkSaveDataHook {

    // require = 0 (issue #69's crash lesson, kept on this line's target): a chunk-system
    // overhaul that bypasses ChunkSerializer.write must degrade to "no save-driven
    // dirty detection" (edits refresh on rejoin), never to a fatal InjectionError under
    // the config's defaultRequire=1. Vanilla-path regression cover: the Tier-2
    // DirtyContentFilter gametests and the dirty-broadcast soak both fail if this hook
    // silently stops firing on an unmodified server, and Mixin still logs its own
    // expect-level warning when the target is missing. Pinned by SaveHookContractTest
    // (source-regex).
    @Inject(method = "write", at = @At("RETURN"), require = 0)
    private static void lss$onChunkSaveData(ServerLevel level, ChunkAccess chunk,
                                            CallbackInfoReturnable<CompoundTag> cir) {
        LSSServerNetworking.onChunkSaveData(level, chunk);
    }
}
