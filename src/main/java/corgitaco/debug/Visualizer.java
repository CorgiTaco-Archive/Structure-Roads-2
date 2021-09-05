package corgitaco.debug;

import net.minecraft.util.Direction;
import net.minecraft.util.math.*;
import net.minecraft.util.math.vector.Vector2f;
import org.lwjgl.system.CallbackI;

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
    static long startTime;

    private static final double WINDINESS = 1; //How windy it is (Doesn't affect much if above PI)
    private static final boolean SHOW_CONTROLS = false;

    public static void main(String[] args) {
        startTime = System.currentTimeMillis();
        smoothTest();
        long endTime = System.currentTimeMillis();

        //It seems ~1000ms is spent loading BlockPos
        System.out.println("Completed in " + (endTime - startTime) + " ms");
    }

    private static class PointWithGradient{
        private final BlockPos pos;
        private final Vec2 gradient;

        public PointWithGradient(BlockPos pos, Vec2 gradient){
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
    private static class Vec2{
        private final double x, y;

        public Vec2(double x, double y){
            this.x = x;
            this.y = y;
        }

        public Vec2 mult(double n){
            return new Vec2(x * n, y * n);
        }

        public BlockPos add(BlockPos pos){
            return new BlockPos(pos.getX() + x, pos.getY(), pos.getZ() + y);
        }

        public BlockPos addBackwards(BlockPos pos){
            return new BlockPos(pos.getX() - x, pos.getY(), pos.getZ() - y);
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }
    }

    public static void smoothTest(){
        Random random = new Random();

        int range = 1000;
        MutableBoundingBox image = new MutableBoundingBox(0, 0, 0, range - 1, 0, range - 1);

        BufferedImage img = new BufferedImage(range, range, BufferedImage.TYPE_INT_RGB);
        String pathname = "run\\yeet.png";
        File file = new File(pathname);
        if (file.exists())
            file.delete();

        BlockPos startPos = new BlockPos(random.nextInt(range / 2), 0, random.nextInt(range / 2));
        BlockPos endPos = new BlockPos(random.nextInt(range / 2) + range / 2, 0, random.nextInt(range / 2) + range / 2);

        List<PointWithGradient> points = new ArrayList<>();
        int pointCount = 7;
        double angle = Math.atan2(endPos.getZ() - startPos.getZ(), endPos.getX() - startPos.getX());
        double dist = Math.sqrt(
                (startPos.getX() - endPos.getX()) * (startPos.getX() - endPos.getX()) +
                (startPos.getZ() - endPos.getZ()) * (startPos.getZ() - endPos.getZ())
        );
        double scale = 0.5 * dist / pointCount;

        System.out.println("Start: " + startPos);
        System.out.println("End: " + endPos);
        System.out.println("Length: " + dist);
        System.out.println("Scale: " + scale);

        for(int point = 0; point < pointCount; point++){
            double t = (double) (point) / (pointCount - 1);
            BlockPos pos = getLerpedBlockPos(startPos, endPos, t);

            if(point != 0 || point != pointCount - 1) {
                double shiftAngle = random.nextDouble() * Math.PI * 2;
                double shiftLength = dist * 0.25 / pointCount;

                pos = pos.east((int) (Math.cos(shiftAngle) * shiftLength)).south((int) (Math.sin(shiftAngle) * shiftLength));

                if(pos.getX() < 0){
                    //For some reason there's no setX()
                    pos = pos.east(-pos.getX());
                }else if(pos.getX() >= range){
                    pos = pos.west(pos.getX() - range + 1);
                }

                if(pos.getZ() < 0){
                    pos = pos.south(-pos.getZ());
                }else if(pos.getZ() >= range){
                    pos = pos.north(pos.getZ() - range + 1);
                }
            }

            double angleAtPoint = angle + (random.nextDouble() * 2 - 1) * WINDINESS;
            Vec2 normalizedVector = new Vec2(Math.cos(angleAtPoint), Math.sin(angleAtPoint));
            double length = (random.nextDouble() + 1) * scale;

            points.add(new PointWithGradient(pos, normalizedVector.mult(length)));
        }

        int rgb = new Color(24, 154, 25).getRGB();

        for(int i = 0; i < pointCount - 1; i++){
            for(BlockPos pos : getBezierPoints(points.get(i), points.get(i + 1))){
                if(pos.getX() < 0 || pos.getZ() < 0 || pos.getX() >= range || pos.getZ() >= range)
                    continue;
                img.setRGB(pos.getX(), pos.getZ(), rgb);
            }
        }

        int pointColor = new Color(186, 55, 13).getRGB();
        int controlColor = new Color(197, 32, 76).getRGB();

        for(PointWithGradient point : points){
            //Draw points and control points
            int X = point.getPos().getX();
            int Z = point.getPos().getZ();

            drawSquare(3, X, Z, img, pointColor);

            if(SHOW_CONTROLS) {
                BlockPos controlOne = point.getGradient().add(point.getPos());
                BlockPos controlTwo = point.getGradient().addBackwards(point.getPos());

                drawSquare(2, controlOne.getX(), controlOne.getZ(), img, controlColor);
                drawSquare(2, controlTwo.getX(), controlTwo.getZ(), img, controlColor);

                int maxDiff = Math.max(Math.abs(controlOne.getX() - controlTwo.getX()), Math.abs(controlOne.getZ() - controlTwo.getZ())) + 1;
                double step = 1 / (double) maxDiff;

                for (double t = 0; t <= 1; t += step) {
                    BlockPos pos = getLerpedBlockPos(controlOne, controlTwo, t);
                    if (pos.getX() < 0 || pos.getZ() < 0 || pos.getX() >= range || pos.getZ() >= range)
                        continue;
                    img.setRGB(pos.getX(), pos.getZ(), controlColor);
                }
            }
        }

        try {
            file = new File(pathname);
            ImageIO.write(img, "png", file);
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    private static void drawSquare(int halfWidth, int X, int Z, BufferedImage img, int color){
        for(int x = Math.max(0, X - 3); x < Math.min(img.getWidth() - 1, X + 3); x++){
            for(int z = Math.max(0, Z - 3); z < Math.min(img.getHeight() - 1, Z + 3); z++) {
                img.setRGB(x, z, color);
            }
        }
    }

    private static List<BlockPos> getBezierPoints(PointWithGradient start, PointWithGradient end){
        BlockPos controlOne = start.getGradient().add(start.getPos());
        BlockPos controlTwo = end.getGradient().addBackwards(end.getPos());

        List<BlockPos> points = new ArrayList<>();

        for(double t = 0; t <= 1; t += 0.001){
            points.add(deCastelJustAlgPos(start.getPos(), end.getPos(), controlOne, controlTwo, t));
        }

        return points;
    }

    private static BlockPos getLerpedBlockPos(BlockPos start, BlockPos end, double lerp) {
        return new BlockPos(MathHelper.lerp(lerp, start.getX(), end.getX()), 0, MathHelper.lerp(lerp, start.getZ(), end.getZ()));
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
