package corgitaco.modid.commands;


import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import corgitaco.debug.Visualizer;
import corgitaco.modid.core.StructureData;
import corgitaco.modid.core.StructureRegion;
import corgitaco.modid.core.StructureRegionManager;
import corgitaco.modid.world.path.IPathGenerator;
import corgitaco.modid.world.path.PathfindingPathGenerator;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.loading.FMLPaths;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Random;
import java.util.Set;

import static corgitaco.modid.core.StructureRegionManager.*;

public class DebugPathRegion {


    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("getRegionPaths").executes(cs -> paintPathRegion(cs.getSource()));
    }

    public static int paintPathRegion(CommandSource source) {
        long prevTime = System.currentTimeMillis();


        ServerWorld level = source.getLevel();

        StructureRegionManager structureRegionManager = ((StructureRegionManager.Access) level).getStructureRegionManager();


        Vector3d position = source.getPosition();
        int currentChunkX = SectionPos.blockToSectionCoord((int) position.x);
        int currentChunkZ = SectionPos.blockToSectionCoord((int) position.z);
        int regionX = chunkToRegion(currentChunkX);
        int regionZ = chunkToRegion(currentChunkZ);


        int searchRange = 1;

        int minSearchRangeRegionX = regionX - searchRange;
        int minSearchRangeRegionZ = regionZ - searchRange;

        int maxSearchRangeRegionX = regionX + searchRange;
        int maxSearchRangeRegionZ = regionZ + searchRange;


        int minSearchChunkX = regionToChunk(minSearchRangeRegionX);
        int minSearchChunkZ = regionToChunk(minSearchRangeRegionZ);

        int maxChunkX = regionToMaxChunk(maxSearchRangeRegionX);
        int maxChunkZ = regionToMaxChunk(maxSearchRangeRegionZ);

        int xLengthChunks = maxChunkX - minSearchChunkX;
        int zLengthChunks = maxChunkZ - minSearchChunkZ;

        int range = SectionPos.sectionToBlockCoord(xLengthChunks) + 16;
        BufferedImage image = new BufferedImage(range, range, BufferedImage.TYPE_INT_RGB);

        int searchRegionBlockMinX = regionToBlock(minSearchRangeRegionX);
        int searchRegionBlockMinZ = regionToBlock(minSearchRangeRegionZ);

        int drawX = (int) position.x - searchRegionBlockMinX;
        int drawZ = (int) position.z - searchRegionBlockMinZ;


        Visualizer.drawSquare(drawX, drawZ, image, new Color(255, 255, 255).getRGB(), 25);

        //Draw axes to make it easy to locate things
        //X-axis
        Graphics g = image.getGraphics();


        int lineDrawX = drawX % 100;
        int lineDrawZ = drawZ % 100;

        while (lineDrawX < range) {
            String s = String.valueOf(lineDrawX + searchRegionBlockMinX);
            g.setColor(Color.GRAY);
            g.drawLine(lineDrawX, 0, lineDrawX, range);
            g.setColor(Color.WHITE);
            g.drawLine(lineDrawX, drawZ - 20, lineDrawX, drawZ + 20);
            g.drawString(s, lineDrawX - g.getFontMetrics().stringWidth(s) / 2, drawZ - 40);
            lineDrawX += 100;
        }

        //Y/Z-Axis
        while (lineDrawZ < range) {
            String s = String.valueOf(lineDrawZ + searchRegionBlockMinZ);
            g.setColor(Color.GRAY);
            g.drawLine(0, lineDrawZ, range, lineDrawZ);
            g.setColor(Color.WHITE);
            g.drawLine(drawX - 20, lineDrawZ, drawX + 20, lineDrawZ);
            Rectangle2D bounds = g.getFontMetrics().getStringBounds(s, g);
            g.drawString(s, drawX + 25, (int) (lineDrawZ + bounds.getHeight() / 2));
            lineDrawZ += 100;
        }

        g.drawLine(0, drawZ, range, drawZ);
        g.drawLine(drawX, 0, drawX, range);

        int gridLineThickness = 10;

        for (int xSearch = minSearchRangeRegionX; xSearch <= maxSearchRangeRegionX; xSearch++) {
            for (int zSearch = minSearchRangeRegionZ; zSearch <= maxSearchRangeRegionZ; zSearch++) {
                int gridDrawX = regionToBlock(xSearch - minSearchRangeRegionX);
                int gridDrawZ = regionToBlock(zSearch - minSearchRangeRegionZ);
                g.setColor(Color.RED);

                for (int thickness = -gridLineThickness; thickness < gridLineThickness; thickness++) {
                    g.drawLine(gridDrawX + thickness, 0, gridDrawX + thickness, range);
                    g.drawLine(0, gridDrawZ + thickness, range, gridDrawZ+ thickness);
                }

                paintRegion(structureRegionManager, level, g, range, image, searchRegionBlockMinX, searchRegionBlockMinZ, xSearch, zSearch);
            }
        }

        File file = FMLPaths.GAMEDIR.get().resolve("yeet.png").toFile();
        if (file.exists())
            file.delete();

        try {
            file = new File(file.getAbsolutePath());
            ImageIO.write(image, "png", file);
            source.sendSuccess(new TranslationTextComponent("Finished processing debug image in: %sms", (System.currentTimeMillis() - prevTime)), true);
        } catch (IOException e) {
            System.out.println(e);
            return 0;
        }

        return 1;
    }

    private static void paintRegion(StructureRegionManager regionManager, ServerWorld world, Graphics g, int range, BufferedImage image, int searchRegionBlockMinX, int searchRegionBlockMinZ, int xSearch, int zSearch) {
        long regionKey = regionKey(xSearch, zSearch);
        StructureRegion structureRegion = regionManager.getStructureRegion(regionKey);
        StructureData structureData = structureRegion.structureData(Structure.VILLAGE);
        LongSet completedRegionStructureCachesForLevel = structureData.getLocationContextData(true).keySet();

        for (Long aLong : completedRegionStructureCachesForLevel) {
            paintChunk(range, searchRegionBlockMinX, searchRegionBlockMinZ, image, new Color(0, 200, 0).getRGB(), new ChunkPos(aLong));
        }


        Random random = new Random(regionKey);
        paintPathGenerators(g, image, searchRegionBlockMinX, searchRegionBlockMinZ, regionKey, structureData.getPathGenerators(true).values(), random);
        paintPathGenerators(g, image, searchRegionBlockMinX, searchRegionBlockMinZ, regionKey, structureData.getPathGeneratorNeighbors().values(), random);
    }

    private static void paintPathGenerators(Graphics g, BufferedImage image, int searchRegionBlockMinX, int searchRegionBlockMinZ, long regionKey, Collection<PathfindingPathGenerator> pathGenerators, Random random) {
        for (IPathGenerator<Structure<?>> pathGenerator : pathGenerators) {
            Color color = new Color(random.nextInt(251) + 5, random.nextInt(251) + 5, random.nextInt(251) + 5);
            int rgb = color.getRGB();

            Long2ReferenceOpenHashMap<Set<BlockPos>> blockPosList = pathGenerator.getNodesByRegion().get(regionKey);
            for (Set<BlockPos> value : blockPosList.values()) {
                for (BlockPos blockPos : value) {
                    int x = blockPos.getX() - searchRegionBlockMinX;
                    int z = blockPos.getZ() - searchRegionBlockMinZ;

                    Visualizer.drawSquare(x, z, image, rgb, 3);
                }
            }
            g.setColor(color);

            MutableBoundingBox bbox = pathGenerator.getBoundingBox();
            g.drawRect(bbox.x0 - searchRegionBlockMinX, bbox.z0 - searchRegionBlockMinZ, bbox.x1 - bbox.x0, bbox.z1 - bbox.z0);
        }
    }

    private static void paintChunk(int range, int searchRegionBlockMinX, int searchRegionBlockMinZ, BufferedImage image, int color, ChunkPos startChunkPos) {
        for (int x = startChunkPos.getMinBlockX(); x < startChunkPos.getMaxBlockX(); x++) {
            for (int z = startChunkPos.getMinBlockZ(); z < startChunkPos.getMaxBlockZ(); z++) {
                int drawX = x - searchRegionBlockMinX;
                int drawZ = z - searchRegionBlockMinZ;

                if (drawX > 0 && drawX < range && drawZ > 0 && drawZ < range) {
                    image.setRGB(drawX, drawZ, color);
                }
            }
        }
    }
}
