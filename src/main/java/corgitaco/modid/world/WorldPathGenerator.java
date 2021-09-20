package corgitaco.modid.world;

import com.mojang.serialization.Codec;
import corgitaco.modid.core.StructureData;
import corgitaco.modid.core.StructureRegionManager;
import corgitaco.modid.structure.AdditionalStructureContext;
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
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.blockstateprovider.BlockStateProvider;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.feature.template.JigsawReplacementStructureProcessor;
import net.minecraft.world.gen.feature.template.PlacementSettings;
import net.minecraft.world.gen.feature.template.Template;
import net.minecraft.world.gen.feature.template.TemplateManager;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

import static corgitaco.modid.core.StructureRegionManager.chunkToRegionKey;


public class WorldPathGenerator extends Feature<PathConfig> {

    public static final boolean DEBUG = false;

    public WorldPathGenerator(Codec<PathConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(ISeedReader world, ChunkGenerator generator, Random random, BlockPos pos, PathConfig config) {
        int x = pos.getX();
        int chunkX = SectionPos.blockToSectionCoord(x);
        int z = pos.getZ();
        int chunkZ = SectionPos.blockToSectionCoord(z);
        long currentChunk = ChunkPos.asLong(chunkX, chunkZ);
        long currentRegionKey = chunkToRegionKey(currentChunk);

        Structure<?> structure = config.getStructure();
        ServerWorld level = world.getLevel();
        StructureRegionManager structureRegionManager = ((StructureRegionManager.Access) level).getStructureRegionManager();
        StructureData currentRegionStructureData = structureRegionManager.getStructureRegion(currentRegionKey).structureData(structure);

        Long2ReferenceOpenHashMap<AdditionalStructureContext> currentRegionStructures = currentRegionStructureData.getLocationContextData(true);

        if (DEBUG) {
            if (currentRegionStructures.containsKey(currentChunk)) {
                buildMarker(world, x, z, Blocks.EMERALD_BLOCK.defaultBlockState());
            }
        }

        generatePaths(world, random, config.getBiomePathBlocks(), config.getDefaultStateProvider(), currentChunk, currentRegionKey, currentRegionStructureData.getPathGenerators(true).values(), config.getPathSize());
        generatePaths(world, random, config.getBiomePathBlocks(), config.getDefaultStateProvider(), currentChunk, currentRegionKey, currentRegionStructureData.getPathGeneratorNeighbors().values(), config.getPathSize());

        return true;
    }

    private void generatePaths(ISeedReader world, Random random, Map<Biome.Category, BlockStateProvider> biomeStateProviders, BlockStateProvider defaultStateProvider, long currentChunk, long currentRegionKey, Collection<PathfindingPathGenerator> values, int pathSize) {
        for (IPathGenerator<Structure<?>> pathGenerator : values) {
//            if (pathGenerator.getBoundingBox().intersects(pos.getX(), pos.getZ(), pos.getX(), pos.getZ())) {
                Long2ReferenceOpenHashMap<Set<BlockPos>> chunkNodes = pathGenerator.getNodesByRegion().get(currentRegionKey);
                if (chunkNodes.containsKey(currentChunk)) {
                    for (BlockPos blockPos : chunkNodes.get(currentChunk)) {
                        if (DEBUG) {
                            buildMarker(world, blockPos.getX(), blockPos.getZ(), pathGenerator.debugState());
                        }

                        generatePath(world, random, biomeStateProviders, defaultStateProvider, blockPos, pathSize);
                    }
                    generateLights(world, random, currentChunk, pathGenerator);
//                }
            }
        }
    }

    private void generatePath(ISeedReader world, Random random, Map<Biome.Category, BlockStateProvider> biomeStateProviders, BlockStateProvider defaultStateProvider, BlockPos blockPos, int pathSize) {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        //MutableRegistry<Biome> biomeRegistryKey = world.registryAccess().registry(Registry.BIOME_REGISTRY).get();
        for (int xMove = -pathSize; xMove < pathSize; xMove++) {
            for (int zMove = -pathSize; zMove < pathSize; zMove++) {
                BlockPos.Mutable movedMutable = mutable.setWithOffset(blockPos, xMove, 0, zMove);
                movedMutable.setY(world.getHeight(Heightmap.Type.WORLD_SURFACE_WG, movedMutable.getX(), movedMutable.getZ()) - 1);
                BlockStateProvider stateProvider = biomeStateProviders.getOrDefault(world.getBiome(mutable).getBiomeCategory(), defaultStateProvider);
                world.setBlock(movedMutable, stateProvider.getState(random, movedMutable), 2);
            }
        }
    }

    private void generateLights(ISeedReader world, Random random, long currentChunk, IPathGenerator<Structure<?>> pathGenerator) {
        Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<Set<BlockPos>>> lightNodes = pathGenerator.getNodesByRegion();
        long regionKey = chunkToRegionKey(currentChunk);
        if (lightNodes.containsKey(regionKey)) {
            for (BlockPos blockPos : lightNodes.get(regionKey).getOrDefault(currentChunk, new HashSet<>())) {
                BlockPos lightPos = blockPos.offset(2, world.getHeight(Heightmap.Type.WORLD_SURFACE_WG, blockPos.getX() + 2, blockPos.getZ() + 2), 2);
                TemplateManager templatemanager = world.getLevel().getStructureManager();
                Template template = templatemanager.get(new ResourceLocation("village/plains/plains_lamp_1"));

                if (template != null) {
                    PlacementSettings placementsettings = (new PlacementSettings()).setMirror(Mirror.NONE).setRotation(Rotation.NONE).addProcessor(JigsawReplacementStructureProcessor.INSTANCE).setIgnoreEntities(false).setChunkPos(null);
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
