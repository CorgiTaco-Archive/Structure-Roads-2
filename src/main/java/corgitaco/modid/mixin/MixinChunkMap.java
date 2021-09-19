package corgitaco.modid.mixin;

import com.mojang.datafixers.util.Either;
import corgitaco.modid.core.StructureData;
import corgitaco.modid.core.StructureRegion;
import corgitaco.modid.core.StructureRegionManager;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkManager.class)
public class MixinChunkMap {

    @Shadow @Final private ServerWorld level;

    @Inject(method = "scheduleChunkLoad", at = @At("HEAD"), cancellable = true)
    private void loadStructureRegions(ChunkPos pos, CallbackInfoReturnable<CompletableFuture<Either<IChunk, ChunkHolder.IChunkLoadingError>>> cir) {
        long chunkPos = pos.toLong();
        StructureRegionManager structureRegionManager = ((StructureRegionManager.Access) this.level).getStructureRegionManager();
        StructureRegion structureRegion = structureRegionManager.getStructureRegion(StructureRegionManager.chunkToRegionKey(chunkPos));
        structureRegion.getLoadedChunks().add(chunkPos);
    }

    @SuppressWarnings("UnresolvedMixinReference")
    @Inject(method = "lambda$scheduleUnload$10(Lnet/minecraft/world/server/ChunkHolder;Ljava/util/concurrent/CompletableFuture;JLnet/minecraft/world/chunk/IChunk;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/server/ChunkManager;save(Lnet/minecraft/world/chunk/IChunk;)Z", shift = At.Shift.BEFORE))
    private void unloadStructureRegions(ChunkHolder holder, CompletableFuture<IChunk> chunkFuture, long chunkPos, IChunk chunk, CallbackInfo ci) {
        StructureRegionManager structureRegionManager = ((StructureRegionManager.Access) this.level).getStructureRegionManager();
        long regionKey = StructureRegionManager.chunkToRegionKey(chunkPos);
        StructureRegion structureRegion = structureRegionManager.getStructureRegion(regionKey);


        structureRegion.getLoadedChunks().remove(chunkPos);
        if (structureRegion.getLoadedChunks().size() == 0) {
            structureRegionManager.unloadStructureRegion(regionKey);
            for (StructureData value : structureRegion.getDataByStructure().values()) {
                for (long neighborKey : value.getPathGeneratorReferences().keySet()) {
                    StructureRegion neighborStructureRegion = structureRegionManager.getStructureRegion(neighborKey);

                    // If this neighbor has no loaded chunks... drop it.
                    if (neighborStructureRegion.getLoadedChunks().size() == 0) {
                        structureRegionManager.unloadStructureRegion(neighborKey);
                    }
                }
            }
        }
    }
}
