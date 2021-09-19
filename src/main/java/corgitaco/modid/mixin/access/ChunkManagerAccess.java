package corgitaco.modid.mixin.access;

import net.minecraft.world.server.ChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.io.File;

@Mixin(ChunkManager.class)
public interface ChunkManagerAccess {

    @Accessor
    File getStorageFolder();
}