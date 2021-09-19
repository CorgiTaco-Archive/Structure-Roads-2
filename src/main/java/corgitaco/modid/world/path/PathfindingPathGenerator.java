package corgitaco.modid.world.path;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import corgitaco.modid.util.CodecUtil;
import corgitaco.modid.util.DataForChunk;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterable;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.gen.NoiseChunkGenerator;
import net.minecraft.world.gen.SimplexNoiseGenerator;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.BiomeDictionary;

import java.util.*;

import static corgitaco.modid.core.StructureRegionManager.chunkToRegionKey;
import static net.minecraftforge.common.BiomeDictionary.Type;

public class PathfindingPathGenerator implements IPathGenerator<Structure<?>> {

    public static final Codec<PathfindingPathGenerator> CODEC = RecordCodecBuilder.create(builder -> {
        return builder.group(Point.POINT_STRUCTURE_CODEC.fieldOf("start").forGetter(pathfindingPathGenerator -> {
            return pathfindingPathGenerator.startPoint;
        }), Point.POINT_STRUCTURE_CODEC.fieldOf("end").forGetter(pathfindingPathGenerator -> {
            return pathfindingPathGenerator.endPoint;
        }), Codec.unboundedMap(Codec.LONG, Codec.unboundedMap(Codec.LONG, BlockPos.CODEC.listOf())).fieldOf("nodesByRegion").forGetter(pathfindingPathGenerator -> {
            return new HashMap<>(pathfindingPathGenerator.nodesByRegion);
        }), Codec.LONG.fieldOf("saveRegion").forGetter(pathfindingPathGenerator -> {
            return pathfindingPathGenerator.saveRegion;
        }), CodecUtil.BOUNDING_BOX_CODEC.fieldOf("pathBox").forGetter(pathfindingPathGenerator -> {
            return pathfindingPathGenerator.pathBox;
        })).apply(builder, PathfindingPathGenerator::new);
    });


    private final Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<List<BlockPos>>> nodesByRegion = new Long2ReferenceOpenHashMap<>();
    private final Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<List<BlockPos>>> lightsByRegion = new Long2ReferenceOpenHashMap<>();
    private final Point<Structure<?>> startPoint;
    private final Point<Structure<?>> endPoint;
    private SimplexNoiseGenerator simplex;
    private final long saveRegion;
    private final MutableBoundingBox pathBox;

    private int nSamples = 0;
    private static final boolean ADDITIONAL_DEBUG_DETAILS = false;
    private static final boolean NOISE = false;
    private static final int BOUNDING_BOX_EXPANSION = 3;
    private boolean dispose = false;

    public PathfindingPathGenerator(Point<Structure<?>> startPoint, Point<Structure<?>> endPoint, Map<Long, Map<Long, List<BlockPos>>> nodesByRegion, long saveRegion, MutableBoundingBox pathBox) {
        this.startPoint = startPoint;
        this.endPoint = endPoint;
        this.saveRegion = saveRegion;
        nodesByRegion.forEach((regionkey, positionsByChunk) -> {
            this.nodesByRegion.put(regionkey.longValue(), new Long2ReferenceOpenHashMap<>(positionsByChunk));
        });
        this.pathBox = pathBox;
    }

    public PathfindingPathGenerator(ServerWorld world, Point<Structure<?>> startPoint, Point<Structure<?>> endPoint, Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>> dataForRegion) {
        ChunkGenerator generator = world.getChunkSource().getGenerator();
        this.startPoint = new Point<>(startPoint.getStructure(), new BlockPos(startPoint.getPos().getX(), getHeight(startPoint.getPos().getX(), startPoint.getPos().getZ(), generator), startPoint.getPos().getZ()));
        this.endPoint = new Point<>(endPoint.getStructure(), new BlockPos(endPoint.getPos().getX(), getHeight(endPoint.getPos().getX(), endPoint.getPos().getZ(), generator), endPoint.getPos().getZ()));
        this.pathBox = pathBox(startPoint.getPos(), endPoint.getPos(), BOUNDING_BOX_EXPANSION);

        this.simplex = new SimplexNoiseGenerator(new Random(world.getSeed()));


        generatePath(dataForRegion, generator, world.registryAccess().registry(Registry.BIOME_REGISTRY).orElse(null));


        int lastSize = 0;
        long saveRegion = Long.MIN_VALUE;
        for (Long2ReferenceOpenHashMap.Entry<Long2ReferenceOpenHashMap<List<BlockPos>>> Long2ReferenceOpenHashMapEntry : nodesByRegion.long2ReferenceEntrySet()) {
            long regionKey = Long2ReferenceOpenHashMapEntry.getLongKey();
            Long2ReferenceOpenHashMap<List<BlockPos>> chunkEntries = Long2ReferenceOpenHashMapEntry.getValue();

            int size = chunkEntries.size();
            if (size > lastSize) {
                lastSize = size;
                saveRegion = regionKey;
            }
        }

        if (nodesByRegion.isEmpty()) {
            this.dispose = true;
        }

        this.saveRegion = saveRegion;
    }

