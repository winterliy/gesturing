package dev.ledge.gesturing.nms.v1_14_R1;

import net.minecraft.server.v1_14_R1.DamageSource;
import net.minecraft.server.v1_14_R1.Entity;
import net.minecraft.server.v1_14_R1.EntityArmorStand;
import net.minecraft.server.v1_14_R1.EnumMoveType;
import net.minecraft.server.v1_14_R1.Vec3D;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_14_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_14_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * Copyright (C) Gecolay and contributors.
 * Adapted from GSit 3.8.0 (GPL-3.0-only), SeatEntity, for Spigot-mapped 1.16.5 (v1_14_R1).
 */
public final class SeatEntity extends EntityArmorStand {
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
        setBasePlate(false);
        setLocation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        addScoreboardTag("gesturing_seat");
    }

    @Override public void tick() {
        super.tick();
        if (!isAlive() || !valid) return;
        if (runnable != null) runnable.run();
        if (!passengers.isEmpty()) {
            Entity rider = passengers.get(0);
            yaw = rider.yaw;
            lastYaw = yaw;
        }
    }

    @Override public void move(EnumMoveType type, Vec3D movement) { }
    @Override public boolean damageEntity(DamageSource source, float amount) { return false; }

    // -- adapter API used by SeatManager (keeps NMS types out of version-neutral code) --
    public boolean mount(Player rider) { return ((CraftPlayer) rider).getHandle().startRiding(this); }
    public boolean mountOn(Player target) { return startRiding(((CraftPlayer) target).getHandle()); }
    public boolean spawn(org.bukkit.World world) { return ((CraftWorld) world).getHandle().addEntity(this, CreatureSpawnEvent.SpawnReason.CUSTOM); }
    public static void dismount(Player rider) {
        Entity handle = ((CraftPlayer) rider).getHandle();
        if (handle.isPassenger()) handle.stopRiding();
    }
    public void dismountSelf() { stopRiding(); }
    public boolean alive() { return isAlive(); }
    public void remove() { die(); }
}
