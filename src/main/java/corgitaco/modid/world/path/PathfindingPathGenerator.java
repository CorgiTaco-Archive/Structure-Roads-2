package corgitaco.modid.world.path;

import corgitaco.modid.util.DataForChunk;
import corgitaco.modid.world.WorldPathGenerator;
import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterable;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.SimplexNoiseGenerator;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.BiomeDictionary;

import static net.minecraftforge.common.BiomeDictionary.Type;

import java.util.*;

public class PathfindingPathGenerator implements IPathGenerator {
    private final Long2ObjectArrayMap<List<BlockPos>> nodesByChunk = new Long2ObjectArrayMap<>();
    private final Long2ObjectArrayMap<List<BlockPos>> lightsByChunk = new Long2ObjectArrayMap<>();
    private final BiomeProvider biomeSource;
    private final ChunkGenerator chunkGenerator;
    private final BlockPos startPos;
    private final BlockPos endPos;
    private final SimplexNoiseGenerator simplex;
    private final Registry<Biome> biomeRegistry;
    private final int startY, endY;

    private int nSamples = 0;
    private static final boolean ADDITIONAL_DEBUG_DETAILS = false;

    public PathfindingPathGenerator(ServerWorld world, BlockPos startPos, BlockPos endPos, Random random, BiomeProvider biomeSource, Long2ObjectArrayMap<Long2ObjectArrayMap<DataForChunk>> dataForRegion) {
        this.biomeSource = biomeSource;
        this.chunkGenerator = world.getChunkSource().generator;
        this.startY = getHeight(startPos.getX(), startPos.getZ(), world.getChunkSource().getGenerator());
        this.endY = getHeight(endPos.getX(), endPos.getZ(), world.getChunkSource().getGenerator());
        this.startPos = startPos;
        this.endPos = endPos;

        this.simplex = new SimplexNoiseGenerator(new Random(world.getSeed()));

        biomeRegistry = world.registryAccess().registry(Registry.BIOME_REGISTRY).orElse(null);

        generatePath(dataForRegion);
    }

    private void generatePath(Long2ObjectArrayMap<Long2ObjectArrayMap<DataForChunk>> dataForRegion){
        if (ADDITIONAL_DEBUG_DETAILS) {
            System.out.println(String.format("Started Pathfinder Gen for: %s - %s", startPos, endPos));
        }
        long startTime = System.currentTimeMillis();
        long startChunk = getChunkLongFromBlockPos(startPos);
        long endChunk = getChunkLongFromBlockPos(endPos);

        if (startChunk == endChunk) {
            if (ADDITIONAL_DEBUG_DETAILS) {
                System.out.println("Rejected path (Were in same chunk)");
            }
            return;
        }

        Tile pathInfo = findBestPath(startChunk, endChunk, dataForRegion);

        if (pathInfo != null) {
            Tile currentTile = pathInfo;
            List<BlockPos> basePos = new ArrayList<>();
            while (currentTile != null) {
                basePos.add(new BlockPos(ChunkPos.getX(currentTile.pos) * 16 + 8, 0, ChunkPos.getZ(currentTile.pos) * 16 + 8));
                currentTile = currentTile.bestPrev;
            }

            List<BlockPos> positions = averagePoints(basePos);
            for(int i = 0; i < positions.size() - 1; i++){
                BlockPos start = positions.get(i);
                BlockPos end = positions.get(i + 1);

                for(float t = 0.0f; t < 1; t += 0.2f){
                    addBlockPos(PathGenerator.getLerpedBlockPos(start, end, t));
                }
            }
            addBlockPos(positions.get(basePos.size() - 1));
        } else {
            if (ADDITIONAL_DEBUG_DETAILS) {
                System.out.println("No path found!");
            }
        }


        System.out.println(String.format("Pathfinder Gen for: %s - %s took %sms. Sampled %s times.", startPos, endPos, System.currentTimeMillis() - startTime, nSamples));
    }

