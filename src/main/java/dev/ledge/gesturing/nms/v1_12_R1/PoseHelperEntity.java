package dev.ledge.gesturing.nms.v1_12_R1;

import java.lang.reflect.Field;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityAreaEffectCloud;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;

/**
 * Copyright (C) Gecolay and contributors.
 * Adapted from GSit 3.8.0 (GPL-3.0-only), PlayerSitEntity, for Spigot-mapped 1.16.5.
 */
public final class PoseHelperEntity extends EntityAreaEffectCloud {
    private static Field vehicleField;

    public PoseHelperEntity(Location location) {
        super(((CraftWorld) location.getWorld()).getHandle(), location.getX(), location.getY(), location.getZ());
        setRadius(0);
        setDuration(Integer.MAX_VALUE);
        setNoGravity(true);
        setInvulnerable(true);
        addScoreboardTag("gesturing_pose_helper");
    }

    private static synchronized Field vehicleField() {
        if (vehicleField == null) {
            try {
                // Spigot 1.12 keeps the obfuscated name ("au"); the only instance field of type Entity is the vehicle.
                for (Field field : Entity.class.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getType() != Entity.class) continue;
                    field.setAccessible(true);
                    vehicleField = field;
                    break;
                }
                if (vehicleField == null) throw new NoSuchFieldException("Entity vehicle field");
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
        return vehicleField;
    }

    @Override public void B_() { }

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
