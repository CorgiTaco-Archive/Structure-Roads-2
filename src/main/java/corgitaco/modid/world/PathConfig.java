package corgitaco.modid.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import corgitaco.modid.util.CodecUtil;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.blockstateprovider.BlockStateProvider;
import net.minecraft.world.gen.feature.IFeatureConfig;
import net.minecraft.world.gen.feature.structure.Structure;

import java.util.Map;

public class PathConfig implements IFeatureConfig {

    public static final Codec<PathConfig> CODEC = RecordCodecBuilder.create((builder) -> {
        return builder.group(CodecUtil.STRUCTURE_CODEC.fieldOf("structure").forGetter(pathConfig -> {
            return pathConfig.structure;
        }), Codec.unboundedMap(Biome.Category.CODEC, BlockStateProvider.CODEC).fieldOf("biomeStateProviders").forGetter(pathConfig -> {
            return pathConfig.biomePathBlocks;
        }), BlockStateProvider.CODEC.fieldOf("default").forGetter(pathConfig -> {
            return pathConfig.defaultStateProvider;
        }), Codec.INT.fieldOf("pathSize").forGetter(pathConfig -> {
            return pathConfig.pathSize;
        }), ResourceLocation.CODEC.fieldOf("lightFilePath").forGetter(pathConfig -> {
            return pathConfig.lightFilePath;
        })).apply(builder, PathConfig::new);
    });

    private final Structure<?> structure;
    private final Map<Biome.Category, BlockStateProvider> biomePathBlocks;
    private final BlockStateProvider defaultStateProvider;
    private final int pathSize;
    private final ResourceLocation lightFilePath;

    public PathConfig(Structure<?> structure, Map<Biome.Category, BlockStateProvider> biomePathBlocks, BlockStateProvider defaultStateProvider, int pathSize, ResourceLocation lightFilePath) {
        this.structure = structure;
        this.biomePathBlocks = biomePathBlocks;
        this.defaultStateProvider = defaultStateProvider;
        this.pathSize = pathSize;
        this.lightFilePath = lightFilePath;
    }

    public Structure<?> getStructure() {
        return structure;
    }

    public Map<Biome.Category, BlockStateProvider> getBiomePathBlocks() {
        return biomePathBlocks;
    }

    public BlockStateProvider getDefaultStateProvider() {
        return defaultStateProvider;
    }

    public int getPathSize() {
        return pathSize;
    }

    public ResourceLocation getLightFilePath() {
        return lightFilePath;
    }
}