    //Returns a tile that represents the endChunk
    //Tracing through the .bestPrev of every tile yields the path
    private Tile findBestPath(long startChunk, long endChunk, Long2ObjectArrayMap<Long2ObjectArrayMap<DataForChunk>> dataForRegion){
        Long2ObjectArrayMap<Tile> tiles = new Long2ObjectArrayMap<>();

        PriorityQueue<Tile> tilesToCheck = new PriorityQueue<>(Tile::compareTo);
        Tile tileAtStartPos = new Tile(1, startChunk);
        tileAtStartPos.activate(endChunk, 0);

        tiles.put(startChunk, tileAtStartPos);
        tilesToCheck.add(tileAtStartPos);

        boolean found = false;
        while (!tilesToCheck.isEmpty()) {
            if (nSamples > 10000) break; //To not get stuck if goal is completely inaccesible
            Tile currentTile = tilesToCheck.poll();

            if (currentTile.pos == endChunk) {
                found = true;
                break;
            }

            for (long pos : surroundingChunks(currentTile.pos)) {
                Tile tile = getTileAt(pos, tiles, dataForRegion);
                int newDist = currentTile.distFromStart + currentTile.weight;


                if (newDist < tile.distFromStart) {
                    if (!tile.active) {
                        tile.activate(endChunk, newDist);
                    }

                    tile.distFromStart = newDist;
                    tile.bestPrev = currentTile;

                    tilesToCheck.add(tile);
                }
            }
        }

        if(found) return tiles.get(endChunk);
        else return null;
    }

    private List<BlockPos> averagePoints(List<BlockPos> pos){
        List<BlockPos> averagePoints = new ArrayList<>(pos.size());
        averagePoints.add(pos.get(0));
        for(int i = 1; i < pos.size() - 1; i++){
            int x = pos.get(i - 1).getX() + 2 * pos.get(i).getX() + pos.get(i + 1).getX();
            int z = pos.get(i - 1).getZ() + 2 * pos.get(i).getZ() + pos.get(i + 1).getZ();

            int xShift = (int) (simplex.getValue(x * 100, z * 100) * 5);
            int zShift = (int) (simplex.getValue(x * 100, z * 100 + 173647) * 5);

            averagePoints.add(new BlockPos(x / 4 + xShift, 0, z / 4 + zShift));
        }
        averagePoints.add(pos.get(pos.size() - 1));

        return averagePoints;
    }

    private void addLightPos(BlockPos pos) {
        long chunkPos = getChunkLongFromBlockPos(pos);
        List<BlockPos> nodeList = lightsByChunk.get(chunkPos);
        if (nodeList == null) {
            nodeList = new ArrayList<>();
            lightsByChunk.put(chunkPos, nodeList);
        }

        nodeList.add(pos);
    }

    private void addBlockPos(BlockPos pos) {
        long chunkPos = getChunkLongFromBlockPos(pos);
        List<BlockPos> nodeList = nodesByChunk.get(chunkPos);
        if (nodeList == null) {
            nodeList = new ArrayList<>();
            nodesByChunk.put(chunkPos, nodeList);
        }

        nodeList.add(pos);
    }

    private LongIterable surroundingChunks(long pos) {
        LongArrayList points = new LongArrayList();
        int x = ChunkPos.getX(pos);
        int z = ChunkPos.getZ(pos);

        points.add(ChunkPos.asLong(x + 1, z));
        points.add(ChunkPos.asLong(x - 1, z));
        points.add(ChunkPos.asLong(x, z + 1));
        points.add(ChunkPos.asLong(x, z - 1));

        return points;
    }

    private Tile getTileAt(long pos, Long2ObjectArrayMap<Tile> tiles, Long2ObjectArrayMap<Long2ObjectArrayMap<DataForChunk>> dataForRegion) {
        Tile tile = tiles.get(pos);
        if (tile == null) {
            Tile newTile = new Tile(getWeight(dataForRegion, pos), pos);
            tiles.put(pos, newTile);
            return newTile;
        }
        return tile;
    }

