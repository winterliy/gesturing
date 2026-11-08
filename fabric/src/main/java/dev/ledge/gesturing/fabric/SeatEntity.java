package dev.ledge.gesturing.fabric;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/** Invisible marker seat entity; behavior adapted from GSit 3.8.0 (GPL-3.0-only). */
final class SeatEntity extends ArmorStand {
    SeatEntity(ServerLevel level, double x, double y, double z) {
        super(level, x, y, z);
        setNoGravity(true);
        setNoBasePlate(true);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public void move(MoverType type, Vec3 movement) { }
}
