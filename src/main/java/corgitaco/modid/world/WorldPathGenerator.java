package corgitaco.modid.world;

import com.mojang.serialization.Codec;
import corgitaco.modid.Main;
import corgitaco.modid.core.StructureData;
import corgitaco.modid.core.StructureRegionManager;
import corgitaco.modid.mixin.access.UtilAccess;
import corgitaco.modid.structure.AdditionalStructureContext;
import corgitaco.modid.util.DataForChunk;
import corgitaco.modid.world.path.IPathGenerator;
import corgitaco.modid.world.path.PathfindingPathGenerator;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.Direction;
import net.minecraft.util.Mirror;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.ISeedReader;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.blockstateprovider.WeightedBlockStateProvider;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.NoFeatureConfig;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.feature.template.PlacementSettings;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.gen.feature.template.TemplateManager;
import net.minecraft.world.server.ServerWorld;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static corgitaco.modid.core.StructureRegionManager.*;


public class WorldPathGenerator extends Feature<NoFeatureConfig> {

    public static final boolean DEBUG = false;
    private static final boolean MULTI_THREADED_NOISE_CACHE = false;
    private static final boolean USE_NOISE_CACHE = false;
    public static final Executor EXECUTOR = UtilAccess.invokeMakeExecutor("paths");
    private static int totalTimeSpent = 0;

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
        long currentRegionKey = chunkToRegionKey(currentChunk);

        Structure<?> structure = Structure.VILLAGE;
        ServerWorld level = world.getLevel();
        StructureRegionManager structureRegionManager = ((StructureRegionManager.Access) level).getStructureRegionManager();
        StructureData currentRegionStructureData = structureRegionManager.getStructureRegion(currentRegionKey).structureData(structure);

        Long2ReferenceOpenHashMap<AdditionalStructureContext> currentRegionStructures = currentRegionStructureData.getLocationContextData(true);

        if (DEBUG) {
            if (currentRegionStructures.containsKey(currentChunk)) {
                buildMarker(world, x, z, Blocks.EMERALD_BLOCK.defaultBlockState());
            }
        }

        generatePaths(world, random, pos, stateProvider, currentChunk, currentRegionKey, currentRegionStructureData.getPathGenerators(true).values());
        generatePaths(world, random, pos, stateProvider, currentChunk, currentRegionKey, currentRegionStructureData.getPathGeneratorNeighbors().values());

