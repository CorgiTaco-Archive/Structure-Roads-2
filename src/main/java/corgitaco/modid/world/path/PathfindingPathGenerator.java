package corgitaco.modid.world.path;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.server.ServerWorld;

import java.util.*;

public class PathfindingPathGenerator implements IPathGenerator{
    private final Long2ObjectArrayMap<List<BlockPos>> nodesByChunk = new Long2ObjectArrayMap<>();
    private final Long2ObjectArrayMap<List<BlockPos>> lightsByChunk = new Long2ObjectArrayMap<>();
    private final BiomeProvider biomeSource;
    private int nSamples = 0;

    public PathfindingPathGenerator(ServerWorld world, BlockPos startPos, BlockPos endPos, Random random, BiomeProvider biomeSource){
        this.biomeSource = biomeSource;

        System.out.println("Started Pathfinder Gen");
        long startTime = System.currentTimeMillis();
        ChunkPos startChunk = new ChunkPos(startPos);
        ChunkPos endChunk = new ChunkPos(endPos);

        if(startChunk.equals(endChunk)){
            System.out.println("Rejected path (Were in same chunk)");
            return;
        }


        Map<Long, Tile> tiles = new HashMap<>();

        PriorityQueue<Tile> tilesToCheck = new PriorityQueue<>(Tile::compareTo);
        Tile tileAtStartPos = new Tile(false, startChunk);
        tileAtStartPos.activate(endChunk, 0);

        tiles.put(startPos.asLong(), tileAtStartPos);
        tilesToCheck.add(tileAtStartPos);

        boolean found = false;
        while(!tilesToCheck.isEmpty()){
            if(nSamples > 10000) break; //To not get stuck if goal is completely inaccesible
            Tile currentTile = tilesToCheck.poll();
            int newDist = currentTile.distFromStart + 1;

            if(currentTile.pos.equals(endChunk)){
                found = true;
                break;
            }

            for(ChunkPos pos : surroundingChunks(currentTile.pos)){
                Tile tile = getTileAt(world, pos, tiles);
                if(tile.isMountain) continue;


                if(newDist < tile.distFromStart){
                    if(!tile.active){
                        tile.activate(endChunk, newDist);
                    }

                    tile.distFromStart = newDist;
                    tile.bestPrev = currentTile;

                    tilesToCheck.add(tile);
                }
            }
        }

        if(found){
            Tile currentTile = tiles.get(endChunk.toLong());
            while(currentTile.bestPrev != null){
                BlockPos start = new BlockPos(currentTile.pos.x * 16 + 8, 60, currentTile.pos.z * 16 + 8);
                BlockPos end = new BlockPos(currentTile.bestPrev.pos.x * 16 + 8, 60, currentTile.bestPrev.pos.z * 16 + 8);
                for(float t = 0; t < 1; t += 0.2){
                    addBlockPos(PathGenerator.getLerpedBlockPos(start, end, t));
                }

                currentTile = currentTile.bestPrev;
            }
        }else{
            System.out.println("No path found!");
        }

        System.out.println("Created path in " + (System.currentTimeMillis() - startTime) + " ms");
        System.out.println("Sampled " + nSamples + " times");
        System.out.println("Completed pathfinder gen");
    }

    private void addBlockPos(BlockPos pos){
        ChunkPos chunkPos = new ChunkPos(pos);
        List<BlockPos> nodeList = nodesByChunk.get(chunkPos.toLong());
        if(nodeList == null){
            nodeList = new ArrayList<>();
            nodesByChunk.put(chunkPos.toLong(), nodeList);
        }

        nodeList.add(pos);
    }

    private Iterable<? extends ChunkPos> surroundingChunks(ChunkPos pos) {
        List<ChunkPos> points = new ArrayList<>();
        points.add(new ChunkPos(pos.x + 1, pos.z));
        points.add(new ChunkPos(pos.x - 1, pos.z));
        points.add(new ChunkPos(pos.x, pos.z + 1));
        points.add(new ChunkPos(pos.x, pos.z - 1));

        return points;
    }

    private Tile getTileAt(ServerWorld world, ChunkPos pos, Map<Long, Tile> tiles){
        Tile tile = tiles.get(pos.toLong());
        if(tile == null) {
            Tile newTile = new Tile(shouldAvoid(world, pos), pos);
            tiles.put(pos.toLong(), newTile);
            return newTile;
        }
        return tile;
    }

    private boolean shouldAvoid(ServerWorld world, ChunkPos pos){
        nSamples++;
        //world.getBiome() seems to sometimes get the biome from the world and cause a lock for some reason
        //As far as I can tell this alternative will always calculate the biome
        Biome biome = world.getBiomeManager().getNoiseBiomeAtPosition(pos.x * 16 + 8, 60, pos.z * 16 + 8);
        if(biome.getBiomeCategory().equals(Biome.Category.OCEAN))
            return true;
        return biome.getDepth() > 0.5f;

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

    public static class Tile implements Comparable{
        private Tile bestPrev = null;
        private int distFromStart = Integer.MAX_VALUE;
        private float distFromGoal = Float.MAX_VALUE;
        private boolean active = false;
        private final boolean isMountain;
        private final ChunkPos pos;

        public Tile(boolean isMountain, ChunkPos pos){
            this.isMountain = isMountain;
            this.pos = pos;
        }

        public float getFCost(){
            return distFromGoal + distFromStart;
        }

        @Override
        public int compareTo(Object o) {
            Tile tile = (Tile) o;
            int diff = Float.compare(getFCost(), tile.getFCost());
            if(diff == 0){
                return Float.compare(distFromGoal, tile.distFromGoal);
            }
            return diff;
        }

        public void activate(ChunkPos goal, int distFromStart){
            active = true;
            int dx = pos.x - goal.x;
            int dz = pos.z - goal.z;
            distFromGoal = (float) Math.sqrt(dx * dx + dz * dz);
            this.distFromStart = distFromStart;
        }
    }
}
