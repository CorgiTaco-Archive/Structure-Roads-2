package corgitaco.modid.util;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import corgitaco.modid.Main;
import corgitaco.modid.mixin.access.BiomeGenerationSettingsAccess;
import corgitaco.modid.world.PathConfig;
import corgitaco.modid.world.WorldPathGenerator;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import net.minecraft.block.Blocks;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.WorldGenRegistries;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biomes;
import net.minecraft.world.gen.GenerationStage;
import net.minecraft.world.gen.blockstateprovider.WeightedBlockStateProvider;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.IFeatureConfig;
import net.minecraft.world.gen.feature.structure.Structure;
import net.minecraft.world.gen.placement.NoPlacementConfig;
import net.minecraft.world.gen.placement.Placement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class BiomeUtils {

    public static final List<Feature<?>> FEATURES = new ArrayList<>();

    public static final Feature<PathConfig> PATH_GENERATOR = createFeature("path_generator", new WorldPathGenerator(PathConfig.CODEC));
    public static final ConfiguredFeature<?, ?> CONFIGURED_PATH_GENERATOR = createConfiguredFeature("path_generator", PATH_GENERATOR.configured(
            new PathConfig(Structure.VILLAGE, Util.make(new Object2ObjectArrayMap<>(), (map) -> {
                map.put(Biomes.BAMBOO_JUNGLE, new WeightedBlockStateProvider().add(Blocks.GRASS_PATH.defaultBlockState(), 5).add(Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2));
                map.put(Biomes.JUNGLE, new WeightedBlockStateProvider().add(Blocks.GRASS_PATH.defaultBlockState(), 5).add(Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2));
                map.put(Biomes.JUNGLE_EDGE, new WeightedBlockStateProvider().add(Blocks.GRASS_PATH.defaultBlockState(), 5).add(Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2));
                map.put(Biomes.DESERT, new WeightedBlockStateProvider().add(Blocks.COBBLESTONE.defaultBlockState(), 1).add(Blocks.SANDSTONE.defaultBlockState(), 4));
                map.put(Biomes.DESERT_HILLS, new WeightedBlockStateProvider().add(Blocks.COBBLESTONE.defaultBlockState(), 1).add(Blocks.SANDSTONE.defaultBlockState(), 4));
                map.put(Biomes.DESERT_LAKES, new WeightedBlockStateProvider().add(Blocks.COBBLESTONE.defaultBlockState(), 1).add(Blocks.SANDSTONE.defaultBlockState(), 4));
            }), new WeightedBlockStateProvider().add(Blocks.GRASS_PATH.defaultBlockState(), 5).add(Blocks.COARSE_DIRT.defaultBlockState(), 2), 5, new ResourceLocation(""))).decorated(Placement.NOPE.configured(NoPlacementConfig.INSTANCE)));

    public static void addFeaturesToBiomes(Biome biome, RegistryKey<Biome> biomeKey) {
        addFeatureToBiome(biome, GenerationStage.Decoration.RAW_GENERATION, CONFIGURED_PATH_GENERATOR);
    }

    public static <C extends IFeatureConfig, F extends Feature<C>> F createFeature(String id, F feature) {
        ResourceLocation bygID = new ResourceLocation(Main.MOD_ID, id);
        if (Registry.FEATURE.keySet().contains(bygID))
            throw new IllegalStateException("Feature ID: \"" + bygID.toString() + "\" already exists in the Features registry!");

//        Registry.register(Registry.FEATURE, bygID, feature);
        feature.setRegistryName(bygID); //Forge
        FEATURES.add(feature);
        return feature;
    }

    public static <FC extends IFeatureConfig, F extends Feature<FC>, CF extends ConfiguredFeature<FC, F>> CF createConfiguredFeature(String id, CF configuredFeature) {
        ResourceLocation bygID = new ResourceLocation(Main.MOD_ID, id);
        if (WorldGenRegistries.CONFIGURED_FEATURE.keySet().contains(bygID))
            throw new IllegalStateException("Configured Feature ID: \"" + bygID.toString() + "\" already exists in the Configured Features registry!");

        Registry.register(WorldGenRegistries.CONFIGURED_FEATURE, bygID, configuredFeature);
        return configuredFeature;
    }

    public static void addFeatureToBiome(Biome biome, GenerationStage.Decoration feature, ConfiguredFeature<?, ?> configuredFeature) {
        convertImmutableFeatures(biome);
        List<List<Supplier<ConfiguredFeature<?, ?>>>> biomeFeatures = ((BiomeGenerationSettingsAccess) biome.getGenerationSettings()).getFeatures();
        while (biomeFeatures.size() <= feature.ordinal()) {
            biomeFeatures.add(Lists.newArrayList());
        }
        biomeFeatures.get(feature.ordinal()).add(() -> configuredFeature);
    }

    private static void convertImmutableFeatures(Biome biome) {
        if (((BiomeGenerationSettingsAccess) biome.getGenerationSettings()).getFeatures() instanceof ImmutableList) {
            ((BiomeGenerationSettingsAccess) biome.getGenerationSettings()).setFeatures(((BiomeGenerationSettingsAccess) biome.getGenerationSettings()).getFeatures().stream().map(Lists::newArrayList).collect(Collectors.toList()));
        }
    }
}