    public static MutableBoundingBox pathBox(BlockPos startPos, BlockPos endPos) {
        return pathBox(startPos, endPos, 0);
    }

    public static MutableBoundingBox pathBox(BlockPos startPos, BlockPos endPos, int expansion) {
        int startPosX = startPos.getX();
        int startPosZ = startPos.getZ();

        int endPosX = endPos.getX();
        int endPosZ = endPos.getZ();

        return new MutableBoundingBox(Math.min(startPosX, endPosX) - expansion, 0, Math.min(startPosZ, endPosZ) - expansion, Math.max(startPosX, endPosX) + expansion, 0, Math.max(startPosZ, endPosZ) + expansion);
    }


    private void generatePath(Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>> dataForRegion, ChunkGenerator generator, Registry<Biome> biomeRegistry) {
        if (ADDITIONAL_DEBUG_DETAILS) {
            System.out.println(String.format("Started Pathfinder Gen for: %s - %s", startPoint, endPoint));
        }
        long startTime = System.currentTimeMillis();
        long startChunk = getChunkLongFromBlockPos(startPoint.getPos());
        long endChunk = getChunkLongFromBlockPos(endPoint.getPos());

        if (startChunk == endChunk) {
            if (ADDITIONAL_DEBUG_DETAILS) {
                System.out.println("Rejected path (Were in same chunk)");
            }
            return;
        }

        Tile pathInfo = findBestPath(startChunk, endChunk, dataForRegion, generator, biomeRegistry);

        if (pathInfo != null) {
            Tile currentTile = pathInfo;
            List<BlockPos> basePos = new ArrayList<>();
            while (currentTile != null) {
                basePos.add(new BlockPos(ChunkPos.getX(currentTile.pos) * 16 + 8, 0, ChunkPos.getZ(currentTile.pos) * 16 + 8));
                currentTile = currentTile.bestPrev;
            }

            List<BlockPos> positions = averagePoints(basePos);
            for(BlockPos pos : positions){
                expandBoundingBoxToFit(pos);
            }

            for(int i = 0; i < positions.size() - 1; i++){
                BlockPos start = positions.get(i);
                BlockPos end = positions.get(i + 1);

                int dx = Math.abs(start.getX() - end.getZ());
                int dz = Math.abs(start.getZ() - end.getZ());

                int steps = (int) Math.ceil(Math.max(dx, dz) / 3.0f);
                if(steps <= 1) steps = 2;

                for(int step = 0; step < steps; step++){
                    addBlockPos(PathGenerator.getLerpedBlockPos(start, end, (float) step / steps));
                }
            }
            addBlockPos(positions.get(basePos.size() - 1));
        } else {
            if (ADDITIONAL_DEBUG_DETAILS) {
                System.out.println("No path found!");
            }
        }


        System.out.println(String.format("Pathfinder Gen for: %s - %s took %sms. Sampled %s times.", startPoint, endPoint, System.currentTimeMillis() - startTime, nSamples));
    }

    //Returns a tile that represents the endChunk
    //Tracing through the .bestPrev of every tile yields the path
    private Tile findBestPath(long startChunk, long endChunk, Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>> dataForRegion, ChunkGenerator generator, Registry<Biome> biomeRegistry) {
        Long2ReferenceOpenHashMap<Tile> tiles = new Long2ReferenceOpenHashMap<>();

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
                Tile tile = getTileAt(pos, tiles, dataForRegion, generator, biomeRegistry);
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

        if (found) return tiles.get(endChunk);
        else return null;
    }

    private void expandBoundingBoxToFit(BlockPos pos){
        if(pathBox.x0 > pos.getX() - BOUNDING_BOX_EXPANSION){
            pathBox.x0 = pos.getX() - BOUNDING_BOX_EXPANSION;
        }else if(pathBox.x1 < pos.getX() + BOUNDING_BOX_EXPANSION){
            pathBox.x1 = pos.getX() + BOUNDING_BOX_EXPANSION;
        }

        if(pathBox.z0 > pos.getZ() - BOUNDING_BOX_EXPANSION){
            pathBox.z0 = pos.getZ() - BOUNDING_BOX_EXPANSION;
        }else if(pathBox.z1 < pos.getZ() + BOUNDING_BOX_EXPANSION){
            pathBox.z1 = pos.getZ() + BOUNDING_BOX_EXPANSION;
        }
    }

