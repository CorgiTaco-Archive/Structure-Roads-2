package corgitaco.modid.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import corgitaco.modid.Main;
import corgitaco.modid.commands.DebugAvoidanceMap;
import corgitaco.modid.commands.DebugPathRegion;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Commands.class)
public abstract class MixinCommands {

    @Shadow
    @Final
    private CommandDispatcher<CommandSource> dispatcher;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void addEnhancedCelestialCommands(Commands.EnvironmentType envType, CallbackInfo ci) {
        LiteralArgumentBuilder<CommandSource> requires = Commands.literal(Main.MOD_ID).requires(commandSource -> commandSource.hasPermission(3));
        requires.then(DebugPathRegion.register(dispatcher));
        requires.then(DebugAvoidanceMap.register(dispatcher));
        dispatcher.register(requires);
    }
}