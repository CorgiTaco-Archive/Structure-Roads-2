package corgitaco.modid.mixin.access;

import net.minecraft.world.gen.feature.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Structure.class)
public interface StructureAccess {

    @Invoker()
    boolean invokeLinearSeparation();
}