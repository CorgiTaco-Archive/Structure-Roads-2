package corgitaco.modid.world;

import com.mojang.serialization.Codec;
import corgitaco.modid.mixin.access.StructureAccess;
import corgitaco.modid.structure.AdditionalStructureContext;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.util.SharedSeedRandom;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraft.world.server.ServerWorld;

import java.util.Map;
import java.util.Random;
import java.util.Set;

public class WorldPathGenerator extends Feature<NoFeatureConfig> {

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

    private final Map<ServerWorld, Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>>> contextCache = new Object2ObjectArrayMap<>();

    @Override
    public boolean place(ISeedReader world, ChunkGenerator generator, Random random, BlockPos pos, NoFeatureConfig config) {
        long seed = world.getSeed();

        int minConnectionCount = 2;
        int maxConnectionCount = 5;

        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        long currentChunk = ChunkPos.asLong(chunkX, chunkZ);

        int currentRegionX = chunkToRegion(chunkX);
        int currentRegionZ = chunkToRegion(chunkZ);
        long currentRegion = regionLong(chunkToRegion(chunkX), chunkToRegion(chunkZ));

        Structure<?> structure = Structure.VILLAGE;
        StructureSeparationSettings structureSeparationSettings = generator.getSettings().structureConfig().get(structure);

        Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> structuresByRegion = contextCache.computeIfAbsent(world.getLevel(), (serverWorld -> new Long2ReferenceOpenHashMap<>()));

        int range = 1;

        Long2ObjectArrayMap<AdditionalStructureContext> surroundingRegionStructures = new Long2ObjectArrayMap<>();

        for (int regionX = currentRegionX - range; regionX <= currentRegionX + range; regionX++) {
            for (int regionZ = currentRegionZ - range; regionZ <= currentRegionZ + range; regionZ++) {
                long region = regionLong(regionX, regionZ);

                collectRegionStructures(seed, generator.getBiomeSource(), structure, structureSeparationSettings, structuresByRegion, regionX, regionZ);

                surroundingRegionStructures.putAll(structuresByRegion.get(region));
            }
        }

        long[] currentAndSurroundingRegionStructurePositions = surroundingRegionStructures.keySet().toLongArray();

        Long2ObjectArrayMap<AdditionalStructureContext> currentRegionStructures = structuresByRegion.get(currentRegion);
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

        return true;
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
    private static void scanRegionStructureGrid(long seed, BiomeProvider biomeSource, Structure<?> village, StructureSeparationSettings structureSeparationSettings, int spacing, Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> regionPositions, int activeMinGridX, int activeMinGridZ, int activeMaxGridX, int activeMaxGridZ) {
        Random random = new Random(seed);

        for (int structureGridX = activeMinGridX; structureGridX <= activeMaxGridX; structureGridX++) {
            for (int structureGridZ = activeMinGridZ; structureGridZ <= activeMaxGridZ; structureGridZ++) {
                long structureChunkPos = getStructureChunkPos(village, seed, structureSeparationSettings.salt(), new SharedSeedRandom(), spacing, structureSeparationSettings.separation(), structureGridX, structureGridZ);

                int structureChunkPosX = ChunkPos.getX(structureChunkPos);
                int structureChunkPosZ = ChunkPos.getZ(structureChunkPos);
                long structureRegionLong = regionLong(chunkToRegion(structureChunkPosX), chunkToRegion(structureChunkPosZ));

                Long2ObjectArrayMap<AdditionalStructureContext> structureData = regionPositions.computeIfAbsent(structureRegionLong, (value) -> new Long2ObjectArrayMap<>());

                // Verify this biome is a valid biome for this structure
                if (!sampleAndTestChunkBiomesForStructure(structureChunkPosX, structureChunkPosZ, biomeSource, village)) {
                    continue;
                }


                // We need to verify that we've actually got a valid structure start.
                long chunkPos = village.getPotentialFeatureChunk(structureSeparationSettings, seed, new SharedSeedRandom(), structureChunkPosX, structureChunkPosZ).toLong();

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

    public static long regionLong(int regionX, int regionZ) {
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
