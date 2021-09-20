package corgitaco.modid.util;

import com.mojang.serialization.Codec;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Util;
import net.minecraft.util.math.MutableBoundingBox;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.structure.Structure;

import java.util.stream.IntStream;

public class CodecUtil {
    public static final Codec<Structure<?>> STRUCTURE_CODEC = ResourceLocation.CODEC.xmap(Registry.STRUCTURE_FEATURE::get, Registry.STRUCTURE_FEATURE::getKey);

    public static final Codec<MutableBoundingBox> BOUNDING_BOX_CODEC = Codec.INT_STREAM.comapFlatMap((intStream) -> {
        return Util.fixedSize(intStream, 6).map((array) -> {
            return new MutableBoundingBox(array[0], array[1], array[2], array[3], array[4], array[5]);
        });
    }, (boundingBox) -> {
        return IntStream.of(boundingBox.x0, boundingBox.y0, boundingBox.z0, boundingBox.x1, boundingBox.y1, boundingBox.z1);
    }).stable();

    public static final Codec<RegistryKey<Biome>> BIOME_KEY = ResourceLocation.CODEC.xmap(resourceLocation -> RegistryKey.create(Registry.BIOME_REGISTRY, resourceLocation), RegistryKey::location);
}
