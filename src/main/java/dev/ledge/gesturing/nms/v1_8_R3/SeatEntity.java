package dev.ledge.gesturing.nms.v1_8_R3;

import dev.ledge.gesturing.GesturingPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Invisible rideable seat. Behavior adapted from GSit (GPL-3.0-only). 1.8 has no safe NMS hook for the seat tick,
 * so this wraps a Bukkit armor stand and drives its per-tick callback with a repeating task.
 */
public final class SeatEntity {
    private final Location location;
    private ArmorStand stand;
    private Runnable runnable;
    private BukkitTask task;

    public SeatEntity(Location location) { this.location = location.clone(); }

    public void setRunnable(Runnable runnable) { this.runnable = runnable; }

    public boolean spawn(World world) {
        stand = world.spawn(location, ArmorStand.class);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setMarker(true);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setCustomName("gesturing_seat");
        GesturingPlugin plugin = GesturingPlugin.getPlugin(GesturingPlugin.class);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!alive()) { cancel(); return; }
            if (runnable != null) runnable.run();
            if (stand.getPassenger() != null) {
                Location rider = stand.getPassenger().getLocation();
                Location facing = stand.getLocation();
                facing.setYaw(rider.getYaw());
                stand.teleport(facing);
                stand.setPassenger(stand.getPassenger());
            }
        }, 1L, 1L);
        return true;
    }

    public boolean mount(Player rider) { return stand != null ? stand.setPassenger(rider) : pending(rider); }

    private Player pendingRider;
    private boolean pending(Player rider) { pendingRider = rider; return true; }

    public boolean mountOn(Player target) { return target.setPassenger(standOrSpawn()); }

    private ArmorStand standOrSpawn() {
        if (stand == null) spawn(location.getWorld());
        return stand;
    }

    public static void dismount(Player rider) {
        if (rider.getVehicle() != null) rider.leaveVehicle();
    }

    public void dismountSelf() { if (stand != null && stand.getVehicle() != null) stand.leaveVehicle(); }
    public boolean alive() { return stand != null && !stand.isDead() && stand.isValid(); }
    public Location location() { return stand == null ? location : stand.getLocation(); }
    public void remove() { cancel(); if (stand != null) stand.remove(); }
    private void cancel() { if (task != null) { task.cancel(); task = null; } }
}
