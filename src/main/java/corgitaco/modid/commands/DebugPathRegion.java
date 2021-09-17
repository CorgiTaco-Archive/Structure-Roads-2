package corgitaco.modid.commands;


import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import corgitaco.debug.Visualizer;
import corgitaco.modid.world.WorldPathGenerator;
import corgitaco.modid.world.path.IPathGenerator;
import corgitaco.modid.world.path.PathContext;
import corgitaco.modid.world.path.PathGenerator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import static corgitaco.modid.world.WorldPathGenerator.regionKey;

public class DebugPathRegion {


    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("getRegionPaths").executes(cs -> paintPathRegion(cs.getSource()));
    }

    public static int paintPathRegion(CommandSource source) {
        long prevTime = System.currentTimeMillis();


        ServerWorld level = source.getLevel();

        PathContext pathContext = ((PathContext.Access) level).getPathContext();


        Vector3d position = source.getPosition();
        int currentChunkX = SectionPos.blockToSectionCoord((int) position.x);
        int currentChunkZ = SectionPos.blockToSectionCoord((int) position.z);
        int regionX = WorldPathGenerator.chunkToRegion(currentChunkX);
        int regionZ = WorldPathGenerator.chunkToRegion(currentChunkZ);


        int searchRange = 1;

        int minSearchRangeRegionX = regionX - searchRange;
        int minSearchRangeRegionZ = regionZ - searchRange;

        int maxSearchRangeRegionX = regionX + searchRange;
        int maxSearchRangeRegionZ = regionZ + searchRange;


        int minSearchChunkX = WorldPathGenerator.regionToChunk(minSearchRangeRegionX);
        int minSearchChunkZ = WorldPathGenerator.regionToChunk(minSearchRangeRegionZ);

        int maxChunkX = WorldPathGenerator.regionToMaxChunk(maxSearchRangeRegionX);
        int maxChunkZ = WorldPathGenerator.regionToMaxChunk(maxSearchRangeRegionZ);

        int xLengthChunks = maxChunkX - minSearchChunkX;
        int zLengthChunks = maxChunkZ - minSearchChunkZ;

        int range = SectionPos.sectionToBlockCoord(xLengthChunks) + 16;
        BufferedImage image = new BufferedImage(range, range, BufferedImage.TYPE_INT_RGB);

        int searchRegionBlockMinX = WorldPathGenerator.regionToBlock(minSearchRangeRegionX);
        int searchRegionBlockMinZ = WorldPathGenerator.regionToBlock(minSearchRangeRegionZ);

        int drawX = (int) position.x - searchRegionBlockMinX;
        int drawZ = (int) position.z - searchRegionBlockMinZ;


        Visualizer.drawSquare(drawX, drawZ, image, new Color(255, 255, 255).getRGB(), 25);

        //Draw axes to make it easy to locate things
        //X-axis
        Graphics g = image.getGraphics();
        int lineDrawX = drawX % 100;
        int lineDrawZ = drawZ % 100;

        while(lineDrawX < range){
            String s = String.valueOf(lineDrawX + searchRegionBlockMinX);
            g.setColor(Color.GRAY);
            g.drawLine(lineDrawX, 0, lineDrawX, range);
            g.setColor(Color.WHITE);
            g.drawLine(lineDrawX, drawZ - 20, lineDrawX, drawZ + 20);
            g.drawString(s, lineDrawX - g.getFontMetrics().stringWidth(s) / 2, drawZ - 40);
            lineDrawX += 100;
        }

        //Y/Z-Axis
        while(lineDrawZ < range){
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

        for (int xSearch = minSearchRangeRegionX; xSearch <= maxSearchRangeRegionX; xSearch++) {
            for (int zSearch = minSearchRangeRegionZ; zSearch <= maxSearchRangeRegionZ; zSearch++) {
                long regionKey = regionKey(xSearch, zSearch);
                LongSet completedRegionStructureCachesForLevel = pathContext.getContextCacheForLevel().get(regionKey).keySet();

                for (Long aLong : completedRegionStructureCachesForLevel) {
                    paintChunk(range, searchRegionBlockMinX, searchRegionBlockMinZ, image, new Color(0, 200, 0).getRGB(), new ChunkPos(aLong));
                }

                List<IPathGenerator> pathGenerators = pathContext.getPathGenerators().get(regionKey);

                Random random = new Random(regionKey);
                for (IPathGenerator pathGenerator : pathGenerators) {
                    Color color = new Color(random.nextInt(251) + 5, random.nextInt(251) + 5, random.nextInt(251) + 5);
                    int rgb = color.getRGB();

//                    long startStructureChunk = pathGenerator.getStartStructureChunk();
//                    long endStructureChunk = pathGenerator.getEndStructureChunk();
//
//                    ChunkPos startChunkPos = new ChunkPos(startStructureChunk);
//                    ChunkPos endChunkPos = new ChunkPos(endStructureChunk);
//
//                    paintChunk(range, searchRegionBlockMinX, searchRegionBlockMinZ, image, color, startChunkPos);
//                    paintChunk(range, searchRegionBlockMinX, searchRegionBlockMinZ, image, color, endChunkPos);

                    for (List<BlockPos> value : pathGenerator.getNodesByChunk().values()) {
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
