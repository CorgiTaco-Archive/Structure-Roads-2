package corgitaco.modid.core;

import com.mojang.serialization.Lifecycle;
import corgitaco.modid.Main;
import corgitaco.modid.world.path.PathGeneratorType;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.MutableRegistry;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.SimpleRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class Registries {
    private static final Map<ResourceLocation, Supplier<?>> LOADERS = new HashMap<>();
    private static final MutableRegistry<MutableRegistry<?>> WRITABLE_REGISTRY = new SimpleRegistry<>(RegistryKey.createRegistryKey(new ResourceLocation("root")), Lifecycle.experimental());
    private static final Logger LOGGER = LogManager.getLogger();

    public static final RegistryKey<Registry<PathGeneratorType<?>>> PATH_GENERATOR_TYPE_REGISTRY = createRegistryKey("path_generator_type");

    public static final Registry<PathGeneratorType<?>> PATH_GENERATOR_TYPE = registerRegistry(PATH_GENERATOR_TYPE_REGISTRY, () -> PathGeneratorType.PATHFINDING_PATH_GENERATOR);

    private static <T> RegistryKey<Registry<T>> createRegistryKey(String name){
        return RegistryKey.createRegistryKey(new ResourceLocation(Main.MOD_ID, name));
    }
    private static <T> Registry<T> registerRegistry(RegistryKey<? extends Registry<T>> key, Supplier<T> supplier){
        return registerRegistry(key, Lifecycle.experimental(), supplier);
    }
    private static <T> Registry<T> registerRegistry(RegistryKey<? extends Registry<T>> key, Lifecycle lifecycle, Supplier<T> supplier){
        return registerRegistry(key, new SimpleRegistry<>(key, lifecycle), lifecycle, supplier);
    }
    private static <T, R extends MutableRegistry<T>> Registry<T> registerRegistry(RegistryKey<? extends Registry<T>> key, R registry, Lifecycle lifecycle, Supplier<T> supplier){
        ResourceLocation location = key.location();
        LOADERS.put(location, supplier);
        MutableRegistry<R> mutableRegistry = (MutableRegistry<R>) WRITABLE_REGISTRY;
        return mutableRegistry.register((RegistryKey) key, registry, lifecycle);
    }

    static {
        LOADERS.forEach((key, value) -> {
            if(value.get() == null){
                LOGGER.error("Unable to bootstrap registry '{}'", key);
            }
        });

        Registry.checkRegistry(WRITABLE_REGISTRY);
    }
}
