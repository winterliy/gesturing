package dev.ledge.gesturing.nms.v26_3;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld;

/**
 * Copyright (C) Gecolay and contributors.
 * Adapted from GSit 3.8.0 (GPL-3.0-only), v26_3 PlayerSitEntity.
 */
public final class PoseHelperEntity extends AreaEffectCloud {
    private static volatile Field vehicleField;

    public PoseHelperEntity(Location location) {
        super(((CraftWorld) location.getWorld()).getHandle(), location.getX(), location.getY(), location.getZ());
        persist = false;
        setRadius(0);
        setDuration(Integer.MAX_VALUE);
        setNoGravity(true);
        setInvulnerable(true);
        addTag("gesturing_pose_helper");
        if (vehicleField == null) resolveVehicleField();
    }

    private static synchronized void resolveVehicleField() {
        if (vehicleField != null) return;
        List<Field> candidates = new ArrayList<>();
        for (Field field : Entity.class.getDeclaredFields()) if (field.getType().equals(Entity.class)) candidates.add(field);
        Field field = candidates.get(0);
        field.setAccessible(true);
        vehicleField = field;
    }

    @Override public void tick() { }

    /** Links {@code passenger} to this entity without the server-side riding logic (packet-only use). */
    public void attachPassenger(Entity passenger) {
        if (vehicleField == null) resolveVehicleField();
        try { vehicleField.set(passenger, this); } catch (Throwable ignored) { }
        passengers = ImmutableList.of(passenger);
    }

    public void setVehicle(Entity vehicle) {
        try { vehicleField.set(this, vehicle); } catch (Throwable ignored) { }
        if (vehicle.passengers.isEmpty()) vehicle.passengers = ImmutableList.of(this);
        else {
            List<Entity> list = Lists.newArrayList(vehicle.passengers);
            list.add(this);
            vehicle.passengers = ImmutableList.copyOf(list);
        }
    }
}
