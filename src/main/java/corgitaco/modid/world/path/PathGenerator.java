package corgitaco.modid.world.path;

import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.*;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.feature.structure.Structure;

import java.util.*;

import static corgitaco.modid.core.StructureRegionManager.chunkToRegionKey;

public class PathGenerator /*implements IPathGenerator<Structure<?>>*/ {
    /*
    public static boolean DEBUG = false;

    private final MutableBoundingBox pathBox;
    private final List<PointWithGradient> points;
    private final Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<Set<BlockPos>>> nodesByChunk = new Long2ReferenceOpenHashMap<>();
    private final Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<Set<BlockPos>>> lightsByChunk = new Long2ReferenceOpenHashMap<>();
    private final BlockState debugState;
    private final long startStructureChunk;
    private final long endStructureChunk;

    public PathGenerator(BlockPos startPos, BlockPos endPos, long startStructureChunk, long endStructureChunk, Random random, int pointCount, double windiness) {
        this.startStructureChunk = startStructureChunk;
        this.endStructureChunk = endStructureChunk;
        this.pathBox = pathBox(startPos, endPos);
        points = getPointWithGradients(random, startPos, endPos, pathBox, pointCount, windiness);

        for (int idx = 0; idx < points.size() - 1; idx++) {
            for (BlockPos pos : getBezierPoints(points.get(idx), points.get(idx + 1), 0.001)) {
                long chunkPosKey = ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
                nodesByChunk.computeIfAbsent(chunkToRegionKey(chunkPosKey), (key) -> new Long2ReferenceOpenHashMap<>()).computeIfAbsent(chunkPosKey, (key) -> new HashSet<>()).add(pos);
            }

            for (BlockPos pos : getBezierPoints(points.get(idx), points.get(idx + 1), 0.004)) {
                long chunkPosKey = ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
                nodesByChunk.computeIfAbsent(chunkToRegionKey(chunkPosKey), (key) -> new Long2ReferenceOpenHashMap<>()).computeIfAbsent(chunkPosKey, (key) -> new HashSet<>()).add(pos);
            }
        }

        debugState = Registry.BLOCK.getRandom(random).defaultBlockState();
    }

    public boolean intersects(BlockPos pos) {
        return pathBox.intersects(pos.getX(), pos.getX(), pos.getZ(), pos.getZ());
    }

    public List<PointWithGradient> getPoints() {
        return points;
    }

    public Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<Set<BlockPos>>> getNodesByRegion() {
        return nodesByChunk;
    }

    public BlockState debugState() {
        return debugState;
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
        return null;
    }

    @Override
    public Point<Structure<?>> getEnd() {
        return null;
    }

    @Override
    public long saveRegion() {
        return 0;
    }

    public Long2ReferenceOpenHashMap<Long2ReferenceOpenHashMap<Set<BlockPos>>> getLightsByRegion() {
       return this.lightsByChunk;
    }

    @Override
    public boolean dispose() {
        return false;
    }

    @Override
    public void setLastLoadedGameTime(long gameTime) {

    }

    @Override
    public long lastLoadedGameTime() {
        return 0;
    }

    public static List<PointWithGradient> getPointWithGradients(Random random, BlockPos startPos, BlockPos endPos, MutableBoundingBox pathBox, int pointCount, double windiness) {
        double angle = Math.atan2(endPos.getZ() - startPos.getZ(), endPos.getX() - startPos.getX());
        double dist = Math.sqrt((startPos.getX() - endPos.getX()) * (startPos.getX() - endPos.getX()) + (startPos.getZ() - endPos.getZ()) * (startPos.getZ() - endPos.getZ()));
        double scale = 0.5 * dist / pointCount;

        return getPointsWithGradients(random, startPos, endPos, pathBox, pointCount, angle, dist, scale, windiness);
    }

    private static List<PointWithGradient> getPointsWithGradients(Random random, BlockPos startPos, BlockPos endPos, MutableBoundingBox pathBox, int pointCount, double angle, double dist, double scale, double windiness) {
        List<PointWithGradient> points = new ArrayList<>();
        for (int point = 0; point < Math.max(pointCount, 2); point++) {
            double t = (double) (point) / (pointCount - 1);
            BlockPos pos = getLerpedBlockPos(startPos, endPos, t);

            Vec2 gradient = getRandomGradient(random, angle, scale, windiness);
            pos = stayInBox(random, point, pointCount, dist, pos, pathBox, gradient);

            points.add(new PointWithGradient(pos, gradient));
        }

        if (DEBUG) {
            System.out.println("Start: " + startPos);
            System.out.println("End: " + endPos);
            System.out.println("Length: " + dist);
            System.out.println("Scale: " + scale);
        }
        return points;
    }

    private static Vec2 getRandomGradient(Random random, double angle, double scale, double windiness) {
        double angleAtPoint = angle + (random.nextDouble() * 2 - 1) * windiness;
        Vec2 normalizedVector = new Vec2(Math.cos(angleAtPoint), Math.sin(angleAtPoint));
        double length = (random.nextDouble() + 1) * scale;
        return normalizedVector.mult(length);
    }

    private static BlockPos stayInBox(Random random, int point, int pointCount, double dist, BlockPos pos, MutableBoundingBox box, Vec2 gradient) {
        double shiftAngle = random.nextDouble() * Math.PI * 2;
        //Most of the new code is attempts and getting it to fit within the bounding box
        if (point != 0 && point != pointCount - 1) {
            double shiftLength = dist * 0.25 / pointCount;


            pos = pos.east((int) (Math.cos(shiftAngle) * shiftLength)).south((int) (Math.sin(shiftAngle) * shiftLength));

            int lowerXPos = (int) (pos.getX() - Math.abs(gradient.getX()));
            int upperXPos = (int) (pos.getX() + Math.abs(gradient.getX()));

            int lowerZPos = (int) (pos.getZ() - Math.abs(gradient.getY()));
            int upperZPos = (int) (pos.getZ() + Math.abs(gradient.getY()));

            if (lowerXPos < box.x0) {
                pos = pos.east(box.x0 - lowerXPos);
            } else if (upperXPos >= box.x1) {
                pos = pos.west(box.x1 - upperXPos + 1);
            }

            if (lowerZPos < box.z0) {
                pos = pos.south(box.z0 - lowerZPos);
            } else if (upperZPos >= box.z1) {
                pos = pos.north(box.z1 - upperZPos + 1);
            }
        }else{
            //Note: this will only work if expansion = 0 because of the way it checks what side the points are
            int multiplier = (point == 0) ? 1 : -1;

            if(pos.getX() == box.x0){
                gradient.x = Math.abs(gradient.x) * multiplier; //Ensure gradient is positive so it doesn't exit bounding box

                int xPos = (int) (pos.getX() - multiplier * gradient.x);
                if(xPos > box.x1){
                    gradient.x = multiplier * (box.x1 - box.x0);
                }
            }else{
                gradient.x = -Math.abs(gradient.x) * multiplier;

                int xPos = (int) (pos.getX() - multiplier * gradient.x);
                if(xPos < box.x0){
                    gradient.x = -multiplier * (box.x1 - box.x0);
                }
            }

            if(pos.getZ() == box.z0){
                gradient.y = Math.abs(gradient.y) * multiplier;

                int zPos = (int) (pos.getZ() - multiplier * gradient.y);
                if(zPos > box.z1){
                    gradient.y = multiplier * (box.z1 - box.z0);
                }
            }else{
                gradient.y = -Math.abs(gradient.y) * multiplier;

                int zPos = (int) (pos.getZ() - multiplier * gradient.y);
                if(zPos < box.z0){
                    gradient.y = - multiplier * (box.z1 - box.z0);
                }
            }

            int zPos = (int) (pos.getZ() - multiplier * gradient.y);
        }
        return pos;
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
    */
    public static BlockPos getLerpedBlockPos(BlockPos start, BlockPos end, double lerp) {
        return new BlockPos(MathHelper.lerp(lerp, start.getX(), end.getX()), 0, MathHelper.lerp(lerp, start.getZ(), end.getZ()));
    }
    /*
    public static List<BlockPos> getBezierPoints(PointWithGradient start, PointWithGradient end, double lerpIncrement) {
        BlockPos startPos = start.getPos();
        BlockPos endPos = end.getPos();

        BlockPos controlOne = start.getGradient().add(startPos);
        BlockPos controlTwo = end.getGradient().addBackwards(endPos);

        List<BlockPos> points = new ArrayList<>();

        for (double t = 0; t <= 1; t += lerpIncrement) {
            points.add(deCastelJustAlgPos(startPos, endPos, controlOne, controlTwo, t));
        }

        return points;
    }

    public static BlockPos deCastelJustAlgPos(BlockPos start, BlockPos end, BlockPos drag, BlockPos drag2, double lerp) {
        double draggedStartX = MathHelper.lerp(lerp, start.getX(), drag.getX());
        double draggedStartZ = MathHelper.lerp(lerp, start.getZ(), drag.getZ());

        double draggedDraggedX = MathHelper.lerp(lerp, drag.getX(), drag2.getX());
        double draggedDraggedZ = MathHelper.lerp(lerp, drag.getZ(), drag2.getZ());

        double endDraggedX = MathHelper.lerp(lerp, drag2.getX(), end.getX());
        double endDraggedZ = MathHelper.lerp(lerp, drag2.getZ(), end.getZ());

        double x = MathHelper.lerp(lerp, MathHelper.lerp(lerp, draggedStartX, draggedDraggedX), MathHelper.lerp(lerp, draggedDraggedX, endDraggedX));
        double z = MathHelper.lerp(lerp, MathHelper.lerp(lerp, draggedStartZ, draggedDraggedZ), MathHelper.lerp(lerp, draggedDraggedZ, endDraggedZ));
        return new BlockPos(x, 0, z);
    }

    public long getStartStructureChunk() {
        return startStructureChunk;
    }

    public long getEndStructureChunk() {
        return endStructureChunk;
    }

    public MutableBoundingBox getPathBox() {
        return pathBox;
    }*/

    public static class PointWithGradient {
        private final BlockPos pos;
        private final Vec2 gradient;

        public PointWithGradient(BlockPos pos, Vec2 gradient) {
            this.pos = pos;
            this.gradient = gradient;
        }

        public BlockPos getPos() {
            return pos;
        }

        public Vec2 getGradient() {
            return gradient;
        }
    }

    public static class Vec2 {
        private double x, y;

        public Vec2(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public Vec2 mult(double n) {
            return new Vec2(x * n, y * n);
        }

        public BlockPos add(BlockPos pos) {
            return new BlockPos(pos.getX() + x, pos.getY(), pos.getZ() + y);
        }

        public BlockPos addBackwards(BlockPos pos) {
            return new BlockPos(pos.getX() - x, pos.getY(), pos.getZ() - y);
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }
    }
}
