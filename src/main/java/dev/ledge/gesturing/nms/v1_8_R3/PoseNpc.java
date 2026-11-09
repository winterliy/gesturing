package dev.ledge.gesturing.nms.v1_8_R3;

import com.mojang.authlib.GameProfile;
import dev.ledge.gesturing.gesture.PoseKind;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.v1_8_R3.BlockPosition;
import net.minecraft.server.v1_8_R3.DataWatcher;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.Packet;
import net.minecraft.server.v1_8_R3.PacketPlayOutBed;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityEquipment;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityHeadRotation;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_8_R3.PacketPlayOutEntityTeleport;
import net.minecraft.server.v1_8_R3.PacketPlayOutNamedEntitySpawn;
import net.minecraft.server.v1_8_R3.PacketPlayOutPlayerInfo;
import net.minecraft.server.v1_8_R3.PlayerInteractManager;
import net.minecraft.server.v1_8_R3.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R3.CraftServer;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Packet-only copy of the player lying in a bed pose, adapted from GSit (GPL-3.0-only) for Spigot-mapped 1.8.8.
 * The real player stays on an invisible seat, so no bed screen, darkening or spawn-point change happens.
 */
public final class PoseNpc {
    private static final double BASE_OFFSET = -0.025d;

    private final SeatEntity seat;
    private final Location seatLocation;
    private final Player seatPlayer;
    private final EntityPlayer serverPlayer;
    private final EntityPlayer npc;
    private final Location blockLocation;
    private final Block bedBlock;
    private final BlockPosition bedPos;
    private final byte bedData;
    private final int renderRange;
    private final Plugin plugin;
    private Set<Player> viewers = new HashSet<>();
    private List<Packet<?>> bundle = new ArrayList<>();
    private List<Packet<?>> hiddenEquipment;
    private float directionCache = Float.NaN;

    public PoseNpc(Plugin plugin, Player player, SeatEntity seat, Location seatLocation, PoseKind kind) {
        this.plugin = plugin;
        this.seat = seat;
        this.seatLocation = seatLocation.clone();
        this.seatPlayer = player;
        this.serverPlayer = ((CraftPlayer) player).getHandle();
        this.renderRange = Math.min(Bukkit.getViewDistance(), 10) * 16;

        blockLocation = seatLocation.clone();
        blockLocation.setY(0);
        bedBlock = blockLocation.getBlock();
        bedPos = new BlockPosition(blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ());

        float yaw = seatLocation.getYaw() % 360f;
        if (yaw >= 180f) yaw -= 360f;
        if (yaw < -180f) yaw += 360f;
        // 1.8 bed meta: bits 0-1 = direction the head points (0 south, 1 west, 2 north, 3 east), bit 3 = head half.
        // The head points opposite to the facing direction (same rule as the newer ports).
        int facing = (yaw >= 135f || yaw < -135f) ? 0 : (yaw >= -135f && yaw < -45f) ? 1 : (yaw >= -45f && yaw < 45f) ? 2 : 3;
        bedData = (byte) (facing | 8);

        npc = createNpc();
        npc.setLocation(seatLocation.getX(), seatLocation.getY() - BASE_OFFSET + 0.1125d, seatLocation.getZ(), 0f, 0f);
    }

