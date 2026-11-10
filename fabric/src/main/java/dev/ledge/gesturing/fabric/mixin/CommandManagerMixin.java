package dev.ledge.gesturing.fabric.mixin;

import dev.ledge.gesturing.fabric.GesturingFabric;
import net.minecraft.server.command.CommandManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers the Brigadier commands once the vanilla dispatcher is built. */
@Mixin(CommandManager.class)
public abstract class CommandManagerMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void gesturing$register(boolean dedicated, CallbackInfo ci) {
        GesturingFabric.registerCommands(((CommandManager) (Object) this).method_17518());
    }
}