    private List<BlockPos> averagePoints(List<BlockPos> pos) {
        List<BlockPos> averagePoints = new ArrayList<>(pos.size());
        averagePoints.add(pos.get(0));
        for (int i = 1; i < pos.size() - 1; i++) {
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
        long chunkKey = getChunkLongFromBlockPos(pos);
        long regionKey = chunkToRegionKey(chunkKey);
        lightsByRegion.computeIfAbsent(regionKey, (key) -> {
            return new Long2ReferenceOpenHashMap<>();
        }).computeIfAbsent(chunkKey, (key) -> {
            return new ArrayList<>();
        }).add(pos);
    }

    private void addBlockPos(BlockPos pos) {
        long chunkKey = getChunkLongFromBlockPos(pos);
        long regionKey = chunkToRegionKey(chunkKey);
        nodesByRegion.computeIfAbsent(regionKey, (key) -> {
            return new Long2ReferenceOpenHashMap<>();
        }).computeIfAbsent(chunkKey, (key) -> {
            return new ArrayList<>();
        }).add(pos);
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

    private Tile getTileAt(long pos, Long2ReferenceOpenHashMap<Tile> tiles, Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>> dataForRegion, ChunkGenerator generator, Registry<Biome> biomeRegistry) {
        Tile tile = tiles.get(pos);
        if (tile == null) {
            Tile newTile = new Tile(getWeight(dataForRegion, pos, generator, biomeRegistry), pos);
            tiles.put(pos, newTile);
            return newTile;
        }
        return tile;
    }

    private int getWeight(Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<DataForChunk>> dataForRegion, long pos, ChunkGenerator generator, Registry<Biome> biomeRegistry) {
        nSamples++;
        int chunkX = ChunkPos.getX(pos);
        int chunkZ = ChunkPos.getZ(pos);

        Long2ReferenceOpenHashMap<DataForChunk> regionData = dataForRegion.computeIfAbsent(chunkToRegionKey(pos), (key) -> {
            return new Long2ReferenceOpenHashMap<>();
        });


        DataForChunk chunkData = regionData.computeIfAbsent(pos, (key) -> {
            return new DataForChunk(generator.getBiomeSource().getNoiseBiome((chunkX << 2) + 2, 60, (chunkZ << 2) + 2));
        });

        Biome biome = chunkData.getBiome();
        RegistryKey<Biome> biomeKey = null;
        if (biomeRegistry != null) {
            biomeKey = biomeRegistry.getResourceKey(biome).orElse(null);
        }


        Set<Type> biomeTypes = BiomeDictionary.getTypes(biomeKey);
        if (containsAny(biomeTypes, Type.MOUNTAIN, Type.HILLS, Type.OCEAN, Type.PLATEAU)) {
            return 10000;
        } else {
            int height = chunkData.getHeight(generator, chunkX * 16 + 8, chunkZ * 16 + 8);
            if (NOISE && testHeight(startPoint.getPos().getY(), endPoint.getPos().getY(), height, generator.getSeaLevel())) {
                return 10000;
            } else {
                if (containsAny(biomeTypes, Type.RIVER, Type.SPOOKY)) {
                    return 3; //Will make river crossing try to be shorter
                }
            }
        }

        return 1;
    }

    private static <T> boolean containsAny(Collection<T> collection, T... items) {
        for (T item : items) {
            if (collection.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private static int getHeight(int x, int z, ChunkGenerator generator) {
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
    public Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<List<BlockPos>>> getNodesByRegion() {
        return nodesByRegion;
    }

    @Override
    public Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<List<BlockPos>>> getLightsByRegion() {
        return lightsByRegion;
    }

    @Override
    public BlockState debugState() {
        return Blocks.RED_CONCRETE.defaultBlockState();
    }

    @Override
    public MutableBoundingBox getBoundingBox() {
        return pathBox;
    }

    @Override
    public boolean createdSuccessfully() {
        return true;
    }

    @Override
    public Point<Structure<?>> getStart() {
        return this.startPoint;
    }

    @Override
    public Point<Structure<?>> getEnd() {
        return this.endPoint;
    }

    @Override
    public long saveRegion() {
        return this.saveRegion;
    }

    @Override
    public boolean dispose() {
        return this.dispose;
    }

    @Override
    public void setLastLoadedGameTime(long gameTime) {

    }

    @Override
    public long lastLoadedGameTime() {
        return 0;
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
