package corgitaco.modid.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.SectionPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class DebugAvoidanceMap {
    private static final Map<Biome, RegistryKey<Biome>> biomeRegistryKeyMap = new HashMap<>();

    public static ArgumentBuilder<CommandSource, ?> register(CommandDispatcher<CommandSource> dispatcher) {
        return Commands.literal("paintAvoidance").executes(cs -> paintAvoidance(cs.getSource()));
    }

    public static int paintAvoidance(CommandSource source){
        System.out.println("Painting Avoidance");
        ServerWorld world = source.getLevel();

        Vector3d position = source.getPosition();

        int currentChunkX = SectionPos.blockToSectionCoord((int) position.x);
        int currentChunkZ = SectionPos.blockToSectionCoord((int) position.z);

        int minSearchChunkX = currentChunkX - 200;
        int minSearchChunkZ = currentChunkZ - 200;

        int maxChunkX = currentChunkX + 200;
        int maxChunkZ = currentChunkZ + 200;

        int xSize = maxChunkX - minSearchChunkX + 1;
        int zSize = maxChunkZ - minSearchChunkZ + 1;
        BufferedImage image = new BufferedImage(xSize, zSize, BufferedImage.TYPE_INT_RGB);

        int rgb = Color.WHITE.getRGB();

        for(int chunkX = minSearchChunkX; chunkX <= maxChunkX; chunkX++){
            for(int chunkZ = minSearchChunkZ; chunkZ <= maxChunkZ; chunkZ++){
                if(!shouldAvoid(world, chunkX, chunkZ)){
                    image.setRGB(chunkX - minSearchChunkX, chunkZ - minSearchChunkZ, rgb);
                }
            }
        }

        File file = FMLPaths.GAMEDIR.get().resolve("avoid.png").toFile();
        if (file.exists())
            file.delete();

        try {
            file = new File(file.getAbsolutePath());
            ImageIO.write(image, "png", file);
            source.sendSuccess(new TranslationTextComponent("Created avoidance map"), true);
        } catch (IOException e) {
            System.out.println(e);
            source.sendFailure(new TranslationTextComponent("Failed to save map"));
            return 0;
        }

        return 1;
    }

    private static boolean shouldAvoid(ServerWorld world, int chunkX, int chunkZ){
        Biome biome = world.getBiome(new BlockPos(chunkX * 16 + 8, 60, chunkZ * 16 + 8));
        if(biome.getBiomeCategory().equals(Biome.Category.OCEAN))
            return true;
        return biome.getDepth() > 0.5f;
    }

    static {
        for(Map.Entry<RegistryKey<Biome>, Biome> entry : ForgeRegistries.BIOMES.getEntries()){
            biomeRegistryKeyMap.put(entry.getValue(), entry.getKey());
        }
    }
}
