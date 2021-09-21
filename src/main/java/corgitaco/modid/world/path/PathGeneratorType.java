package corgitaco.modid.world.path;

import com.mojang.serialization.Codec;
import corgitaco.modid.Main;
import corgitaco.modid.core.Registries;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.gen.feature.structure.Structure;

public class PathGeneratorType<T extends IPathGenerator<Structure<?>>> {
    public static final PathGeneratorType<PathfindingPathGenerator> PATHFINDING_PATH_GENERATOR = register("pathfinding_path_generator", PathfindingPathGenerator.CODEC);

    private final Codec<T> codec;

    private PathGeneratorType(Codec<T> codec) {
        this.codec = codec;
    }

    private static <P extends IPathGenerator<Structure<?>>> PathGeneratorType<P> register(String name, Codec<P> codec){
        return Registry.register(Registries.PATH_GENERATOR_TYPE, new ResourceLocation(Main.MOD_ID, name), new PathGeneratorType<>(codec));
    }

    public Codec<T> getCodec() {
        return codec;
    }
}
