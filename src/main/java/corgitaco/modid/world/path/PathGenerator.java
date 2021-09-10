package corgitaco.modid.world.path;

import it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.*;
import net.minecraft.util.registry.Registry;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PathGenerator {

    public static boolean DEBUG = false;

    private final MutableBoundingBox pathBox;
    private final List<PointWithGradient> points;
    private final Long2ObjectArrayMap<List<BlockPos>> nodesByChunk = new Long2ObjectArrayMap<>();
    private final Long2ObjectArrayMap<List<BlockPos>> lightsByChunk = new Long2ObjectArrayMap<>();
    private final BlockState debugState;

    public PathGenerator(BlockPos startPos, BlockPos endPos, long startStructureChunk, long endStructureChunk, Random random, int pointCount, double windiness) {
        this.pathBox = pathBox(startPos, endPos);
        points = getPointWithGradients(random, startPos, endPos, pathBox, pointCount, windiness);

        for (int idx = 0; idx < points.size() - 1; idx++) {
            for (BlockPos pos : getBezierPoints(points.get(idx), points.get(idx + 1), 0.001)) {
                nodesByChunk.computeIfAbsent(ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())), (key) -> new ArrayList<>()).add(pos);
            }

            for (BlockPos pos : getBezierPoints(points.get(idx), points.get(idx + 1), 0.004)) {
                lightsByChunk.computeIfAbsent(ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())), (key) -> new ArrayList<>()).add(pos);
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

    public Long2ObjectArrayMap<List<BlockPos>> getNodesByChunk() {
        return nodesByChunk;
    }

    public BlockState debugState() {
        return debugState;
    }

    public Long2ObjectArrayMap<List<BlockPos>> getLightsByChunk() {
        return lightsByChunk;
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

            pos = stayInBox(random, point, pointCount, dist, pos, pathBox);

            points.add(new PointWithGradient(pos, getRandomGradient(random, angle, scale, windiness)));


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

    private static BlockPos stayInBox(Random random, int point, int pointCount, double dist, BlockPos pos, MutableBoundingBox box) {
        double shiftAngle = random.nextDouble() * Math.PI * 2;
        if (point != 0 && point != pointCount - 1) {
            double shiftLength = dist * 0.25 / pointCount;


            pos = pos.east((int) (Math.cos(shiftAngle) * shiftLength)).south((int) (Math.sin(shiftAngle) * shiftLength));

            if (pos.getX() < box.x0) {
                //For some reason there's no setX()
                pos = pos.east(-pos.getX());
            } else if (pos.getX() >= box.x1) {
                pos = pos.west(pos.getX() - box.x1 + 1);
            }

            if (pos.getZ() < box.z0) {
                pos = pos.south(-pos.getZ());
            } else if (pos.getZ() >= box.z1) {
                pos = pos.north(pos.getZ() - box.z1 + 1);
            }
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

    public static BlockPos getLerpedBlockPos(BlockPos start, BlockPos end, double lerp) {
        return new BlockPos(MathHelper.lerp(lerp, start.getX(), end.getX()), 0, MathHelper.lerp(lerp, start.getZ(), end.getZ()));
    }

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
        private final double x, y;

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
