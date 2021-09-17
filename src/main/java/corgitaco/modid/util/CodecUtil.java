package corgitaco.modid.util;

import com.mojang.serialization.Codec;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.feature.structure.Structure;

public class CodecUtil {
    public static final Codec<Structure<?>> STRUCTURE_CODEC = ResourceLocation.CODEC.xmap(Registry.STRUCTURE_FEATURE::get, Registry.STRUCTURE_FEATURE::getKey);
}
