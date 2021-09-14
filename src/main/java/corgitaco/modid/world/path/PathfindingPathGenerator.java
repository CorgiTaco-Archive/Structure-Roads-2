package corgitaco.modid.world.path;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterable;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

public class PathfindingPathGenerator implements IPathGenerator {
    private final Long2ObjectArrayMap<List<BlockPos>> nodesByChunk = new Long2ObjectArrayMap<>();
    private final Long2ObjectArrayMap<List<BlockPos>> lightsByChunk = new Long2ObjectArrayMap<>();
    private final BiomeProvider biomeSource;
    private final ChunkGenerator chunkGenerator;
    private final int startY;
    private final int endY;

    private int nSamples = 0;
    private static final boolean ADDITIONAL_DEBUG_DETAILS = false;

    public PathfindingPathGenerator(ServerWorld world, BlockPos startPos, BlockPos endPos, Random random, BiomeProvider biomeSource) {
        this.biomeSource = biomeSource;
        this.chunkGenerator = world.getChunkSource().generator;

        this.startY = chunkGenerator.getBaseHeight(startPos.getX(), startPos.getZ(), Heightmap.Type.OCEAN_FLOOR_WG);
        this.endY = chunkGenerator.getBaseHeight(endPos.getX(), endPos.getZ(), Heightmap.Type.OCEAN_FLOOR_WG);

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


        Long2ObjectArrayMap<Tile> tiles = new Long2ObjectArrayMap<>();

        PriorityQueue<Tile> tilesToCheck = new PriorityQueue<>(Tile::compareTo);
        Tile tileAtStartPos = new Tile(false, startChunk);
        tileAtStartPos.activate(endChunk, 0);

        tiles.put(startPos.asLong(), tileAtStartPos);
        tilesToCheck.add(tileAtStartPos);

        boolean found = false;
        while (!tilesToCheck.isEmpty()) {
            if (nSamples > 10000) break; //To not get stuck if goal is completely inaccesible
            Tile currentTile = tilesToCheck.poll();
            int newDist = currentTile.distFromStart + 1;

            if (currentTile.pos == endChunk) {
                found = true;
                break;
            }

            for (long pos : surroundingChunks(currentTile.pos)) {
                Tile tile = getTileAt(pos, tiles);
                if (tile.isMountain) continue;


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

        if (found) {
            Tile currentTile = tiles.get(endChunk);
            while (currentTile.bestPrev != null) {
                BlockPos start = new BlockPos(ChunkPos.getX(currentTile.pos) * 16 + 8, 60, ChunkPos.getZ(currentTile.pos) * 16 + 8);
                BlockPos end = new BlockPos(ChunkPos.getX(currentTile.bestPrev.pos) * 16 + 8, 60, ChunkPos.getZ(currentTile.bestPrev.pos) * 16 + 8);
                for (float t = 0; t < 1; t += 0.2) {
                    addBlockPos(PathGenerator.getLerpedBlockPos(start, end, t));
                }

                currentTile = currentTile.bestPrev;
            }
        } else {
            if (ADDITIONAL_DEBUG_DETAILS) {
                System.out.println("No path found!");
            }
        }


        System.out.println(String.format("Pathfinder Gen for: %s - %s took %sms. Sampled %s times.", startPos, endPos, System.currentTimeMillis() - startTime, nSamples));
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

    private Tile getTileAt(long pos, Long2ObjectArrayMap<Tile> tiles) {
        Tile tile = tiles.get(pos);
        if (tile == null) {
            Tile newTile = new Tile(shouldAvoid(pos), pos);
            tiles.put(pos, newTile);
            return newTile;
        }
        return tile;
    }

    private boolean shouldAvoid(long pos) {
        nSamples++;
        //world.getBiome() seems to sometimes get the biome from the world and cause a lock for some reason
        //As far as I can tell this alternative will always calculate the biome
        int chunkX = ChunkPos.getX(pos);
        int chunkZ = ChunkPos.getZ(pos);
        Biome biome = this.biomeSource.getNoiseBiome((chunkX << 2) + 2, 60, (chunkZ << 2) + 2);

        if (biome.getBiomeCategory().equals(Biome.Category.OCEAN) || testHeight(startY, startY, SectionPos.sectionToBlockCoord(chunkX), SectionPos.sectionToBlockCoord(chunkZ), chunkGenerator)) {
            return true;
        }
        return biome.getDepth() > 0.5f;

    }

    public static boolean testHeight(int startY, int endY, int x, int z, ChunkGenerator generator) {
        int baseHeight = generator.getBaseHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG);
        return baseHeight <= startY + 10 && baseHeight >= generator.getSeaLevel() && baseHeight >= endY;
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
        private final boolean isMountain;
        private final long pos;

        public Tile(boolean isMountain, long pos) {
            this.isMountain = isMountain;
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
