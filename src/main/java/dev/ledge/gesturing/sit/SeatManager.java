package dev.ledge.gesturing.sit;

import dev.ledge.gesturing.nms.v1_16_R3.SeatEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class SeatManager {
    private final Map<UUID, SeatEntity> seats = new HashMap<>();
    private final Map<UUID, UUID> playerSeatTargets = new HashMap<>();
    private final Map<UUID, Location> seatLocations = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    public SeatEntity seatOf(Player player) { return seats.get(player.getUniqueId()); }
    public Location seatLocationOf(Player player) { return seatLocations.get(player.getUniqueId()); }

    public boolean sitOnSupportingBlock(Player player) { return sitOnSupportingBlock(player, player.getLocation().getYaw()); }

    public boolean sitOnSupportingBlock(Player player, float yaw) {
        stop(player);
        Location returnLocation = player.getLocation().clone();
        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        if (block.isEmpty() || block.isLiquid()) return false;
        SeatGeometry.Offset offset = SeatGeometry.offsetFor(block.getBlockData());
        boolean directional = block.getBlockData() instanceof Stairs || block.getBlockData() instanceof Directional;
        Location location = new Location(block.getWorld(), block.getX() + offset.x(), block.getY() + offset.y() - 0.025,
                block.getZ() + offset.z(), directional ? offset.yaw() : yaw, 0f);
        SeatEntity seat = new SeatEntity(location);
        if (!seat.mount(player)) {
            seat.remove();
            return false;
        }
        if (!seat.spawn(block.getWorld())) {
            SeatEntity.dismount(player);
            seat.remove();
            return false;
        }
        seats.put(player.getUniqueId(), seat);
        seatLocations.put(player.getUniqueId(), location);
        returnLocations.put(player.getUniqueId(), returnLocation);
        return true;
    }

    public boolean sitOnPlayer(Player rider, Player target) {
        stop(rider);
        SeatEntity seat = new SeatEntity(target.getLocation().add(0, 0.15, 0));
        if (!seat.mountOn(target) || !seat.mount(rider)) {
            SeatEntity.dismount(rider);
            seat.dismountSelf();
            seat.remove();
            return false;
        }
        if (!seat.spawn(target.getWorld())) {
            SeatEntity.dismount(rider);
            seat.dismountSelf();
            seat.remove();
            return false;
        }
        seats.put(rider.getUniqueId(), seat);
        playerSeatTargets.put(rider.getUniqueId(), target.getUniqueId());
        return true;
    }

    public void stop(Player player) {
        SeatEntity seat = seats.remove(player.getUniqueId());
        playerSeatTargets.remove(player.getUniqueId());
        seatLocations.remove(player.getUniqueId());
        if (seat == null) return;
        Location back = returnLocations.remove(player.getUniqueId());
        SeatEntity.dismount(player);
        if (seat.alive()) seat.remove();
        if (back != null && player.isOnline() && !player.isDead() && back.getWorld() == player.getWorld()) {
            Location current = player.getLocation();
            back.setYaw(current.getYaw());
            back.setPitch(current.getPitch());
            player.teleport(back);
        }
    }

    public boolean isSitting(Player player) {
        SeatEntity seat = seats.get(player.getUniqueId());
        return seat != null && seat.alive();
    }

    public void stopPlayersSittingOn(Player target) {
        for (UUID riderId : playerSeatTargets.entrySet().stream()
                .filter(entry -> entry.getValue().equals(target.getUniqueId()))
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toList())) {
            Player rider = target.getServer().getPlayer(riderId);
            if (rider != null) stop(rider);
            else {
                SeatEntity seat = seats.remove(riderId);
                playerSeatTargets.remove(riderId);
                if (seat != null && seat.alive()) seat.remove();
            }
        }
    }

    public void clear() {
        for (SeatEntity seat : seats.values()) if (seat.alive()) seat.remove();
        seats.clear();
        playerSeatTargets.clear();
    }
}
