package dev.ledge.gesturing.sit;

import dev.ledge.gesturing.nms.v26_3.SeatEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class SeatManager {
    private final Map<UUID, SeatEntity> seats = new HashMap<>();
    private final Map<UUID, UUID> playerSeatTargets = new HashMap<>();

    public boolean sitOnSupportingBlock(Player player) {
        stop(player);
        Block block = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
        if (block.isEmpty() || block.isLiquid()) return false;
        SeatGeometry.Offset offset = SeatGeometry.offsetFor(block.getBlockData());
        boolean directional = block.getBlockData() instanceof Stairs || block.getBlockData() instanceof Directional;
        Location location = new Location(block.getWorld(), block.getX() + offset.x(), block.getY() + offset.y() - 0.025,
                block.getZ() + offset.z(), directional ? offset.yaw() : player.getLocation().getYaw(), 0f);
        SeatEntity seat = new SeatEntity(location);
        Entity rider = ((CraftPlayer) player).getHandle();
        if (!rider.startRiding(seat, true, true)) {
            seat.discard();
            return false;
        }
        if (!((CraftWorld) block.getWorld()).getHandle().addFreshEntity(seat, CreatureSpawnEvent.SpawnReason.CUSTOM)) {
            rider.stopRiding(true);
            seat.discard();
            return false;
        }
        seats.put(player.getUniqueId(), seat);
        return true;
    }

    public boolean sitOnPlayer(Player rider, Player target) {
        stop(rider);
        SeatEntity seat = new SeatEntity(target.getLocation().add(0, 0.15, 0));
        Entity targetHandle = ((CraftPlayer) target).getHandle();
        Entity riderHandle = ((CraftPlayer) rider).getHandle();
        if (!seat.startRiding(targetHandle, true, true) || !riderHandle.startRiding(seat, true, true)) {
            riderHandle.stopRiding(true);
            seat.stopRiding(true);
            seat.discard();
            return false;
        }
        if (!((CraftWorld) target.getWorld()).getHandle().addFreshEntity(seat, CreatureSpawnEvent.SpawnReason.CUSTOM)) {
            riderHandle.stopRiding(true);
            seat.stopRiding(true);
            seat.discard();
            return false;
        }
        seats.put(rider.getUniqueId(), seat);
        playerSeatTargets.put(rider.getUniqueId(), target.getUniqueId());
        return true;
    }

    public void stop(Player player) {
        SeatEntity seat = seats.remove(player.getUniqueId());
        playerSeatTargets.remove(player.getUniqueId());
        if (seat == null) return;
        Entity handle = ((CraftPlayer) player).getHandle();
        if (handle.isPassenger()) handle.stopRiding(true);
        if (seat.isAlive()) seat.discard();
    }

    public boolean isSitting(Player player) {
        SeatEntity seat = seats.get(player.getUniqueId());
        return seat != null && seat.isAlive();
    }

    public void stopPlayersSittingOn(Player target) {
        for (UUID riderId : playerSeatTargets.entrySet().stream()
                .filter(entry -> entry.getValue().equals(target.getUniqueId()))
                .map(Map.Entry::getKey).toList()) {
            Player rider = target.getServer().getPlayer(riderId);
            if (rider != null) stop(rider);
            else {
                SeatEntity seat = seats.remove(riderId);
                playerSeatTargets.remove(riderId);
                if (seat != null && seat.isAlive()) seat.discard();
            }
        }
    }

    public void clear() {
        for (SeatEntity seat : seats.values()) if (seat.isAlive()) seat.discard();
        seats.clear();
        playerSeatTargets.clear();
    }
}
