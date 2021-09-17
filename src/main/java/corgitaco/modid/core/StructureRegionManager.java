package corgitaco.modid.core;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.gen.feature.structure.Structure;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class StructureRegionManager {

    private final Path savePath;
    private final Long2ReferenceOpenHashMap<StructureRegion> structureRegions;

    public StructureRegionManager(Path savePath) {
        this.savePath = savePath;
        try {
            Files.createDirectories(savePath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.structureRegions = new Long2ReferenceOpenHashMap<>();
    }

    public LongSet getRegionStructurePositions(long regionPos, Structure<?> structure) {
        StructureRegion structureRegion = structureRegions.computeIfAbsent(regionPos, (regionKey) -> new StructureRegion());
        return structureRegion.getRegionStructures().computeIfAbsent(structure, structure1 -> {
            CompoundNBT regionNbt = getRegionNbt(regionPos);
            return regionNbt != null ? structureRegion.regionStructurePositionsFromFile(regionNbt, structure) : new LongOpenHashSet();
        });
    }

    @Nullable
    public CompoundNBT getRegionNbt(long regionPos) {
        int x = getX(regionPos);
        int z = getZ(regionPos);

        File file = savePath.resolve(String.format("%s,%s.2dr", x, z)).toFile();

        try {
            return file.exists() ? CompressedStreamTools.read(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public Object2ObjectArrayMap<Structure<?>, LongSet> allRegionStructurePositions(long regionPos) {
        StructureRegion structureRegion = structureRegions.computeIfAbsent(regionPos, (regionKey) -> new StructureRegion());
        CompoundNBT regionNbt = getRegionNbt(regionPos);
        return regionNbt != null ? structureRegion.allRegionStructurePositionsFromFile(regionNbt) : structureRegion.getRegionStructures();
    }

    public static int getX(long regionPos) {
        return (int) (regionPos & 4294967295L);
    }

    public static int getZ(long regionPos) {
        return (int) (regionPos >>> 32 & 4294967295L);
    }

    // Region size is 256 x 256 chunks or 4096 x 4096 blocks
    public static long regionKey(int regionX, int regionZ) {
        return (long) regionX & 4294967295L | ((long) regionZ & 4294967295L) << 32;
    }

    public static int chunkToRegion(int coord) {
        return coord >> 8;
    }

    public static int blockToRegion(int coord) {
        return coord >> 12;
    }

    public static int regionToBlock(int coord) {
        return coord << 12;
    }

    public static int regionToChunk(int coord) {
        return coord << 8;
    }

    public static int regionToMaxChunk(int coord) {
        return regionToChunk(coord + 1) - 1;
    }

    public static long chunkToRegionKey(long chunk) {
        return regionKey(chunkToRegion(ChunkPos.getX(chunk)), chunkToRegion(ChunkPos.getZ(chunk)));
    }

    public static BlockPos getPosFromChunk(long chunk) {
        int chunkX = ChunkPos.getX(chunk);
        int chunkZ = ChunkPos.getZ(chunk);
        return new BlockPos(SectionPos.sectionToBlockCoord(chunkX), 0, SectionPos.sectionToBlockCoord(chunkZ));
    }

    public interface Access {

        StructureRegionManager getStructureRegionManager();
    }
}
