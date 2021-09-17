package corgitaco.modid.util;

import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.gen.Heightmap;

public class DataForChunk {

    private final Biome biome;
    private int height;

    public DataForChunk(Biome biome) {
        this(biome, Integer.MIN_VALUE);
    }

    public DataForChunk(Biome biome, int height) {
        this.biome = biome;
        this.height = height;
    }

    public Biome getBiome() {
        return biome;
    }

    public int getHeight(ChunkGenerator generator, int x, int z) {
        if (height == Integer.MIN_VALUE) {
            this.height = generator.getBaseHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG);
        }
        return height;
    }
}