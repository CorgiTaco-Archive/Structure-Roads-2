package corgitaco.modid.mixin.access;

import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.concurrent.ExecutorService;

@Mixin(Util.class)
public interface UtilAccess {

    @Invoker
    static ExecutorService invokeMakeExecutor(String name) {
        throw new Error("Mixin did not apply!");
    }


}
