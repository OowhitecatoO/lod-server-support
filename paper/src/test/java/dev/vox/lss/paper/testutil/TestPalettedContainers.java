package dev.vox.lss.paper.testutil;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;

/**
 * 1.21.1 line: the test-side stand-in for 26.x's {@code PalettedContainerFactory}
 * surface (that class does not exist on this MC). Each method is the vanilla-exact
 * construction for this line — the same forms the main sources use (SectionConstruction
 * javadoc; NbtSectionSerializer's scopedFor) — so test fixtures and production encode
 * against identical codecs/strategies. The seam handle the suites pass around stays the
 * biome {@link Registry} (the retargeted parameter type).
 */
public final class TestPalettedContainers {
    private TestPalettedContainers() {}

    public static PalettedContainer<BlockState> createForBlockStates() {
        return new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY,
                Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES);
    }

    public static PalettedContainer<Holder<Biome>> createForBiomes(Registry<Biome> biomeRegistry) {
        return new PalettedContainer<>(biomeRegistry.asHolderIdMap(),
                biomeRegistry.getHolderOrThrow(Biomes.PLAINS),
                PalettedContainer.Strategy.SECTION_BIOMES);
    }

    /** Vanilla's ChunkSerializer BLOCK_STATE_CODEC shape (codecRW). */
    public static Codec<PalettedContainer<BlockState>> blockStatesContainerCodec() {
        return PalettedContainer.codecRW(Block.BLOCK_STATE_REGISTRY, BlockState.CODEC,
                PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());
    }

    /** Vanilla's makeBiomeCodec shape (codecRO). */
    public static Codec<PalettedContainerRO<Holder<Biome>>> biomeContainerCodec(
            Registry<Biome> biomeRegistry) {
        return PalettedContainer.codecRO(biomeRegistry.asHolderIdMap(),
                biomeRegistry.holderByNameCodec(),
                PalettedContainer.Strategy.SECTION_BIOMES,
                biomeRegistry.getHolderOrThrow(Biomes.PLAINS));
    }
}
