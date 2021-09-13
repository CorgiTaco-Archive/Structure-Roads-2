package corgitaco.modid.world.path;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public interface IPathGenerator {
    Long2ObjectArrayMap<List<BlockPos>> getNodesByChunk();
    Long2ObjectArrayMap<List<BlockPos>> getLightsByChunk();
    BlockState debugState();
}