    public void spawn() {
        viewers = nearbyPlayers();
        DataWatcher npcData = npc.getDataWatcher();
        DataWatcher realData = serverPlayer.getDataWatcher();
        npcData.watch(10, realData.getByte(10));
        serverPlayer.setInvisible(true);

        List<Packet<?>> packets = new ArrayList<>();
        packets.add(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, npc));
        packets.add(new PacketPlayOutNamedEntitySpawn(npc));
        packets.add(new PacketPlayOutEntityMetadata(npc.getId(), npcData, true));
        packets.add(new PacketPlayOutBed(npc, bedPos));
        packets.addAll(equipmentPackets(npc.getId(), true));
        bundle = packets;
        for (Player viewer : viewers) addViewer(viewer);
        setEquipmentVisibility(false);
        seat.setRunnable(this::tick);
    }

    private void tick() {
        Set<Player> current = nearbyPlayers();
        for (Player viewer : current) if (viewers.add(viewer)) addViewer(viewer);
        viewers.removeIf(viewer -> {
            if (current.contains(viewer)) return false;
            removeViewer(viewer);
            return true;
        });
        updateDirection();
        serverPlayer.setInvisible(true);
        setEquipmentVisibility(false);
    }

    public void remove() {
        seat.setRunnable(null);
        for (Player viewer : viewers) removeViewer(viewer);
        serverPlayer.setInvisible(false);
        setEquipmentVisibility(true);
    }

    private void addViewer(Player player) {
        player.sendBlockChange(blockLocation, Material.BED_BLOCK, bedData);
        for (Packet<?> packet : bundle) send(player, packet);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            send(player, new PacketPlayOutEntityTeleport(npc));
            Bukkit.getScheduler().runTaskLater(plugin, () -> send(player, new PacketPlayOutEntityTeleport(npc)), 2L);
        }, 2L);
    }

    private void removeViewer(Player player) {
        EntityPlayer handle = ((CraftPlayer) player).getHandle();
        send(handle, new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, npc));
        send(handle, new PacketPlayOutEntityDestroy(npc.getId()));
        player.sendBlockChange(blockLocation, bedBlock.getType(), bedBlock.getData());
    }

    private Set<Player> nearbyPlayers() {
        Set<Player> result = new HashSet<>();
        double max = (double) renderRange * renderRange;
        for (Player player : seatPlayer.getWorld().getPlayers()) {
            if (seatLocation.distanceSquared(player.getLocation()) <= max && player.canSee(seatPlayer)) result.add(player);
        }
        return result;
    }

    private void updateDirection() {
        float playerYaw = seatPlayer.getLocation().getYaw();
        if (directionCache == playerYaw) return;
        directionCache = playerYaw;
        float adjusted = (playerYaw < 0f ? 360f + playerYaw : playerYaw) % 360f;
        adjusted = adjusted >= 315 ? adjusted - 360 : adjusted <= 45 ? adjusted : adjusted >= 180 ? -45 : 45;
        PacketPlayOutEntityHeadRotation head = new PacketPlayOutEntityHeadRotation(npc, (byte) (adjusted * 256f / 360f));
        for (Player viewer : viewers) send(viewer, head);
    }

    private List<Packet<?>> equipmentPackets(int entityId, boolean visible) {
        List<Packet<?>> list = new ArrayList<>();
        for (int slot = 0; slot < 5; slot++) {
            list.add(new PacketPlayOutEntityEquipment(entityId, slot, visible ? serverPlayer.getEquipment(slot) : null));
        }
        return list;
    }

    private void setEquipmentVisibility(boolean visible) {
        List<Packet<?>> list;
        if (!visible && hiddenEquipment != null) list = hiddenEquipment;
        else {
            list = equipmentPackets(serverPlayer.getId(), visible);
            if (!visible) hiddenEquipment = list;
        }
        for (Player viewer : viewers) for (Packet<?> packet : list) send(viewer, packet);
    }

    private EntityPlayer createNpc() {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        WorldServer level = ((CraftWorld) seatLocation.getWorld()).getHandle();
        GameProfile profile = new GameProfile(UUID.randomUUID(), seatPlayer.getName());
        profile.getProperties().putAll(serverPlayer.getProfile().getProperties());
        EntityPlayer created = new EntityPlayer(server, level, profile, new PlayerInteractManager(level));
        created.playerConnection = serverPlayer.playerConnection;
        return created;
    }

    private static void send(Player player, Packet<?> packet) { send(((CraftPlayer) player).getHandle(), packet); }
    private static void send(EntityPlayer player, Packet<?> packet) { player.playerConnection.sendPacket(packet); }
}
