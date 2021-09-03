package corgitaco.debug;

import net.minecraft.util.math.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Visualizer {

    public static void main(String[] args) {
        long seed = 3293193833203L;
        Random random = new Random(seed);
        int range = 1000;
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

        int pointCount = 10;

//        for (int point = 0; point <= pointCount - 1; point++) {
//            double lerp = (double) (point) / pointCount;
//            double nextLerp = (double) (point + 1) / pointCount;
//            BlockPos initial = getLerpedBlockPos(startPos, endPos, lerp);
//            BlockPos end = getLerpedBlockPos(startPos, endPos, nextLerp);
//            pathNodes.addAll(getRandomDraggedDeCastelJusAlgNodes(random, range, initial, end));
//        }

       pathNodes.addAll(getRandomDraggedDeCastelJusAlgNodes(random, range, startPos, endPos));

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
            img.setRGB(node.getX(), node.getZ(), rgb);
        }


        try {
            file = new File(pathname);
            ImageIO.write(img, "png", file);
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    private static List<BlockPos> getRandomDraggedDeCastelJusAlgNodes(Random random, int range, BlockPos startPos, BlockPos endPos) {
        return nodesDeCastelJusAlgList(startPos, endPos, getDragPos(random, range, startPos, endPos), getDragPos(random, range, startPos, endPos));
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


    private static MutableBoundingBox pathBox(BlockPos startPos, BlockPos endPos, Random random, int min, int max) {
        int startPosX = startPos.getX();
        int startPosZ = startPos.getZ();

        int endPosX = endPos.getX();
        int endPosZ = endPos.getZ();

        boolean flipX = startPosX > endPosX;
        boolean flipZ = startPosZ > endPosZ;
        MutableBoundingBox structureBox = new MutableBoundingBox(flipX ? endPosX : startPosX, 0, flipZ ? endPosZ : startPosZ, flipX ? startPosX : endPosZ, 0, flipZ ? startPosZ : endPosZ);

        MutableBoundingBox pathBox;

        int bound = max - min + 1;
        if (structureBox.getXSpan() > structureBox.getZSpan()) {
            pathBox = new MutableBoundingBox(structureBox.x0 - random.nextInt(bound) - min, 0, structureBox.z0, structureBox.x1 + random.nextInt(bound) + min, 0, structureBox.z1);
        } else if (structureBox.getZSpan() > structureBox.getXSpan()) {
            pathBox = new MutableBoundingBox(structureBox.x0, 0, structureBox.z0 - random.nextInt(bound) - min, structureBox.x1, 0, structureBox.z1 + random.nextInt(bound) + min);
        } else {
            pathBox = structureBox;
        }
        return pathBox;
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
