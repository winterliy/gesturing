package dev.ledge.gesturing.nms.v1_16_R3;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import dev.ledge.gesturing.gesture.PoseKind;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.v1_16_R3.BlockBed;
import net.minecraft.server.v1_16_R3.BlockPosition;
import net.minecraft.server.v1_16_R3.BlockPropertyBedPart;
import net.minecraft.server.v1_16_R3.Blocks;
import net.minecraft.server.v1_16_R3.DataWatcher;
import net.minecraft.server.v1_16_R3.DataWatcherObject;
import net.minecraft.server.v1_16_R3.EntityHuman;
import net.minecraft.server.v1_16_R3.EntityLiving;
import net.minecraft.server.v1_16_R3.EntityPlayer;
import net.minecraft.server.v1_16_R3.EntityPose;
import net.minecraft.server.v1_16_R3.EnumDirection;
import net.minecraft.server.v1_16_R3.EnumItemSlot;
import net.minecraft.server.v1_16_R3.ItemStack;
import net.minecraft.server.v1_16_R3.MinecraftServer;
import net.minecraft.server.v1_16_R3.MobEffects;
import net.minecraft.server.v1_16_R3.NBTTagCompound;
import net.minecraft.server.v1_16_R3.Packet;
import net.minecraft.server.v1_16_R3.PacketPlayOutBlockChange;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntity;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityEquipment;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityHeadRotation;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityTeleport;
import net.minecraft.server.v1_16_R3.PacketPlayOutMount;
import net.minecraft.server.v1_16_R3.PacketPlayOutNamedEntitySpawn;
import net.minecraft.server.v1_16_R3.PacketPlayOutPlayerInfo;
import net.minecraft.server.v1_16_R3.PacketPlayOutSpawnEntity;
import net.minecraft.server.v1_16_R3.PlayerInteractManager;
import net.minecraft.server.v1_16_R3.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Copyright (C) Gecolay and contributors.
 * Adapted from GSit 3.8.0 (GPL-3.0-only), Pose model, for Spigot-mapped 1.16.5 (v1_16_R3).
 * Gesturing changes: GSit services/config removed; the pose is a packet-only player copy attached to
 * Gesturing's seat entity, so the real player never sleeps (no bed screen, no darkening, no spawn change).
 */
public final class PoseNpc {
    private static final double BASE_OFFSET = -0.025d;

    private static final DataWatcherObject<Byte> LIVING_FLAGS = findStatic(EntityLiving.class, 7, Byte.class);
    private static final DataWatcherObject<Optional<BlockPosition>> SLEEP_POS = findStatic(EntityLiving.class, 13, Optional.class);
    private static final DataWatcherObject<Byte> SKIN = EntityHuman.getSkinPartsWatcher();
    private static final DataWatcherObject<Byte> MAIN_HAND = findStatic(EntityHuman.class, 17, Byte.class);
    private static final DataWatcherObject<NBTTagCompound> LEFT_SHOULDER = findStatic(EntityHuman.class, 18, NBTTagCompound.class);
    private static final DataWatcherObject<NBTTagCompound> RIGHT_SHOULDER = findStatic(EntityHuman.class, 19, NBTTagCompound.class);

    private final SeatEntity seat;
    private final Location seatLocation;
    private final Player seatPlayer;
    private final PoseKind kind;
    private final EntityPlayer serverPlayer;
    private final EntityPlayer npc;
    private final PoseHelperEntity vehicleEntity;
    private final PoseHelperEntity hideNameEntity;
    private final Location blockLocation;
    private final Block bedBlock;
    private final BlockPosition bedPos;
    private final EnumDirection direction;
    private final boolean lying;
    private final int renderRange;
    private Set<Player> viewers = new HashSet<>();
    private List<Packet<?>> bundle;
    private PacketPlayOutEntityTeleport teleportPacket;
    private PacketPlayOutEntityEquipment hiddenEquipment;
    private PacketPlayOutBlockChange bedPacket;
    private final PacketPlayOutPlayerInfo addInfoPacket;
    private final PacketPlayOutPlayerInfo removeInfoPacket;
    private final PacketPlayOutEntityDestroy removePacket;
    private final Plugin plugin;
    private float directionCache = Float.NaN;