    private int getWeight(Long2ObjectArrayMap<Long2ObjectArrayMap<DataForChunk>> dataForRegion, long pos) {
        nSamples++;
        int chunkX = ChunkPos.getX(pos);
        int chunkZ = ChunkPos.getZ(pos);

        Long2ObjectArrayMap<DataForChunk> regionData = dataForRegion.computeIfAbsent(WorldPathGenerator.chunkToRegionKey(pos), (key) -> {
            return new Long2ObjectArrayMap<>();
        });


        DataForChunk chunkData = regionData.computeIfAbsent(pos, (key) -> {
            return new DataForChunk(this.biomeSource.getNoiseBiome((chunkX << 2) + 2, 60, (chunkZ << 2) + 2));
        });

        Biome biome = chunkData.getBiome();
        RegistryKey<Biome> biomeKey = null;
        if(biomeRegistry != null) {
            biomeKey = biomeRegistry.getResourceKey(biome).orElse(null);
        }






        Set<Type> biomeTypes = BiomeDictionary.getTypes(biomeKey);
        if (containsAny(biomeTypes, Type.MOUNTAIN, Type.HILLS, Type.OCEAN, Type.PLATEAU) || testHeight(startY, endY, chunkData.getHeight(chunkGenerator, chunkX * 16 + 8, chunkZ * 16 + 8), chunkGenerator.getSeaLevel())) {
            return 10000;
        }else if(containsAny(biomeTypes, Type.RIVER, Type.SPOOKY)){
            return 3; //Will make river crossing try to be shorter
        }

        return 1;
    }

    private static <T> boolean containsAny(Collection<T> collection, T... items){
        for(T item : items){
            if(collection.contains(item)){
                return true;
            }
        }
        return false;
    }

    private static int getHeight(int x, int z, ChunkGenerator generator){
//        if(generator instanceof NoiseChunkGenerator){
//            double[] column = ((NoiseChunkGenerator) generator).makeAndFillNoiseColumn(x, z);
//            int baseHeight = column.length - 1;
//            for(; baseHeight >= 0; baseHeight--){
//                if(column[baseHeight] > 0.0) break;
//            }
//            return baseHeight;
//        }else{
//            System.out.println("Used getBaseHeight");
            return generator.getBaseHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG);
//        }
    }

    public static boolean testHeight(int startY, int endY, int x, int z, ChunkGenerator generator) {
        int baseHeight = getHeight(x, z, generator);
        return baseHeight <= startY + 10 && baseHeight >= generator.getSeaLevel() && baseHeight >= endY;
    }

    public static boolean testHeight(int startY, int endY, int height, int seaLevel) {
        return height <= startY + 10 && height >= seaLevel && height >= endY;
    }


    @Override
    public Long2ObjectArrayMap<List<BlockPos>> getNodesByChunk() {
        return nodesByChunk;
    }

    @Override
    public Long2ObjectArrayMap<List<BlockPos>> getLightsByChunk() {
        return lightsByChunk;
    }

    @Override
    public BlockState debugState() {
        return Blocks.RED_CONCRETE.defaultBlockState();
    }

    public static class Tile implements Comparable {
        private Tile bestPrev = null;
        private int distFromStart = Integer.MAX_VALUE;
        private float distFromGoal = Float.MAX_VALUE;
        private boolean active = false;
        private final int weight;
        private final long pos;

        public Tile(int weight, long pos) {
            this.weight = weight;
            this.pos = pos;
        }

        public float getFCost() {
            return distFromGoal + distFromStart;
        }

        @Override
        public int compareTo(Object o) {
            Tile tile = (Tile) o;
            int diff = Float.compare(getFCost(), tile.getFCost());
            if (diff == 0) {
                return Float.compare(distFromGoal, tile.distFromGoal);
            }
            return diff;
        }

        public void activate(long goal, int distFromStart) {
            active = true;
            int x = ChunkPos.getX(pos);
            int z = ChunkPos.getZ(pos);

            int dx = x - ChunkPos.getX(goal);
            int dz = z - ChunkPos.getZ(goal);
            distFromGoal = (float) Math.sqrt(dx * dx + dz * dz);
            this.distFromStart = distFromStart;
        }
    }

    public static long getChunkLongFromBlockPos(BlockPos pos) {
        return ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }
}
