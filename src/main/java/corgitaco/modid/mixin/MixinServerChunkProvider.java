package corgitaco.modid.mixin;

import corgitaco.modid.core.StructureRegionManager;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkProvider.class)
public class MixinServerChunkProvider {


    @Shadow @Final public ServerWorld level;

    @Inject(method = "save", at = @At("HEAD"))
    private void saveAllStructureRegions(boolean p_217210_1_, CallbackInfo ci) {
        StructureRegionManager structureRegionManager = ((StructureRegionManager.Access) this.level).getStructureRegionManager();
        structureRegionManager.saveAllStructureRegions();
    }
}
