package corgitaco.modid.core;

import corgitaco.modid.mixin.access.ChunkManagerAccess;
import corgitaco.modid.mixin.access.StructureAccess;
import corgitaco.modid.structure.AdditionalStructureContext;
import corgitaco.modid.util.DataForChunk;
import corgitaco.modid.world.path.IPathGenerator;
import corgitaco.modid.world.path.PathfindingPathGenerator;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class StructureRegionManager {

    public static final String[] NAMES = new String[]{
            "Perthlochry",
            "Bournemouth",
            "Wimborne",
            "Bredon",
            "Ballachulish",
            "Sudbury",
            "Emall",
            "Bellmare",
            "Garrigill",
            "Polperro",
            "Lakeshore",
            "Wolfden",
            "Aberuthven",
            "Warrington",
            "Northwich",
            "Ascot",
            "Coalfell",
            "Calchester",
            "Stanmore",
            "Clacton",
            "Wanborne",
            "Alnwick",
            "Rochdale",
            "Gormsey",
            "Favorsham",
            "Clare View Point",
            "Aysgarth",
            "Wimborne",
            "Tarrin",
            "Arkmunster",
            "Mirefield",
            "Banrockburn",
            "Acrine",
            "Oldham",
            "Glenarm",
            "Pathstow",
            "Ballachulish",
            "Dumbarton",
            "Carleone",
            "Llanybydder",
            "Norwich",
            "Banrockburn",
            "Auchendale",
            "Arkaley",
            "Aeberuthey",
            "Peltragow",
            "Clarcton",
            "Garigill",
            "Nantwich",
            "Zalfari",
            "Portsmouth",
            "Transmere",
            "Blencathra",
            "Bradford",
            "Thorpeness",
            "Swordbreak",
            "Thorpeness",
            "Aeston",
            "Azmarin",
            "Haran"
    };

    private final Path savePath;
    private final Long2ReferenceOpenHashMap<StructureRegion> structureRegions;
    Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>> dataForLocation = new Long2ReferenceOpenHashMap<>();
    public StructureRegionManager(ServerWorld world) {
        this.savePath = ((ChunkManagerAccess) world.getChunkSource().chunkMap).getStorageFolder().toPath().resolve("structures");
        try {
            Files.createDirectories(savePath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.structureRegions = new Long2ReferenceOpenHashMap<>();
    }

    public Long2ReferenceOpenHashMap<AdditionalStructureContext> getRegionStructurePositions(long regionPos, Structure<?> structure) {
        StructureRegion structureRegion = structureRegions.computeIfAbsent(regionPos, (regionKey) -> new StructureRegion());
        return structureRegion.getRegionStructures().computeIfAbsent(structure, structure1 -> {
            CompoundNBT regionNbt = getRegionNbt(regionPos);
            return regionNbt != null ? structureRegion.regionStructurePositionsFromFile(regionNbt, structure) : new Long2ReferenceOpenHashMap<>();
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

    public Object2ObjectArrayMap<Structure<?>, Long2ReferenceOpenHashMap<AdditionalStructureContext>> allRegionStructurePositions(long regionPos) {
        StructureRegion structureRegion = structureRegions.computeIfAbsent(regionPos, (regionKey) -> new StructureRegion());
        CompoundNBT regionNbt = getRegionNbt(regionPos);
        return regionNbt != null ? structureRegion.allRegionStructurePositionsFromFile(regionNbt) : structureRegion.getRegionStructures();
    }

    public PathfindingPathGenerator getPathGeneratorForKey(long regionPos, StructureRegion.PathKey pathKey) {
        StructureRegion structureRegion = structureRegions.computeIfAbsent(regionPos, (regionKey) -> new StructureRegion());
        CompoundNBT regionNbt = getRegionNbt(regionPos);
        return regionNbt != null ? structureRegion.regionPathGeneratorByKeyFromFile(regionNbt, pathKey) : structureRegion.getPathGeneratorNeighbors().get(pathKey);
    }

    public StructureRegion generateWithStructure(ServerWorld world, long regionKey, Structure<?> structure) {
        int range = 1;
        ChunkGenerator generator = world.getChunkSource().getGenerator();
        StructureRegion structureRegion = this.structureRegions.computeIfAbsent(regionKey, (key) -> new StructureRegion());
        int currentRegionX = getX(regionKey);
        int currentRegionZ = getZ(regionKey);

        if (!structureRegion.isGenerated()) {
            for (int regionX = currentRegionX - range; regionX <= currentRegionX + range; regionX++) {
                for (int regionZ = currentRegionZ - range; regionZ <= currentRegionZ + range; regionZ++) {
                    long neighborKey = regionKey(regionX, regionZ);
                    StructureRegion neighborRegion = this.structureRegions.computeIfAbsent(neighborKey, (key) -> new StructureRegion());
                    if (!neighborRegion.isGenerated()) {
                        generateStructureRegion(world, world.getSeed(), generator.getBiomeSource(), structure, generator.getSettings().getConfig(structure), neighborRegion, this.dataForLocation, neighborKey);
                        neighborRegion.setGenerated();
                    }
                }
            }
            generateStructureRegion(world, world.getSeed(), generator.getBiomeSource(), structure, generator.getSettings().getConfig(structure), structureRegion, this.dataForLocation, regionKey);
            structureRegion.setGenerated();
        }
        return structureRegion;
    }

    public void generateStructureRegion(ServerWorld world, long seed, BiomeProvider biomeSource, Structure<?> structure, StructureSeparationSettings structureSeparationSettings, StructureRegion structureRegion, Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>> dataForLocation, long regionKey) {
        long startTime = System.currentTimeMillis();

        int regionX = getX(regionKey);
        int regionZ = getZ(regionKey);

        int spacing = structureSeparationSettings.spacing();

        int activeMinChunkX = regionToChunk(regionX);
        int activeMinChunkZ = regionToChunk(regionZ);

        int activeMaxChunkX = regionToMaxChunk(regionX);
        int activeMaxChunkZ = regionToMaxChunk(regionZ);

        int activeMinGridX = Math.floorDiv(activeMinChunkX, spacing);
        int activeMinGridZ = Math.floorDiv(activeMinChunkZ, spacing);

        int activeMaxGridX = Math.floorDiv(activeMaxChunkX, spacing);
        int activeMaxGridZ = Math.floorDiv(activeMaxChunkZ, spacing);

        scanRegionStructureGridAndLinkNeighbors(world, seed, biomeSource, structure, structureSeparationSettings, structureRegion, dataForLocation, activeMinGridX, activeMinGridZ, activeMaxGridX, activeMaxGridZ);

        System.out.println("Generated links and paths for region (" + regionX + ", " + regionZ + ") in " + (System.currentTimeMillis() - startTime) + " ms");
    }


    private void scanRegionStructureGridAndLinkNeighbors(ServerWorld world, long seed, BiomeProvider biomeSource, Structure<?> structure, StructureSeparationSettings structureSeparationSettings, StructureRegion region, Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>> dataForLocation, int activeMinGridX, int activeMinGridZ, int activeMaxGridX, int activeMaxGridZ) {
        Random random = new Random(seed);
        int neighborRange = 1;

        for (int structureGridX = activeMinGridX; structureGridX <= activeMaxGridX; structureGridX++) {
            for (int structureGridZ = activeMinGridZ; structureGridZ <= activeMaxGridZ; structureGridZ++) {
                long structurePosFromGrid = getStructurePosFromGrid(structureGridX, structureGridZ, seed, biomeSource, structure, structureSeparationSettings);

                if (structurePosFromGrid == Long.MIN_VALUE) {
                    continue;
                }

                AdditionalStructureContext additionalStructureContext = new AdditionalStructureContext(NAMES[random.nextInt(NAMES.length - 1)]);
                Long2ReferenceOpenHashMap<AdditionalStructureContext> structureData = region.getRegionStructures().computeIfAbsent(structure, (value) -> new Long2ReferenceOpenHashMap<>());
                structureData.put(structurePosFromGrid, additionalStructureContext);


                linkNeighbors(world, seed, biomeSource, structure, structureSeparationSettings, region, dataForLocation, NAMES[random.nextInt(NAMES.length - 1)], neighborRange, structureGridX, structureGridZ, structurePosFromGrid, additionalStructureContext);
            }
        }
    }

    private void linkNeighbors(ServerWorld world, long seed, BiomeProvider biomeSource, Structure<?> structure, StructureSeparationSettings structureSeparationSettings, StructureRegion structureRegion, Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>> dataForLocation, String name, int neighborRange, int structureGridX, int structureGridZ, long structurePosFromGrid, AdditionalStructureContext additionalStructureContext) {
        for (int neighborStructureGridX = -neighborRange; neighborStructureGridX < neighborRange; neighborStructureGridX++) {
            for (int neighborStructureGridZ = -neighborRange; neighborStructureGridZ < neighborRange; neighborStructureGridZ++) {
                long neighborStructurePosFromGrid = getStructurePosFromGrid(structureGridX + neighborStructureGridX, structureGridZ + neighborStructureGridZ, seed, biomeSource, structure, structureSeparationSettings);


                if (neighborStructurePosFromGrid == Long.MIN_VALUE) {
                    continue;
                }

                long neighborStructureRegionKey = chunkToRegionKey(neighborStructurePosFromGrid);

                AdditionalStructureContext neighborAdditionalStructureContext = new AdditionalStructureContext(name);
                Long2ReferenceOpenHashMap<AdditionalStructureContext> neighborStructureData = this.structureRegions.computeIfAbsent(neighborStructureRegionKey, (key) -> new StructureRegion()).getRegionStructures().computeIfAbsent(structure, (key) -> new Long2ReferenceOpenHashMap<>());

                neighborStructureData.put(neighborStructurePosFromGrid, additionalStructureContext);

                additionalStructureContext.getConnections().add(neighborStructurePosFromGrid);
                neighborAdditionalStructureContext.getConnections().add(neighborStructurePosFromGrid);


                SharedSeedRandom structureRandom = new SharedSeedRandom();
                structureRandom.setLargeFeatureWithSalt(seed, ChunkPos.getX(structurePosFromGrid) + ChunkPos.getX(neighborStructurePosFromGrid), ChunkPos.getZ(structurePosFromGrid) + ChunkPos.getZ(neighborStructurePosFromGrid), structureSeparationSettings.salt());

                PathfindingPathGenerator pathGenerator = new PathfindingPathGenerator(world, new IPathGenerator.Point<>(structure, getPosFromChunk(structurePosFromGrid)), new IPathGenerator.Point<>(structure, getPosFromChunk(neighborStructurePosFromGrid)), dataForLocation);
                if (!pathGenerator.dispose()) {
                    long saveRegionKey = pathGenerator.saveRegion();
                    StructureRegion.PathKey pathGeneratorKey = new StructureRegion.PathKey(getChunkFromPos(pathGenerator.getStart().getPos()), getChunkFromPos(pathGenerator.getEnd().getPos()));
                    this.structureRegions.computeIfAbsent(saveRegionKey, (key) -> new StructureRegion()).pathGenerators().put(pathGeneratorKey, pathGenerator);

                    for (long aLong : pathGenerator.getNodesByRegion().keySet()) {
                        if (saveRegionKey == aLong) {
                            continue;
                        }
                        StructureRegion structureRegion1 = this.structureRegions.computeIfAbsent(aLong, (key) -> new StructureRegion());
                        structureRegion1.regionToPathGeneratorReferences().computeIfAbsent(aLong, (key) -> new HashSet<>()).add(pathGeneratorKey);
                        structureRegion1.getPathGeneratorNeighbors().putIfAbsent(pathGeneratorKey, pathGenerator);
                    }
                }
            }
        }
    }

    public static long getStructurePosFromGrid(int structureGridX, int structureGridZ, long worldSeed, BiomeProvider biomeSource, Structure<?> structure, StructureSeparationSettings structureSeparationSettings) {
        long structureChunkPos = getStructureChunkPos(structure, worldSeed, structureSeparationSettings.salt(), new SharedSeedRandom(), structureSeparationSettings.spacing(), structureSeparationSettings.separation(), structureGridX, structureGridZ);
        int structureChunkPosX = ChunkPos.getX(structureChunkPos);
        int structureChunkPosZ = ChunkPos.getZ(structureChunkPos);

        // Verify this biome is a valid biome for this structure
        if (!sampleAndTestChunkBiomesForStructure(structureChunkPosX, structureChunkPosZ, biomeSource, structure)) {
            return Long.MIN_VALUE;
        }
        long chunkPos = structure.getPotentialFeatureChunk(structureSeparationSettings, worldSeed, new SharedSeedRandom(), structureChunkPosX, structureChunkPosZ).toLong();

        return chunkPos == structureChunkPos ? structureChunkPos : Long.MIN_VALUE;
    }


    public static boolean sampleAndTestChunkBiomesForStructure(int chunkX, int chunkZ, BiomeManager.IBiomeReader biomeReader, Structure<?> structure) {
        return biomeReader.getNoiseBiome((chunkX << 2) + 2, 0, (chunkZ << 2) + 2).getGenerationSettings().isValidStart(structure);
    }

    private static long getStructureChunkPos(Structure<?> structure, long worldSeed, int structureSalt, SharedSeedRandom seedRandom, int spacing, int separation, int floorDivX, int floorDivZ) {
        int x;
        int z;
        seedRandom.setLargeFeatureWithSalt(worldSeed, floorDivX, floorDivZ, structureSalt);

        if (((StructureAccess) structure).invokeLinearSeparation()) {
            x = seedRandom.nextInt(spacing - separation);
            z = seedRandom.nextInt(spacing - separation);
        } else {
            x = (seedRandom.nextInt(spacing - separation) + seedRandom.nextInt(spacing - separation)) / 2;
            z = (seedRandom.nextInt(spacing - separation) + seedRandom.nextInt(spacing - separation)) / 2;
        }
        return ChunkPos.asLong(floorDivX * spacing + x, floorDivZ * spacing + z);
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

    public static long getChunkFromPos(BlockPos pos) {
        int chunkX = ChunkPos.getX(SectionPos.blockToSectionCoord(pos.getX()));
        int chunkZ = ChunkPos.getZ(SectionPos.blockToSectionCoord(pos.getZ()));
        return ChunkPos.asLong(chunkX, chunkZ);
    }

    public interface Access {

        StructureRegionManager getStructureRegionManager();
    }
}
