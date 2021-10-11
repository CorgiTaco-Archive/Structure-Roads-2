package corgitaco.debug;

import java.awt.image.BufferedImage;

public class Visualizer {
    /*
    static long startTime;

    private static final double WINDINESS = 2.5; //How windy it is (Doesn't affect much if above PI)
    private static final boolean SHOW_CONTROLS = false;

    public static void main(String[] args) {
        startTime = System.currentTimeMillis();
        smoothTest();
        long endTime = System.currentTimeMillis();

        //It seems ~1000ms is spent loading BlockPos
        System.out.println("Completed in " + (endTime - startTime) + " ms");
    }

    @SuppressWarnings("ConstantConditions")
    public static void smoothTest() {
        Random random = new Random(4857354985498458L);

        int range = 1000;

        BufferedImage img = new BufferedImage(range, range, BufferedImage.TYPE_INT_RGB);
        String pathname = "run\\yeet.png";
        File file = new File(pathname);
        if (file.exists())
            file.delete();

        BlockPos startPos = new BlockPos(random.nextInt(range / 2), 0, random.nextInt(range / 2));
        BlockPos endPos = new BlockPos(random.nextInt(range / 2) + range / 2, 0, random.nextInt(range / 2) + range / 2);

        //MutableBoundingBox pathBox = pathBox(startPos, endPos);

        drawSquare(startPos.getX(), startPos.getZ(), img, new Color(0, 100, 0).getRGB(), 10);
        drawSquare(endPos.getX(), endPos.getZ(), img, new Color(0, 100, 0).getRGB(), 10);


        int pointCount = 25;
        //List<PointWithGradient> points = getPointWithGradients(random, startPos, endPos, pathBox, pointCount, WINDINESS);

        //drawPoints(range, img, points);

        try {
            file = new File(pathname);
            ImageIO.write(img, "png", file);
        } catch (IOException e) {
            System.out.println(e);
        }
    }
    */
    public static void drawSquare(int X, int Z, BufferedImage img, int color, int size) {
        for (int x = Math.max(0, X - size); x < Math.min(img.getWidth() - 1, X + size); x++) {
            for (int z = Math.max(0, Z - size); z < Math.min(img.getHeight() - 1, Z + size); z++) {
                img.setRGB(x, z, color);
            }
        }
    }
    /*
    private static void drawPoints(int range, BufferedImage img, List<PointWithGradient> points) {
        int pointColor = new Color(186, 55, 13).getRGB();
        int controlColor = new Color(197, 32, 76).getRGB();
        int rgb = new Color(24, 154, 25).getRGB();

        for (int i = 0; i < points.size() - 1; i++) {
            for (BlockPos pos : getBezierPoints(points.get(i), points.get(i + 1), 0.001)) {
                if (pos.getX() < 0 || pos.getZ() < 0 || pos.getX() >= range || pos.getZ() >= range)
                    continue;
                drawSquare(pos.getX(), pos.getZ(), img, rgb, 3);
            }
        }

        for (PointWithGradient point : points) {
            //Draw points and control points
            int X = point.getPos().getX();
            int Z = point.getPos().getZ();

            drawSquare(X, Z, img, pointColor, 5);

            if (SHOW_CONTROLS) {
                BlockPos controlOne = point.getGradient().add(point.getPos());
                BlockPos controlTwo = point.getGradient().addBackwards(point.getPos());

                drawSquare(controlOne.getX(), controlOne.getZ(), img, controlColor, 5);
                drawSquare(controlTwo.getX(), controlTwo.getZ(), img, controlColor, 5);

                int maxDiff = Math.max(Math.abs(controlOne.getX() - controlTwo.getX()), Math.abs(controlOne.getZ() - controlTwo.getZ())) + 1;
                double step = 1 / (double) maxDiff;

                for (double t = 0; t <= 1; t += step) {
                    BlockPos pos = getLerpedBlockPos(controlOne, controlTwo, t);
                    if (pos.getX() < 0 || pos.getZ() < 0 || pos.getX() >= range || pos.getZ() >= range) {
                        continue;
                    }
                    img.setRGB(pos.getX(), pos.getZ(), controlColor);
                }
            }
        }
    }
    */
}
