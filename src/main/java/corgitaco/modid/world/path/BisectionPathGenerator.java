package corgitaco.modid.world.path;

/*import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.math.SectionPos;
import net.minecraft.world.biome.provider.BiomeProvider;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;*/

public class BisectionPathGenerator /*implements IPathGenerat*/{
    /*private final Long2ObjectArrayMap<List<BlockPos>> nodesByChunk = new Long2ObjectArrayMap<>();
    private final Long2ObjectArrayMap<List<BlockPos>> lightsByChunk = new Long2ObjectArrayMap<>();
    private final ServerWorld world;
    private MutableBoundingBox boundingBox;
    private boolean rejected = false;
    BlockPos startPos, endPos;
    private static final int BOUNDING_BOX_EXPANSION = 3;
    private int nSamples = 0;

    public BisectionPathGenerator(ServerWorld world, BlockPos startPos, BlockPos endPos, Random random, BiomeProvider biomeSource){
        this.startPos = startPos;
        this.endPos = endPos;
        this.world = world;

        if(!PathGeneratorUtil.canPathPassThrough(world, startPos)){
            rejected = true;
            return;
        }

        if(!PathGeneratorUtil.canPathPassThrough(world, endPos)){
            rejected = true;
            return;
        }

        boundingBox = pathBox(startPos, endPos, BOUNDING_BOX_EXPANSION);

        long startTime = System.currentTimeMillis();
        System.out.println("Started creating bisected path");
        addBlockPos(startPos);
        split(startPos, endPos, 0);
        addBlockPos(endPos);

        if(startPos.equals(endPos)){
            System.out.println("Rejected Path: Both positions were in same chunk");
            rejected = true;
        }

        System.out.println(String.format("Pathfinder Gen for: %s - %s took %sms. Sampled %s times.", startPos, endPos, System.currentTimeMillis() - startTime, nSamples));
        if(rejected)
            System.out.println("Path was rejected");
    }

    public static MutableBoundingBox pathBox(BlockPos startPos, BlockPos endPos, int expansion) {
        int startPosX = startPos.getX();
        int startPosZ = startPos.getZ();

        int endPosX = endPos.getX();
        int endPosZ = endPos.getZ();

        return new MutableBoundingBox(Math.min(startPosX, endPosX) - expansion, 0, Math.min(startPosZ, endPosZ) - expansion, Math.max(startPosX, endPosX) + expansion, 0, Math.max(startPosZ, endPosZ) + expansion);
    }

    private void split(BlockPos startPos, BlockPos endPos, int depth){
        if(nSamples > 10000){
            rejected = true;
            return;
        }

        int dx = endPos.getX() - startPos.getX();
        int dz = endPos.getZ() - startPos.getZ();

        int squaredDist = dx * dx + dz * dz;
        if(squaredDist < 256 || depth > 10)
            return;

        BlockPos.Mutable middlePos = bisect(startPos, endPos).mutable();

        int shiftX = -dz / 5;
        int shiftZ = dx / 5;

        int m = 0;
        boolean notValid;
        BlockPos.Mutable middlePosCopy = new BlockPos.Mutable(middlePos.getX(), 0, middlePos.getZ());
        nSamples++;
        while(notValid = !PathGeneratorUtil.canPathPassThrough(world, middlePos) && m < 5){
            nSamples++;
            middlePos.move(shiftX, 0, shiftZ);
            m++;
        }

        if(notValid){
            middlePos = middlePosCopy;
            m = 0;
            nSamples++;
            while(notValid = !PathGeneratorUtil.canPathPassThrough(world, middlePos) && m < 5){
                middlePos.move(-shiftX, 0, -shiftZ);
                m++;
                nSamples++;
            }
        }

        if(notValid){
            rejected = true;
            return;
        }

        addBlockPos(middlePos);

        split(startPos, middlePos, depth + 1);
        if(rejected) return;
        split(middlePos, endPos, depth + 1);
    }

    private void expandBoundingBoxToFit(BlockPos pos){
        if(boundingBox.x0 > pos.getX() - BOUNDING_BOX_EXPANSION){
            boundingBox.x0 = pos.getX() - BOUNDING_BOX_EXPANSION;
        }else if(boundingBox.x1 < pos.getX() + BOUNDING_BOX_EXPANSION){
            boundingBox.x1 = pos.getX() + BOUNDING_BOX_EXPANSION;
        }

        if(boundingBox.z0 > pos.getZ() - BOUNDING_BOX_EXPANSION){
            boundingBox.z0 = pos.getZ() - BOUNDING_BOX_EXPANSION;
        }else if(boundingBox.z1 < pos.getZ() + BOUNDING_BOX_EXPANSION){
            boundingBox.z1 = pos.getZ() + BOUNDING_BOX_EXPANSION;
        }
    }

    private static BlockPos bisect(BlockPos start, BlockPos end){
        return new BlockPos((start.getX() + end.getX()) / 2, 0, (start.getZ() + end.getZ()) / 2);
    }

    private void addBlockPos(BlockPos pos) {
        expandBoundingBoxToFit(pos);
        long chunkPos = getChunkLongFromBlockPos(pos);
        List<BlockPos> nodeList = nodesByChunk.get(chunkPos);
        if (nodeList == null) {
            nodeList = new ArrayList<>();
            nodesByChunk.put(chunkPos, nodeList);
        }

        nodeList.add(pos);
    }

    public static long getChunkLongFromBlockPos(BlockPos pos) {
        return ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    @Override
    public boolean createdSuccessfully() {
        return !rejected;
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
        return Blocks.DIAMOND_BLOCK.defaultBlockState();
    }

    @Override
    public MutableBoundingBox getBoundingBox() {
        return boundingBox;
    }*/
}
