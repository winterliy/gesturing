package dev.ledge.gesturing.fabric.mixin;

import dev.ledge.gesturing.fabric.GesturingFabric;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.13.2 has no Legacy Fabric API: hook the end of every server tick directly. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "method_20324", at = @At("TAIL"))
    private void gesturing$tick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        GesturingFabric.onServerTick((MinecraftServer) (Object) this);
    }
}
