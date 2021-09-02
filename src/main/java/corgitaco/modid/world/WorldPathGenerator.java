package corgitaco.modid.world;

import com.mojang.serialization.Codec;
import corgitaco.modid.mixin.access.StructureAccess;
import corgitaco.modid.structure.AdditionalStructureContext;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraft.world.server.ServerWorld;

import java.util.Map;
import java.util.Random;
import java.util.Set;

public class WorldPathGenerator extends Feature<NoFeatureConfig> {

    public static final boolean DEBUG = true;

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

    public WorldPathGenerator(Codec<NoFeatureConfig> codec) {
        super(codec);
    }

    private final Map<ServerWorld, Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>>> contextCacheForLevel = new Object2ObjectArrayMap<>();
    private final Map<ServerWorld, LongSet> completedRegionStructureCachesForLevel = new Object2ObjectArrayMap<>();
    private final Map<ServerWorld, LongSet> completedLinkedRegionsForLevel = new Object2ObjectArrayMap<>();
    private final Map<ServerWorld, Long2ObjectArrayMap<Long2ObjectArrayMap<AdditionalStructureContext>>> surroundingRegionStructureCachesForRegionForLevel = new Object2ObjectArrayMap<>();

    @Override
    public boolean place(ISeedReader world, ChunkGenerator generator, Random random, BlockPos pos, NoFeatureConfig config) {
        long seed = world.getSeed();

        int minConnectionCount = 2;
        int maxConnectionCount = 5;

        int x = pos.getX();
        int chunkX = SectionPos.blockToSectionCoord(x);
        int z = pos.getZ();
        int chunkZ = SectionPos.blockToSectionCoord(z);
        long currentChunk = ChunkPos.asLong(chunkX, chunkZ);

        int currentRegionX = chunkToRegion(chunkX);
        int currentRegionZ = chunkToRegion(chunkZ);
        long currentRegionKey = regionKey(chunkToRegion(chunkX), chunkToRegion(chunkZ));

        Structure<?> structure = Structure.BURIED_TREASURE;
        StructureSeparationSettings structureSeparationSettings = generator.getSettings().structureConfig().get(structure);

        Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> structuresByRegion = contextCacheForLevel.computeIfAbsent(world.getLevel(), (serverWorld -> new Long2ReferenceOpenHashMap<>()));
        LongSet completedStructureCacheRegions = completedRegionStructureCachesForLevel.computeIfAbsent(world.getLevel(), (serverWorld -> new LongArraySet()));
        LongSet completedLinkedRegions = completedLinkedRegionsForLevel.computeIfAbsent(world.getLevel(), (serverWorld -> new LongArraySet()));
        Long2ObjectArrayMap<Long2ObjectArrayMap<AdditionalStructureContext>> surroundingRegionStructureCachesForRegion = surroundingRegionStructureCachesForRegionForLevel.computeIfAbsent(world.getLevel(), (serverWorld -> new Long2ObjectArrayMap<>()));

        int range = 1;


        // In cases such as buried treasure where there are 20k+ buried treasures in a given 3x3 region area,
        // creating a new Long2ObjectArrayMap every chunk call & using a .putAll is very inefficient due to the number of entries.
        // So we've cut down performance cost by making this cache get created by region not every single chunk call.
        Long2ObjectArrayMap<AdditionalStructureContext> surroundingRegionStructures = surroundingRegionStructureCachesForRegion.computeIfAbsent(currentRegionKey, (key) -> {
            Long2ObjectArrayMap<AdditionalStructureContext> computedSurroundingRegionStructures = new Long2ObjectArrayMap<>();
            for (int regionX = currentRegionX - range; regionX <= currentRegionX + range; regionX++) {
                for (int regionZ = currentRegionZ - range; regionZ <= currentRegionZ + range; regionZ++) {
                    long regionKey = regionKey(regionX, regionZ);

                    if (!completedStructureCacheRegions.contains(regionKey)) {
                        collectRegionStructures(seed, generator.getBiomeSource(), structure, structureSeparationSettings, structuresByRegion, regionX, regionZ);
                        completedStructureCacheRegions.add(regionKey);
                    }
                    computedSurroundingRegionStructures.putAll(structuresByRegion.get(regionKey));
                }
            }
            return computedSurroundingRegionStructures;
        });

        surroundingRegionStructureCachesForRegion.put(currentRegionKey, surroundingRegionStructures);

        Long2ObjectArrayMap<AdditionalStructureContext> currentRegionStructures = structuresByRegion.get(currentRegionKey);

        if (!completedLinkedRegions.contains(currentRegionKey)) {
            linkStructures(seed, minConnectionCount, maxConnectionCount, structureSeparationSettings, surroundingRegionStructures, currentRegionStructures);
            completedLinkedRegions.add(currentRegionKey);
        }

        if (DEBUG) {
            if (currentRegionStructures.containsKey(currentChunk)) {
                BlockPos.Mutable mutable = new BlockPos.Mutable().set(x, world.getHeight(Heightmap.Type.OCEAN_FLOOR_WG, x, z), z);
                for (int buildY = 0; buildY < 25; buildY++) {
                    world.setBlock(mutable, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
                    mutable.move(Direction.UP);
                }
            }
        }

        return true;
    }

    private void linkStructures(long seed, int minConnectionCount, int maxConnectionCount, StructureSeparationSettings structureSeparationSettings, Long2ObjectArrayMap<AdditionalStructureContext> surroundingRegionStructures, Long2ObjectArrayMap<AdditionalStructureContext> currentRegionStructures) {
        long[] currentAndSurroundingRegionStructurePositions = surroundingRegionStructures.keySet().toLongArray();

        for (Long2ObjectMap.Entry<AdditionalStructureContext> structureAdditionalContextEntry : currentRegionStructures.long2ObjectEntrySet()) {
            long structurePos = structureAdditionalContextEntry.getLongKey();

            SharedSeedRandom structureRandom = new SharedSeedRandom();
            structureRandom.setLargeFeatureWithSalt(seed, ChunkPos.getX(structurePos), ChunkPos.getZ(structurePos), structureSeparationSettings.salt());

            AdditionalStructureContext currentStructureContext = structureAdditionalContextEntry.getValue();
            Set<Long> connections = currentStructureContext.getConnections();

            int connectionCount = structureRandom.nextInt((maxConnectionCount - minConnectionCount) + 1) + minConnectionCount;
            while (connections.size() < connectionCount) {
                long target = currentAndSurroundingRegionStructurePositions[structureRandom.nextInt(currentAndSurroundingRegionStructurePositions.length - 1)];
                AdditionalStructureContext targetStructureContext = surroundingRegionStructures.get(target);

                // Make these structures aware that they're connected each other.
                targetStructureContext.getConnections().add(structurePos);
                currentStructureContext.getConnections().add(target);
            }
        }
    }


    private static void collectRegionStructures(long seed, BiomeProvider biomeSource, Structure<?> village, StructureSeparationSettings structureSeparationSettings, Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> regionPositions, int regionX, int regionZ) {
        int spacing = structureSeparationSettings.spacing();

        int activeMinChunkX = regionToChunk(regionX);
        int activeMinChunkZ = regionToChunk(regionZ);

        int activeMaxChunkX = regionToMaxChunk(regionX);
        int activeMaxChunkZ = regionToMaxChunk(regionZ);

        int activeMinGridX = Math.floorDiv(activeMinChunkX, spacing);
        int activeMinGridZ = Math.floorDiv(activeMinChunkZ, spacing);

        int activeMaxGridX = Math.floorDiv(activeMaxChunkX, spacing);
        int activeMaxGridZ = Math.floorDiv(activeMaxChunkZ, spacing);

        scanRegionStructureGrid(seed, biomeSource, village, structureSeparationSettings, spacing, regionPositions, activeMinGridX, activeMinGridZ, activeMaxGridX, activeMaxGridZ);
    }


    /**
     * Creates the cache of all structure positions for the given region by iterating over the structure grid which should guarantee a structure position per iteration.
     */
    private static void scanRegionStructureGrid(long seed, BiomeProvider biomeSource, Structure<?> structure, StructureSeparationSettings structureSeparationSettings, int spacing, Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> regionPositions, int activeMinGridX, int activeMinGridZ, int activeMaxGridX, int activeMaxGridZ) {
        Random random = new Random(seed);

        for (int structureGridX = activeMinGridX; structureGridX <= activeMaxGridX; structureGridX++) {
            for (int structureGridZ = activeMinGridZ; structureGridZ <= activeMaxGridZ; structureGridZ++) {
                long structureChunkPos = getStructureChunkPos(structure, seed, structureSeparationSettings.salt(), new SharedSeedRandom(), spacing, structureSeparationSettings.separation(), structureGridX, structureGridZ);

                int structureChunkPosX = ChunkPos.getX(structureChunkPos);
                int structureChunkPosZ = ChunkPos.getZ(structureChunkPos);
                long structureRegionKey = regionKey(chunkToRegion(structureChunkPosX), chunkToRegion(structureChunkPosZ));

                Long2ObjectArrayMap<AdditionalStructureContext> structureData = regionPositions.computeIfAbsent(structureRegionKey, (value) -> new Long2ObjectArrayMap<>());

                // Verify this biome is a valid biome for this structure
                if (!sampleAndTestChunkBiomesForStructure(structureChunkPosX, structureChunkPosZ, biomeSource, structure)) {
                    continue;
                }


                // We need to verify that we've actually got a valid structure start.
                long chunkPos = structure.getPotentialFeatureChunk(structureSeparationSettings, seed, new SharedSeedRandom(), structureChunkPosX, structureChunkPosZ).toLong();

                if (chunkPos == structureChunkPos) {
                    structureData.put(structureChunkPos, new AdditionalStructureContext(NAMES[random.nextInt(NAMES.length - 1)]));
                }
            }
        }
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
}
