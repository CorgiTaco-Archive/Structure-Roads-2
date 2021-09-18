package corgitaco.modid.mixin;

import corgitaco.modid.core.StructureRegionManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.DimensionType;
import net.minecraft.world.World;
import net.minecraft.world.chunk.listener.IChunkStatusListener;
import net.minecraft.world.gen.ChunkGenerator;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.spawner.ISpecialSpawner;
import net.minecraft.world.storage.IServerWorldInfo;
import net.minecraft.world.storage.SaveFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.Executor;

@Mixin(ServerWorld.class)
public class MixinServerWorld implements StructureRegionManager.Access {

    private StructureRegionManager pathContext;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void attachPathContext(MinecraftServer server, Executor executor, SaveFormat.LevelSave save, IServerWorldInfo worldInfo, RegistryKey<World> key, DimensionType dimensionType, IChunkStatusListener chunkStatusListener, ChunkGenerator generator, boolean debug, long seed, List<ISpecialSpawner> specialSpawners, boolean e, CallbackInfo ci) {
        this.pathContext = new StructureRegionManager((ServerWorld) (Object) this);
    }

    @Override
    public StructureRegionManager getStructureRegionManager() {
        return pathContext;
    }
}
