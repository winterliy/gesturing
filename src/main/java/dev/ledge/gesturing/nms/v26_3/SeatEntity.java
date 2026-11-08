package dev.ledge.gesturing.nms.v26_3;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_19_R3.CraftWorld;
import org.jetbrains.annotations.NotNull;

/**
 * Copyright (C) Gecolay and contributors.
 * Adapted from GSit 3.8.0 (GPL-3.0-only), v26_3 SeatEntity.
 * Gesturing changes: isolated as a minimal command-driven seat entity.
 */
public final class SeatEntity extends ArmorStand {
    private Runnable runnable;

    public void setRunnable(Runnable runnable) { this.runnable = runnable; }

    public SeatEntity(Location location) {
        super(((CraftWorld) location.getWorld()).getHandle(), location.getX(), location.getY(), location.getZ());
        persist = false;
        setInvisible(true);
        setNoGravity(true);
        setMarker(true);
        setInvulnerable(true);
        setSmall(true);
        setNoBasePlate(true);
        setRot(location.getYaw(), location.getPitch());
        yRotO = getYRot();
        setYBodyRot(yRotO);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(1.0f);
        addTag("gesturing_seat");
    }

    @Override public void tick() {
        super.tick();
        if (!isAlive() || !valid) return;
        if (runnable != null) runnable.run();
        Entity rider = getFirstPassenger();
        if (rider != null) {
            setYRot(rider.getYRot());
            yRotO = getYRot();
        }
    }

    @Override public void move(@NotNull MoverType type, @NotNull Vec3 movement) { }
    @Override public boolean hurt(@NotNull DamageSource source, float amount) { return false; }
    @Override public boolean isAffectedByFluids() { return false; }
    @Override public boolean dismountsUnderwater() { return false; }
}