        return true;
    }

    private void generatePaths(ISeedReader world, Random random, BlockPos pos, WeightedBlockStateProvider stateProvider, long currentChunk, long currentRegionKey, Collection<PathfindingPathGenerator> values) {
        for (IPathGenerator<Structure<?>> pathGenerator : values) {
            if (pathGenerator.getBoundingBox().intersects(pos.getX(), pos.getZ(), pos.getX(), pos.getZ())) {
                Long2ReferenceOpenHashMap<Set<BlockPos>> chunkNodes = pathGenerator.getNodesByRegion().get(currentRegionKey);
                if (chunkNodes.containsKey(currentChunk)) {
                    for (BlockPos blockPos : chunkNodes.get(currentChunk)) {
                        if (DEBUG) {
                            buildMarker(world, blockPos.getX(), blockPos.getZ(), pathGenerator.debugState());
                        }

                        generatePath(world, random, stateProvider, blockPos);
                    }
                    generateLights(world, random, currentChunk, pathGenerator);
                }
            }
        }
    }

    private void computeNoiseCache(ChunkGenerator generator, int currentRegionX, int currentRegionZ, Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>> dataForLocation, int range) {
        int noiseCacheRange = range + 1;
        long beforeCacheCompute = System.currentTimeMillis();

        if (MULTI_THREADED_NOISE_CACHE) {
            List<CompletableFuture<Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>>>> yes = new ArrayList<>();

            for (int regionX = currentRegionX - noiseCacheRange; regionX <= currentRegionX + noiseCacheRange; regionX++) {
                for (int regionZ = currentRegionZ - noiseCacheRange; regionZ <= currentRegionZ + noiseCacheRange; regionZ++) {
                    long regionKey = regionKey(regionX, regionZ);
                    List<CompletableFuture<Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>>>> futures = cacheFuture(generator, regionX, regionZ, regionKey, dataForLocation, 4);

                    if (futures == null) {
                        continue;
                    }
                    yes.addAll(futures);

                }
            }
            CompletableFuture.allOf(yes.toArray(new CompletableFuture[0])).join();
            Main.LOGGER.info("Multithreaded cache compute took: " + (System.currentTimeMillis() - beforeCacheCompute) + "ms");

        } else {
            for (int regionX = currentRegionX - noiseCacheRange; regionX <= currentRegionX + noiseCacheRange; regionX++) {
                for (int regionZ = currentRegionZ - noiseCacheRange; regionZ <= currentRegionZ + noiseCacheRange; regionZ++) {
                    long regionKey = regionKey(regionX, regionZ);
                    int activeMinChunkX = regionToChunk(currentRegionX);
                    int activeMinChunkZ = regionToChunk(currentRegionZ);

                    int activeMaxChunkX = regionToMaxChunk(currentRegionX);
                    int activeMaxChunkZ = regionToMaxChunk(currentRegionZ);

                    int activeXSize = activeMaxChunkX - activeMinChunkX;
                    int activeZSize = activeMaxChunkZ - activeMinChunkZ;


                    for (int xChunk = activeMinChunkX; xChunk < activeMaxChunkX; xChunk++) {
                        for (int zChunk = activeMinChunkZ; zChunk < activeMaxChunkZ; zChunk++) {
                            int height = generator.getBaseHeight(SectionPos.sectionToBlockCoord(xChunk) + 8, SectionPos.sectionToBlockCoord(zChunk) + 8, Heightmap.Type.OCEAN_FLOOR_WG);
                            if (!dataForLocation.containsKey(regionKey)) {
                                dataForLocation.computeIfAbsent(regionKey, (key) -> new Long2ReferenceOpenHashMap<>()).put(ChunkPos.asLong(xChunk, zChunk), new DataForChunk(generator.getBiomeSource().getNoiseBiome((xChunk << 2) + 2, 60, (zChunk << 2) + 2), height));
                            }
                        }
                    }

                }
            }
            Main.LOGGER.info("Singlethreaded cache compute took: " + (System.currentTimeMillis() - beforeCacheCompute) + "ms");
        }
    }

    @Nullable
    private List<CompletableFuture<Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>>>> cacheFuture(ChunkGenerator generator, int currentRegionX, int currentRegionZ, long currentRegionKey, Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>> dataForLocation, int sections) {
        if (dataForLocation.containsKey(currentRegionKey)) {
            return null;
        }

        int activeMinChunkX = regionToChunk(currentRegionX);
        int activeMinChunkZ = regionToChunk(currentRegionZ);

        int activeMaxChunkX = regionToMaxChunk(currentRegionX);
        int activeMaxChunkZ = regionToMaxChunk(currentRegionZ);

        int activeXSize = activeMaxChunkX - activeMinChunkX;
        int activeZSize = activeMaxChunkZ - activeMinChunkZ;


        List<CompletableFuture<Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>>>> yes = new ArrayList<>();

        double regionXSectionSize = (double) activeXSize / sections;
        double regionZSectionSize = (double) activeZSize / sections;

        for (int xThread = 0; xThread < sections / 2; xThread++) {
            for (int zThread = 0; zThread < sections / 2; zThread++) {
                int minX = (int) ((regionXSectionSize * xThread) + activeMinChunkX);
                int minZ = (int) ((regionZSectionSize * zThread) + activeMinChunkZ);

                CompletableFuture<Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>>> future = CompletableFuture.supplyAsync(() -> {
                    long time = System.currentTimeMillis();

                    Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>> dataForChunk = new Long2ReferenceOpenHashMap<>();

                    for (int xChunk = minX; xChunk < minX + regionXSectionSize; xChunk++) {
                        for (int zChunk = minZ; zChunk < minZ + regionZSectionSize; zChunk++) {
                            int height = generator.getBaseHeight(SectionPos.sectionToBlockCoord(xChunk) + 8, SectionPos.sectionToBlockCoord(zChunk) + 8, Heightmap.Type.OCEAN_FLOOR_WG);
                            dataForChunk.computeIfAbsent(currentRegionKey, (key) -> new Long2ReferenceOpenHashMap<>()).put(ChunkPos.asLong(xChunk, zChunk), new DataForChunk(generator.getBiomeSource().getNoiseBiome((xChunk << 2) + 2, 60, (zChunk << 2) + 2), height));
                        }
                    }
                    System.out.println(Thread.currentThread().getName() + " finished in " + (System.currentTimeMillis() - time) + "ms");

                    return dataForChunk;
                }, EXECUTOR).whenComplete(((newMap, throwable) -> {
                    dataForLocation.putAll(newMap);
                }));

                yes.add(future);
            }
        }
        return yes;
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

    private void generateLights(ISeedReader world, Random random, long currentChunk, IPathGenerator<Structure<?>> pathGenerator) {
        Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<Set<BlockPos>>> lightNodes = pathGenerator.getLightsByRegion();
        long regionKey = chunkToRegionKey(currentChunk);
        if (lightNodes.containsKey(regionKey)) {
            for (BlockPos blockPos : lightNodes.get(regionKey).getOrDefault(currentChunk, new HashSet<>())) {
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
}