    public PoseNpc(Plugin plugin, Player player, SeatEntity seat, Location seatLocation, PoseKind kind) {
        this.plugin = plugin;
        this.seat = seat;
        this.seatLocation = seatLocation.clone();
        this.seatPlayer = player;
        this.kind = kind;
        this.serverPlayer = ((CraftPlayer) player).getHandle();
        this.lying = kind == PoseKind.LAY || kind == PoseKind.LEGS_UP;
        this.renderRange = Math.min(Bukkit.getViewDistance(), 10) * 16;

        blockLocation = seatLocation.clone();
        blockLocation.setY(0);
        bedBlock = blockLocation.getBlock();
        bedPos = new BlockPosition(blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ());

        npc = createNpc();
        double height = seatLocation.getY() - BASE_OFFSET;
        double offset = height;
        if (lying) offset += 0.1125d;
        if (kind == PoseKind.BELLYFLOP) offset += -0.19d;
        npc.setLocation(seatLocation.getX(), offset, seatLocation.getZ(), 0f, 0f);

        direction = directionOf(seatLocation.getYaw());
        if (lying) bedPacket = new PacketPlayOutBlockChange(bedPos, Blocks.WHITE_BED.getBlockData()
                .set(BlockBed.FACING, direction.opposite()).set(BlockBed.PART, BlockPropertyBedPart.HEAD));
        addInfoPacket = new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, npc);
        removeInfoPacket = new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, npc);
        removePacket = new PacketPlayOutEntityDestroy(npc.getId());
        if (lying) teleportPacket = new PacketPlayOutEntityTeleport(npc);
        if (kind == PoseKind.LEGS_UP) {
            vehicleEntity = new PoseHelperEntity(player.getLocation());
            float sleepYaw = direction == EnumDirection.SOUTH ? 270 : direction == EnumDirection.WEST ? 180 : direction == EnumDirection.EAST ? 0 : 90;
            vehicleEntity.setLocation(seatLocation.getX(), height - 0.375d, seatLocation.getZ(), sleepYaw, 0f);
        } else vehicleEntity = null;
        hideNameEntity = new PoseHelperEntity(player.getLocation());
    }

    public void spawn() {
        viewers = nearbyPlayers();
        npc.setPose(switch (kind) {
            case LAY, LEGS_UP -> EntityPose.SLEEPING;
            case BELLYFLOP -> EntityPose.SWIMMING;
            case SPIN -> EntityPose.SPIN_ATTACK;
        });
        DataWatcher npcData = npc.getDataWatcher();
        DataWatcher realData = serverPlayer.getDataWatcher();
        if (kind == PoseKind.SPIN) npcData.set(LIVING_FLAGS, (byte) 4);
        if (lying) npcData.set(SLEEP_POS, Optional.of(bedPos));
        npcData.set(MAIN_HAND, realData.get(MAIN_HAND));
        npcData.set(SKIN, realData.get(SKIN));
        npcData.set(LEFT_SHOULDER, realData.get(LEFT_SHOULDER));
        npcData.set(RIGHT_SHOULDER, realData.get(RIGHT_SHOULDER));
        realData.set(LEFT_SHOULDER, new NBTTagCompound());
        realData.set(RIGHT_SHOULDER, new NBTTagCompound());
        serverPlayer.setInvisible(true);
        setEquipmentVisibility(false);

        List<Packet<?>> packets = new ArrayList<>();
        packets.add(addInfoPacket);
        packets.add(new PacketPlayOutNamedEntitySpawn(npc));
        if (kind == PoseKind.LEGS_UP) {
            vehicleEntity.attachPassenger(npc);
            packets.add(new PacketPlayOutSpawnEntity(vehicleEntity));
            packets.add(new PacketPlayOutEntityMetadata(vehicleEntity.getId(), vehicleEntity.getDataWatcher(), true));
            packets.add(new PacketPlayOutMount(vehicleEntity));
        }
        if (lying) packets.add(bedPacket);
        packets.add(new PacketPlayOutEntityMetadata(npc.getId(), npcData, true));
        if (kind == PoseKind.SPIN) packets.add(new PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook(npc.getId(), (short) 0, (short) 0, (short) 0, (byte) 0, fixedRotation(-90f), true));
        packets.add(equipmentPacket());
        bundle = packets;
        for (Player viewer : viewers) addViewer(viewer);

        hideNameEntity.setVehicle(npc);
        send(serverPlayer, new PacketPlayOutSpawnEntity(hideNameEntity));
        send(serverPlayer, new PacketPlayOutEntityMetadata(hideNameEntity.getId(), hideNameEntity.getDataWatcher(), true));
        send(serverPlayer, new PacketPlayOutMount(npc));

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
        if (kind != PoseKind.SPIN) updateDirection();
        serverPlayer.setInvisible(true);
        setEquipmentVisibility(false);
    }

    public void remove() {
        seat.setRunnable(null);
        for (Player viewer : viewers) removeViewer(viewer);
        if (vehicleEntity != null) send(serverPlayer, new PacketPlayOutEntityDestroy(vehicleEntity.getId()));
        send(serverPlayer, new PacketPlayOutEntityDestroy(hideNameEntity.getId()));
        if (!serverPlayer.hasEffect(MobEffects.INVISIBILITY)) serverPlayer.setInvisible(false);
        setEquipmentVisibility(true);
        serverPlayer.getDataWatcher().set(LEFT_SHOULDER, npc.getDataWatcher().get(LEFT_SHOULDER));
        serverPlayer.getDataWatcher().set(RIGHT_SHOULDER, npc.getDataWatcher().get(RIGHT_SHOULDER));
    }

    private void addViewer(Player player) {
        for (Packet<?> packet : bundle) send(player, packet);
        if (!lying) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            send(player, teleportPacket);
            Bukkit.getScheduler().runTaskLater(plugin, () -> send(player, teleportPacket), 2L);
        }, 2L);
    }

    private void removeViewer(Player player) {
        EntityPlayer handle = ((CraftPlayer) player).getHandle();
        send(handle, removeInfoPacket);
        send(handle, removePacket);
        player.sendBlockChange(blockLocation, bedBlock.getBlockData());
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
        if (kind == PoseKind.BELLYFLOP) {
            byte fixed = fixedRotation(seatPlayer.getLocation().getYaw());
            if (directionCache == fixed) return;
            directionCache = fixed;
            PacketPlayOutEntityHeadRotation head = new PacketPlayOutEntityHeadRotation(npc, fixed);
            PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook move = new PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook(npc.getId(), (short) 0, (short) 0, (short) 0, fixed, (byte) 0, true);
            for (Player viewer : viewers) { send(viewer, head); send(viewer, move); }
            return;
        }
        float playerYaw = seatPlayer.getLocation().getYaw();
        if (directionCache == playerYaw) return;
        directionCache = playerYaw;
        if (direction == EnumDirection.WEST) playerYaw -= 90;
        if (direction == EnumDirection.EAST) playerYaw += 90;
        if (direction == EnumDirection.NORTH) playerYaw -= 180;
        playerYaw = (playerYaw < 0f ? 360f + playerYaw : playerYaw) % 360f;
        byte fixed = fixedRotation(playerYaw >= 315 ? playerYaw - 360 : playerYaw <= 45 ? playerYaw : playerYaw >= 180 ? -45 : 45);
        PacketPlayOutEntityHeadRotation head = new PacketPlayOutEntityHeadRotation(npc, fixed);
        for (Player viewer : viewers) send(viewer, head);
    }

    private PacketPlayOutEntityEquipment equipmentPacket() {
        List<Pair<EnumItemSlot, ItemStack>> list = new ArrayList<>();
        for (EnumItemSlot slot : EnumItemSlot.values()) list.add(Pair.of(slot, serverPlayer.getEquipment(slot)));
        return new PacketPlayOutEntityEquipment(npc.getId(), list);
    }

    private void setEquipmentVisibility(boolean visible) {
        if (!visible && hiddenEquipment != null) {
            for (Player viewer : viewers) send(viewer, hiddenEquipment);
            return;
        }
        List<Pair<EnumItemSlot, ItemStack>> list = new ArrayList<>();
        for (EnumItemSlot slot : EnumItemSlot.values()) list.add(Pair.of(slot, visible ? serverPlayer.getEquipment(slot) : ItemStack.b));
        PacketPlayOutEntityEquipment packet = new PacketPlayOutEntityEquipment(serverPlayer.getId(), list);
        if (!visible) hiddenEquipment = packet;
        for (Player viewer : viewers) send(viewer, packet);
    }

    private static byte fixedRotation(float rotation) { return (byte) (rotation * 256f / 360f); }

    private static EnumDirection directionOf(float yaw) {
        yaw = yaw % 360f;
        if (yaw >= 180f) yaw -= 360f;
        if (yaw < -180f) yaw += 360f;
        return (yaw >= 135f || yaw < -135f) ? EnumDirection.NORTH : (yaw >= -135f && yaw < -45f) ? EnumDirection.EAST
                : (yaw >= -45f && yaw < 45f) ? EnumDirection.SOUTH : yaw >= 45f ? EnumDirection.WEST : EnumDirection.NORTH;
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

    /** Finds a static DataWatcherObject of the given entity-data id (obfuscated field names differ per release). */
    @SuppressWarnings("unchecked")
    private static <T> DataWatcherObject<T> findStatic(Class<?> owner, int id, Class<?> valueType) {
        for (Field field : owner.getDeclaredFields()) {
            if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.getType() != DataWatcherObject.class) continue;
            try {
                field.setAccessible(true);
                DataWatcherObject<?> object = (DataWatcherObject<?>) field.get(null);
                if (object.a() != id) continue;
                java.lang.reflect.Type generic = ((java.lang.reflect.ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                Class<?> raw = generic instanceof Class<?> c ? c : (Class<?>) ((java.lang.reflect.ParameterizedType) generic).getRawType();
                if (raw == valueType) return (DataWatcherObject<T>) object;
            } catch (ReflectiveOperationException ignored) { }
        }
        throw new IllegalStateException("No entity-data accessor " + id + " in " + owner.getSimpleName());
    }

    private static void send(Player player, Packet<?> packet) { send(((CraftPlayer) player).getHandle(), packet); }
    private static void send(EntityPlayer player, Packet<?> packet) { player.playerConnection.sendPacket(packet); }
}
