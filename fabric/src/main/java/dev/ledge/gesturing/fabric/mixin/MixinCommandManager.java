package dev.ledge.gesturing.fabric.mixin;

import dev.ledge.gesturing.fabric.GesturingFabric;
import net.minecraft.server.command.CommandManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers /sit and /stand when the server builds its command manager (no Legacy Fabric API on 1.5.2). */
@Mixin(CommandManager.class)
public abstract class MixinCommandManager {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void gesturing$register(CallbackInfo ci) { GesturingFabric.registerCommands((CommandManager) (Object) this); }
}
