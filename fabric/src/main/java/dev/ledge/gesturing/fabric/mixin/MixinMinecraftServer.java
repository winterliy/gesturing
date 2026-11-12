package dev.ledge.gesturing.fabric.mixin;

import dev.ledge.gesturing.fabric.GesturingFabric;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** End-of-tick hook (no Legacy Fabric API on 1.5.2). */
@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {
    @Inject(method = "tick", at = @At("TAIL"))
    private void gesturing$tick(CallbackInfo ci) { GesturingFabric.tick((MinecraftServer) (Object) this); }
}
