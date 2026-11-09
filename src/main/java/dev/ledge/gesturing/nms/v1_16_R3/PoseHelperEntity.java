package dev.ledge.gesturing.nms.v1_16_R3;

import java.lang.reflect.Field;
import net.minecraft.server.v1_16_R3.Entity;
import net.minecraft.server.v1_16_R3.EntityAreaEffectCloud;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;

/**
 * Copyright (C) Gecolay and contributors.
 * Adapted from GSit 3.8.0 (GPL-3.0-only), PlayerSitEntity, for Spigot-mapped 1.16.5.
 */
public final class PoseHelperEntity extends EntityAreaEffectCloud {
    private static Field vehicleField;

    public PoseHelperEntity(Location location) {
        super(((CraftWorld) location.getWorld()).getHandle(), location.getX(), location.getY(), location.getZ());
        persist = false;
        setRadius(0);
        setDuration(Integer.MAX_VALUE);
        setNoGravity(true);
        setInvulnerable(true);
        addScoreboardTag("gesturing_pose_helper");
    }

    private static synchronized Field vehicleField() {
        if (vehicleField == null) {
            try {
                vehicleField = Entity.class.getDeclaredField("vehicle");
                vehicleField.setAccessible(true);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        return vehicleField;
    }

    @Override public void tick() { }

    /** Links {@code passenger} to this entity without server-side riding logic (packet-only use). */
    public void attachPassenger(Entity passenger) {
        try { vehicleField().set(passenger, this); } catch (ReflectiveOperationException ignored) { }
        passengers.clear();
        passengers.add(passenger);
    }

    public void setVehicle(Entity vehicle) {
        try { vehicleField().set(this, vehicle); } catch (ReflectiveOperationException ignored) { }
        if (!vehicle.passengers.contains(this)) vehicle.passengers.add(this);
    }
}
