package corgitaco.modid.world;

import com.mojang.serialization.Codec;
import corgitaco.modid.mixin.access.StructureAccess;
import corgitaco.modid.structure.AdditionalStructureContext;
import corgitaco.modid.util.DataForChunk;
import corgitaco.modid.world.path.IPathGenerator;
import corgitaco.modid.world.path.PathContext;
import corgitaco.modid.world.path.PathfindingPathGenerator;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.blockstateprovider.WeightedBlockStateProvider;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.feature.template.PlacementSettings;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.gen.feature.template.TemplateManager;
import net.minecraft.world.gen.settings.StructureSeparationSettings;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WorldPathGenerator extends Feature<NoFeatureConfig> {

    public static final boolean DEBUG = false;

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

    @Override
    public boolean place(ISeedReader world, ChunkGenerator generator, Random random, BlockPos pos, NoFeatureConfig config) {
        long seed = world.getSeed();

        int minConnectionCount = 1;
        int maxConnectionCount = 2;
        WeightedBlockStateProvider stateProvider = new WeightedBlockStateProvider().add(Blocks.GRASS_PATH.defaultBlockState(), 5).add(Blocks.COARSE_DIRT.defaultBlockState(), 2);

        int x = pos.getX();
        int chunkX = SectionPos.blockToSectionCoord(x);
        int z = pos.getZ();
        int chunkZ = SectionPos.blockToSectionCoord(z);
        long currentChunk = ChunkPos.asLong(chunkX, chunkZ);

        int currentRegionX = chunkToRegion(chunkX);
        int currentRegionZ = chunkToRegion(chunkZ);
        long currentRegionKey = chunkToRegionKey(currentChunk);

        Structure<?> structure = Structure.VILLAGE;
        StructureSeparationSettings structureSeparationSettings = generator.getSettings().structureConfig().get(structure);

        Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> structuresByRegion = ((PathContext.Access) world.getLevel()).getPathContext().getContextCacheForLevel();
        LongSet completedStructureCacheRegions = ((PathContext.Access) world.getLevel()).getPathContext().getCompletedRegionStructureCachesForLevel();
        LongSet completedLinkedRegions = ((PathContext.Access) world.getLevel()).getPathContext().getCompletedLinkedRegionsForLevel();
        Long2ObjectArrayMap<Long2ObjectArrayMap<AdditionalStructureContext>> surroundingRegionStructureCachesForRegion = ((PathContext.Access) world.getLevel()).getPathContext().getSurroundingRegionStructureCachesForRegionForLevel();
        Long2ObjectArrayMap<List<IPathGenerator>> pathGeneratorsForRegion = ((PathContext.Access) world.getLevel()).getPathContext().getPathGenerators();
        Long2ObjectArrayMap<Long2ObjectArrayMap<DataForChunk>> dataForLocation = ((PathContext.Access) world.getLevel()).getPathContext().getDataCache();

        ServerWorld actualWorld = world.getLevel();

        int range = 1;


        // In cases such as buried treasure where there are 20k+ buried treasures in a given 3x3 region area,
        // creating a new Long2ObjectArrayMap every chunk call & using a .putAll is very inefficient due to the number of entries.
        // So we've cut down performance cost by making this cache get created by region not every single chunk call.
//        Long2ObjectArrayMap<AdditionalStructureContext> surroundingRegionStructures = surroundingRegionStructureCachesForRegion.computeIfAbsent(currentRegionKey, (key) -> {
//            Long2ObjectArrayMap<AdditionalStructureContext> computedSurroundingRegionStructures = new Long2ObjectArrayMap<>();
            for (int regionX = currentRegionX - range; regionX <= currentRegionX + range; regionX++) {
                for (int regionZ = currentRegionZ - range; regionZ <= currentRegionZ + range; regionZ++) {
                    long regionKey = regionKey(regionX, regionZ);

                    if (!completedStructureCacheRegions.contains(regionKey)) {
                        collectRegionStructures(actualWorld, seed, generator.getBiomeSource(), structure, structureSeparationSettings, structuresByRegion, pathGeneratorsForRegion, dataForLocation, regionX, regionZ);
                        completedStructureCacheRegions.add(regionKey);
                    }
//                    computedSurroundingRegionStructures.putAll(structuresByRegion.get(regionKey));
                }
            }
//            return computedSurroundingRegionStructures;
//        });

        //System.out.println("Num Path Generators in a single context: " + pathGeneratorsForRegion.size());

        Long2ObjectArrayMap<AdditionalStructureContext> currentRegionStructures = structuresByRegion.get(currentRegionKey);

        if (DEBUG) {
            if (currentRegionStructures.containsKey(currentChunk)) {
                buildMarker(world, x, z, Blocks.EMERALD_BLOCK.defaultBlockState());
            }
        }

        List<IPathGenerator> pathGenerators = pathGeneratorsForRegion.get(currentRegionKey);

        for (IPathGenerator pathGenerator : pathGenerators) {
            Long2ObjectArrayMap<List<BlockPos>> chunkNodes = pathGenerator.getNodesByChunk();
//            if (pathGenerator.intersects(pos)) {
                if (chunkNodes.containsKey(currentChunk)) {
                    for (BlockPos blockPos : chunkNodes.get(currentChunk)) {
                        if (DEBUG) {
                            buildMarker(world, blockPos.getX(), blockPos.getZ(), pathGenerator.debugState());
                        }

                        generatePath(world, random, stateProvider, blockPos);
                    }
//                }
                generateLights(world, random, currentChunk, pathGenerator);
            }
        }

        return true;
    }

    private void generatePath(ISeedReader world, Random random, WeightedBlockStateProvider stateProvider, BlockPos blockPos) {
        int size = 3;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int xMove = -size; xMove < size; xMove++) {
            for (int zMove = -size; zMove < size; zMove++) {
                BlockPos.Mutable movedMutable = mutable.setWithOffset(blockPos, xMove, 0, zMove);
                movedMutable.setY(world.getHeight(Heightmap.Type.WORLD_SURFACE_WG, movedMutable.getX(), movedMutable.getZ()) - 1);
                world.setBlock(movedMutable, stateProvider.getState(random, movedMutable), 2);
            }
        }
    }

    private void generateLights(ISeedReader world, Random random, long currentChunk, IPathGenerator pathGenerator) {
        Long2ObjectArrayMap<List<BlockPos>> lightNodes = pathGenerator.getLightsByChunk();
        if (lightNodes.containsKey(currentChunk)) {
            for (BlockPos blockPos : lightNodes.get(currentChunk)) {
                BlockPos lightPos = blockPos.offset(2, world.getHeight(Heightmap.Type.WORLD_SURFACE_WG, blockPos.getX(), blockPos.getZ()), 2);
                TemplateManager templatemanager = world.getLevel().getStructureManager();
                Template template = templatemanager.get(new ResourceLocation("village/plains/plains_lamp_1"));

                if (template != null) {
                    PlacementSettings placementsettings = (new PlacementSettings()).setMirror(Mirror.NONE).setRotation(Rotation.NONE).setIgnoreEntities(false).setChunkPos(null);
                    template.placeInWorldChunk(world, lightPos, placementsettings, random);
                }
            }
        }
    }

    private void buildMarker(ISeedReader world, int x, int z, BlockState state) {
        BlockPos.Mutable mutable = new BlockPos.Mutable().set(x, world.getHeight(Heightmap.Type.WORLD_SURFACE_WG, x, z), z);
        for (int buildY = 0; buildY < 25; buildY++) {
            world.setBlock(mutable, state, 2);
            mutable.move(Direction.UP);
        }
    }

    private static void collectRegionStructures(ServerWorld world, long seed, BiomeProvider biomeSource, Structure<?> village, StructureSeparationSettings structureSeparationSettings, Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> regionPositions, Long2ObjectArrayMap<List<IPathGenerator>> pathGenerators, Long2ObjectArrayMap<Long2ObjectArrayMap<DataForChunk>> dataForLocation, int regionX, int regionZ) {
        long startTime = System.currentTimeMillis();

        int spacing = structureSeparationSettings.spacing();

        int activeMinChunkX = regionToChunk(regionX);
        int activeMinChunkZ = regionToChunk(regionZ);

        int activeMaxChunkX = regionToMaxChunk(regionX);
        int activeMaxChunkZ = regionToMaxChunk(regionZ);

        int activeMinGridX = Math.floorDiv(activeMinChunkX, spacing);
        int activeMinGridZ = Math.floorDiv(activeMinChunkZ, spacing);

        int activeMaxGridX = Math.floorDiv(activeMaxChunkX, spacing);
        int activeMaxGridZ = Math.floorDiv(activeMaxChunkZ, spacing);

        scanRegionStructureGridAndLinkNeighbors(world, seed, biomeSource, village, structureSeparationSettings, spacing, regionPositions, pathGenerators, dataForLocation, activeMinGridX, activeMinGridZ, activeMaxGridX, activeMaxGridZ);

        System.out.println("Generated links and paths for region (" + regionX + ", " + regionZ + ") in " + (System.currentTimeMillis() - startTime) + " ms");
    }


    /**
     * Creates the cache of all structure positions for the given region by iterating over the structure grid which should guarantee a structure position per iteration.
     */
    private static void scanRegionStructureGridAndLinkNeighbors(ServerWorld world, long seed, BiomeProvider biomeSource, Structure<?> structure, StructureSeparationSettings structureSeparationSettings, int spacing, Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> regionPositions, Long2ObjectArrayMap<List<IPathGenerator>> pathGenerators, Long2ObjectArrayMap<Long2ObjectArrayMap<DataForChunk>> dataForLocation, int activeMinGridX, int activeMinGridZ, int activeMaxGridX, int activeMaxGridZ) {
        Random random = new Random(seed);
        int neighborRange = 1;

        for (int structureGridX = activeMinGridX; structureGridX <= activeMaxGridX; structureGridX++) {
            for (int structureGridZ = activeMinGridZ; structureGridZ <= activeMaxGridZ; structureGridZ++) {
                long structurePosFromGrid = getStructurePosFromGrid(structureGridX, structureGridZ, seed, biomeSource, structure, structureSeparationSettings);

                if (structurePosFromGrid == Long.MIN_VALUE) {
                    continue;
                }

                long structureRegionKey = chunkToRegionKey(structurePosFromGrid);

                AdditionalStructureContext additionalStructureContext = new AdditionalStructureContext(NAMES[random.nextInt(NAMES.length - 1)]);
                Long2ObjectArrayMap<AdditionalStructureContext> structureData = regionPositions.computeIfAbsent(structureRegionKey, (value) -> new Long2ObjectArrayMap<>());
                structureData.put(structurePosFromGrid, additionalStructureContext);


                linkNeighbors(world, seed, biomeSource, structure, structureSeparationSettings, regionPositions, pathGenerators, dataForLocation, NAMES[random.nextInt(NAMES.length - 1)], neighborRange, structureGridX, structureGridZ, structurePosFromGrid, additionalStructureContext);
            }
        }
    }

    private static void linkNeighbors(ServerWorld world, long seed, BiomeProvider biomeSource, Structure<?> structure, StructureSeparationSettings structureSeparationSettings, Long2ReferenceOpenHashMap<Long2ObjectArrayMap<AdditionalStructureContext>> regionPositions, Long2ObjectArrayMap<List<IPathGenerator>> pathGenerators, Long2ObjectArrayMap<Long2ObjectArrayMap<DataForChunk>> dataForLocation, String name, int neighborRange, int structureGridX, int structureGridZ, long structurePosFromGrid, AdditionalStructureContext additionalStructureContext) {
        for (int neighborStructureGridX = -neighborRange; neighborStructureGridX < neighborRange; neighborStructureGridX++) {
            for (int neighborStructureGridZ = -neighborRange; neighborStructureGridZ < neighborRange; neighborStructureGridZ++) {
                long neighborStructurePosFromGrid = getStructurePosFromGrid(structureGridX + neighborStructureGridX, structureGridZ + neighborStructureGridZ, seed, biomeSource, structure, structureSeparationSettings);


                if (neighborStructurePosFromGrid == Long.MIN_VALUE) {
                    continue;
                }

                long neighborStructureRegionKey = chunkToRegionKey(neighborStructurePosFromGrid);

                AdditionalStructureContext neighborAdditionalStructureContext = new AdditionalStructureContext(name);
                Long2ObjectArrayMap<AdditionalStructureContext> neighborStructureData = regionPositions.computeIfAbsent(neighborStructureRegionKey, (value) -> new Long2ObjectArrayMap<>());

                neighborStructureData.put(neighborStructurePosFromGrid, additionalStructureContext);

                additionalStructureContext.getConnections().add(neighborStructurePosFromGrid);
                neighborAdditionalStructureContext.getConnections().add(neighborStructurePosFromGrid);


                SharedSeedRandom structureRandom = new SharedSeedRandom();
                structureRandom.setLargeFeatureWithSalt(seed, ChunkPos.getX(structurePosFromGrid) + ChunkPos.getX(neighborStructurePosFromGrid), ChunkPos.getZ(structurePosFromGrid) + ChunkPos.getZ(neighborStructurePosFromGrid), structureSeparationSettings.salt());

                IPathGenerator pathGenerator = new PathfindingPathGenerator(world, getPosFromChunk(structurePosFromGrid), getPosFromChunk(neighborStructurePosFromGrid), structureRandom, biomeSource, dataForLocation);
                pathGenerators.computeIfAbsent(chunkToRegionKey(structurePosFromGrid), (key) -> new ArrayList<>()).add(pathGenerator);
                pathGenerators.computeIfAbsent(chunkToRegionKey(neighborStructurePosFromGrid), (key) -> new ArrayList<>()).add(pathGenerator);
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
}
