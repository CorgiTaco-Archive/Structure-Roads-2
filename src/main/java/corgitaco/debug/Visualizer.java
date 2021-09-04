package corgitaco.debug;

import net.minecraft.util.Direction;
import net.minecraft.util.math.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class Visualizer {

    public static void main(String[] args) {
        long seed = 778788778;
        Random random = new Random(seed);
        int range = 1000;
        MutableBoundingBox image = new MutableBoundingBox(0, 0, 0, range - 1, 0, range - 1);

        BufferedImage img = new BufferedImage(range, range, BufferedImage.TYPE_INT_RGB);
        String pathname = "run\\yeet.png";
        File file = new File(pathname);
        if (file.exists())
            file.delete();

        BlockPos startPos = new BlockPos(random.nextInt(range - 25), 0, random.nextInt(range - 25));
        int startX = SectionPos.blockToSectionCoord(startPos.getX());
        int startZ = SectionPos.blockToSectionCoord(startPos.getZ());
        long startStructurePos = ChunkPos.asLong(startX, startZ);

        BlockPos endPos = new BlockPos(random.nextInt(range - 25), 0, random.nextInt(range - 25));

        int endX = SectionPos.blockToSectionCoord(endPos.getX());
        int endZ = SectionPos.blockToSectionCoord(endPos.getZ());
        long endStructurePos = ChunkPos.asLong(endX, endZ);


        List<BlockPos> pathNodes = new ArrayList<>();

        int pointCount = 7;

        for (int point = 0; point <= pointCount - 1; point++) {
            double lerp = (double) (point) / pointCount;
            double nextLerp = (double) (point + 1) / pointCount;
            BlockPos initial = getLerpedBlockPos(startPos, endPos, lerp);
            BlockPos end = getLerpedBlockPos(startPos, endPos, nextLerp);
            pathNodes.addAll(getRandomDraggedDeCastelJusAlgNodes(random, range, initial, end));
        }

//       pathNodes.addAll(getRandomDraggedDeCastelJusAlgNodes(random, range, startPos, endPos));

        for (int x = 0; x < range; x++) {
            for (int z = 0; z < range; z++) {
                int pixelSize = 1;
                for (int pixelXSize = -pixelSize; pixelXSize <= pixelSize; pixelXSize++) {
                    for (int pixelZSize = -pixelSize; pixelZSize <= pixelSize; pixelZSize++) {
                        img.setRGB(MathHelper.clamp(x + pixelXSize, 0, range - 1), MathHelper.clamp(z + pixelZSize, 0, range - 1), getColor(x, z, startX, startZ, endX, endZ));
                    }
                }
            }
        }

        int rgb = new Color(24, 154, 25).getRGB();

        for (BlockPos node : pathNodes) {
            if (image.intersects(node.getX(), node.getZ(), node.getX(), node.getZ())) {
                img.setRGB(node.getX(), node.getZ(), rgb);
            }
        }


        try {
            file = new File(pathname);
            ImageIO.write(img, "png", file);
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    private static List<BlockPos> getRandomDraggedDeCastelJusAlgNodes(Random random, int range, BlockPos startPos, BlockPos endPos) {
        MutableBoundingBox pathBox = pathBox(startPos, endPos);

        int minExpansion = 100;
        int maxExpansion = 250;
        BlockPos drag1 = new BlockPos(Math.min(startPos.getX(), endPos.getX()) + random.nextInt(pathBox.getXSpan() - 1) + getDragExpansion(pathBox, random, true, minExpansion, maxExpansion), 0, Math.min(startPos.getZ(), endPos.getZ()) + random.nextInt(pathBox.getZSpan() - 1) + getDragExpansion(pathBox, random, false, minExpansion, maxExpansion));
        BlockPos drag2 = new BlockPos(Math.min(startPos.getX(), endPos.getX()) + random.nextInt(pathBox.getXSpan() - 1) + getDragExpansion(pathBox, random, true, minExpansion, maxExpansion), 0, Math.min(startPos.getZ(), endPos.getZ()) + random.nextInt(pathBox.getZSpan() - 1) + getDragExpansion(pathBox, random, false, minExpansion, maxExpansion));

        BlockPos randomDrag1 = getDragPos(random, range, startPos, endPos);
        BlockPos randomDrag2 = getDragPos(random, range, startPos, endPos);

        return nodesDeCastelJusAlgList(startPos, endPos, drag1, drag2);
    }


    public static Direction[] availableDirections(MutableBoundingBox box) {
        if (box.getXSpan() < box.getZSpan()) {
            return new Direction[]{Direction.SOUTH, Direction.NORTH};
        } else {
            return new Direction[]{Direction.EAST, Direction.WEST};
        }
    }

    public static int getDragExpansion(MutableBoundingBox pathBox, Random random, boolean isXAxis, int minExpansion, int maxExpansion) {
        Direction[] directions = availableDirections(pathBox);

        if (!isXAxis && Arrays.stream(directions).anyMatch(direction -> direction == Direction.SOUTH || direction == Direction.NORTH)) {
            return random.nextInt(maxExpansion - minExpansion + 1) + minExpansion;
        } else if (isXAxis && Arrays.stream(directions).anyMatch(direction -> direction == Direction.EAST || direction == Direction.WEST)){
            return random.nextInt(maxExpansion - minExpansion + 1) + minExpansion;
        } else {
            return 0;
        }
    }


    private static BlockPos getRandomDeCastalBlockPos(Random random, int range, BlockPos startPos, BlockPos endPos, double v) {
        return deCastelJustAlgPos(startPos, endPos, new BlockPos(random.nextInt(range - 25), 0, random.nextInt(range - 25)), new BlockPos(random.nextInt(range - 25), 0, random.nextInt(range - 25)), v);
    }

    public static int getColor(int x, int z, int startX, int startZ, int endX, int endZ) {
        if (SectionPos.blockToSectionCoord(x) == startX && SectionPos.blockToSectionCoord(z) == startZ) {
            return new Color(0, 0, 255).getRGB();
        } else if (SectionPos.blockToSectionCoord(x) == endX && SectionPos.blockToSectionCoord(z) == endZ) {
            return new Color(0, 255, 255).getRGB();
        } else {
            return 0;
        }
    }

    public static List<BlockPos> nodesLerp(BlockPos start, BlockPos end) {
        ArrayList<BlockPos> nodes = new ArrayList<>();

        for (double lerp = 0; lerp < 1.0; lerp += 0.001) {
            nodes.add(getLerpedBlockPos(start, end, lerp));
        }
        return nodes;
    }

    private static BlockPos getLerpedBlockPos(BlockPos start, BlockPos end, double lerp) {
        return new BlockPos(MathHelper.lerp(lerp, start.getX(), end.getX()), 0, MathHelper.lerp(lerp, start.getZ(), end.getZ()));
    }

    public static List<BlockPos> nodesDeCastelJusAlgList(BlockPos start, BlockPos end, BlockPos drag, BlockPos drag2) {
        ArrayList<BlockPos> nodes = new ArrayList<>();

        for (double lerp = 0; lerp < 1.0; lerp += 0.001) {
            BlockPos result = deCastelJustAlgPos(start, end, drag, drag2, lerp);
            nodes.add(result);

        }

        return nodes;
    }

    public static BlockPos getDragPos(Random random, int range, BlockPos startPos, BlockPos endPos) {
        return new BlockPos(random.nextInt(range - 25), 0, random.nextInt(range - 25));
    }


    private static MutableBoundingBox pathBox(BlockPos startPos, BlockPos endPos) {
        int startPosX = startPos.getX();
        int startPosZ = startPos.getZ();

        int endPosX = endPos.getX();
        int endPosZ = endPos.getZ();

        return new MutableBoundingBox(Math.min(startPosX, endPosX), 0, Math.min(startPosZ, endPosZ), Math.max(startPosX, endPosX), 0, Math.max(startPosZ, endPosZ));
    }


    private static BlockPos deCastelJustAlgPos(BlockPos start, BlockPos end, BlockPos drag, BlockPos drag2, double lerp) {
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
}
