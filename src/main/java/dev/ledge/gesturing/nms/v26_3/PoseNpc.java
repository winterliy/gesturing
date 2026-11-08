package dev.ledge.gesturing.nms.v26_3;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import dev.ledge.gesturing.gesture.PoseKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_19_R3.CraftServer;
import org.bukkit.craftbukkit.v1_19_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Copyright (C) Gecolay and contributors.
 * Adapted from GSit 3.8.0 (GPL-3.0-only), mcv/v26_3 model/Pose.java.
 * Gesturing changes: GSit services/config removed; the pose is a packet-only player copy attached to
 * Gesturing's seat entity, so the real player never sleeps (no bed screen, no darkening, no spawn change).
 */
public final class PoseNpc {
    private static final double BASE_OFFSET = -0.025d;
    private static final EntityDataAccessor<net.minecraft.world.entity.Pose> POSE_ACCESSOR = EntityDataSerializers.POSE.createAccessor(6);
    private static final EntityDataAccessor<Byte> DATA_FLAG_ACCESSOR = EntityDataSerializers.BYTE.createAccessor(8);
    private static final EntityDataAccessor<Optional<BlockPos>> SLEEP_BLOCK_POS_ACCESSOR = EntityDataSerializers.OPTIONAL_BLOCK_POS.createAccessor(14);
    private static final EntityDataAccessor<Byte> MAIN_HAND_ACCESSOR = EntityDataSerializers.BYTE.createAccessor(18);
    private static final EntityDataAccessor<Byte> SKIN_ACCESSOR = EntityDataSerializers.BYTE.createAccessor(17);
    private static final EntityDataAccessor<net.minecraft.nbt.CompoundTag> LEFT_SHOULDER_ACCESSOR = EntityDataSerializers.COMPOUND_TAG.createAccessor(19);
    private static final EntityDataAccessor<net.minecraft.nbt.CompoundTag> RIGHT_SHOULDER_ACCESSOR = EntityDataSerializers.COMPOUND_TAG.createAccessor(20);

    private final SeatEntity seat;
    private final Location seatLocation;
    private final Player seatPlayer;
    private final PoseKind kind;
    private final ServerPlayer serverPlayer;
    private final ServerPlayer npc;
    private final PoseHelperEntity vehicleEntity;
    private final PoseHelperEntity hideNameEntity;
    private final Location blockLocation;
    private final Block bedBlock;
    private final BlockPos bedPos;
    private final Direction direction;
    private final boolean lying;
    private final int renderRange;
    private Set<Player> viewers = new HashSet<>();
    private ClientboundBundlePacket bundle;
    private ClientboundTeleportEntityPacket teleportPacket;
    private ClientboundSetEquipmentPacket hiddenEquipment;
    private ClientboundBlockUpdatePacket bedPacket;
    private final ClientboundPlayerInfoUpdatePacket addInfoPacket;
    private final ClientboundPlayerInfoRemovePacket removeInfoPacket;
    private final ClientboundRemoveEntitiesPacket removePacket;
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
        this.renderRange = player.getWorld().getSimulationDistance() * 16;

        blockLocation = seatLocation.clone();
        blockLocation.setY(blockLocation.getWorld().getMinHeight());
        bedBlock = blockLocation.getBlock();
        bedPos = new BlockPos(blockLocation.getBlockX(), blockLocation.getBlockY(), blockLocation.getBlockZ());

        npc = createNpc();
        double height = seatLocation.getY() - BASE_OFFSET;
        double scale = serverPlayer.getScale();
        double offset = height;
        if (lying) offset += 0.1125d * scale;
        if (kind == PoseKind.BELLYFLOP) offset += -0.19d * scale;
        npc.moveTo(seatLocation.getX(), offset, seatLocation.getZ(), 0f, 0f);

        direction = directionOf(seatLocation.getYaw());
        if (lying) bedPacket = new ClientboundBlockUpdatePacket(bedPos, Blocks.WHITE_BED.defaultBlockState()
                .setValue(BedBlock.FACING, direction.getOpposite()).setValue(BedBlock.PART, BedPart.HEAD));
        addInfoPacket = new ClientboundPlayerInfoUpdatePacket(EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), Collections.singletonList(npc));
        removeInfoPacket = new ClientboundPlayerInfoRemovePacket(Collections.singletonList(npc.getUUID()));
        removePacket = new ClientboundRemoveEntitiesPacket(npc.getId());
        if (lying) teleportPacket = new ClientboundTeleportEntityPacket(npc);
        if (kind == PoseKind.LEGS_UP) {
            vehicleEntity = new PoseHelperEntity(player.getLocation());
            float sleepYaw = direction == Direction.SOUTH ? 270 : direction == Direction.WEST ? 180 : direction == Direction.EAST ? 0 : 90;
            vehicleEntity.moveTo(seatLocation.getX(), height - 0.375d * scale, seatLocation.getZ(), sleepYaw, 0f);
        } else vehicleEntity = null;
        hideNameEntity = new PoseHelperEntity(player.getLocation());
    }

    public void spawn() {
        viewers = nearbyPlayers();
        npc.getEntityData().set(POSE_ACCESSOR, switch (kind) {
            case LAY, LEGS_UP -> net.minecraft.world.entity.Pose.SLEEPING;
            case BELLYFLOP -> net.minecraft.world.entity.Pose.SWIMMING;
            case SPIN -> net.minecraft.world.entity.Pose.SPIN_ATTACK;
        });
        if (kind == PoseKind.SPIN) npc.getEntityData().set(DATA_FLAG_ACCESSOR, (byte) 4);
        if (lying) npc.getEntityData().set(SLEEP_BLOCK_POS_ACCESSOR, Optional.of(bedPos));
        npc.getEntityData().set(MAIN_HAND_ACCESSOR, serverPlayer.getEntityData().get(MAIN_HAND_ACCESSOR));
        npc.getEntityData().set(SKIN_ACCESSOR, serverPlayer.getEntityData().get(SKIN_ACCESSOR));
        npc.getEntityData().set(LEFT_SHOULDER_ACCESSOR, serverPlayer.getEntityData().get(LEFT_SHOULDER_ACCESSOR));
        npc.getEntityData().set(RIGHT_SHOULDER_ACCESSOR, serverPlayer.getEntityData().get(RIGHT_SHOULDER_ACCESSOR));
        serverPlayer.getEntityData().set(LEFT_SHOULDER_ACCESSOR, new net.minecraft.nbt.CompoundTag());
        serverPlayer.getEntityData().set(RIGHT_SHOULDER_ACCESSOR, new net.minecraft.nbt.CompoundTag());
        serverPlayer.setInvisible(true);
        setEquipmentVisibility(false);

        List<Packet<ClientGamePacketListener>> packets = new ArrayList<>();
        packets.add(addInfoPacket);
        packets.add(new ClientboundAddEntityPacket(npc.getId(), npc.getUUID(), npc.getX(), npc.getY(), npc.getZ(),
                npc.getXRot(), npc.getYRot(), npc.getType(), 0, npc.getDeltaMovement(), npc.getYHeadRot()));
        if (kind == PoseKind.LEGS_UP) {
            vehicleEntity.attachPassenger(npc);
            packets.add(new ClientboundAddEntityPacket(vehicleEntity.getId(), vehicleEntity.getUUID(), vehicleEntity.getX(),
                    vehicleEntity.getY(), vehicleEntity.getZ(), vehicleEntity.getXRot(), vehicleEntity.getYRot(),
                    vehicleEntity.getType(), 0, vehicleEntity.getDeltaMovement(), vehicleEntity.getYHeadRot()));
            packets.add(new ClientboundSetEntityDataPacket(vehicleEntity.getId(), vehicleEntity.getEntityData().getNonDefaultValues()));
            packets.add(new ClientboundSetPassengersPacket(vehicleEntity));
        }
        if (lying) packets.add(bedPacket);
        packets.add(new ClientboundSetEntityDataPacket(npc.getId(),
                npc.getEntityData().isDirty() ? npc.getEntityData().packDirty() : npc.getEntityData().getNonDefaultValues()));
        packets.add(new ClientboundUpdateAttributesPacket(npc.getId(), serverPlayer.getAttributes().getSyncableAttributes()));
        if (kind == PoseKind.SPIN) packets.add(new ClientboundMoveEntityPacket.PosRot(npc.getId(), (short) 0, (short) 0, (short) 0, (byte) 0, fixedRotation(-90f), true));
        packets.add(equipmentPacket());
        bundle = new ClientboundBundlePacket(packets);
        for (Player viewer : viewers) addViewer(viewer);

        hideNameEntity.setVehicle(npc);
        List<Packet<ClientGamePacketListener>> own = new ArrayList<>();
        own.add(new ClientboundAddEntityPacket(hideNameEntity.getId(), hideNameEntity.getUUID(), hideNameEntity.getX(),
                hideNameEntity.getY(), hideNameEntity.getZ(), hideNameEntity.getXRot(), hideNameEntity.getYRot(),
                hideNameEntity.getType(), 0, hideNameEntity.getDeltaMovement(), hideNameEntity.getYHeadRot()));
        own.add(new ClientboundSetEntityDataPacket(hideNameEntity.getId(), hideNameEntity.getEntityData().getNonDefaultValues()));
        own.add(new ClientboundSetPassengersPacket(npc));
        send(serverPlayer, new ClientboundBundlePacket(own));

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
        if (vehicleEntity != null) send(serverPlayer, new ClientboundRemoveEntitiesPacket(vehicleEntity.getId()));
        send(serverPlayer, new ClientboundRemoveEntitiesPacket(hideNameEntity.getId()));
        if (!serverPlayer.getActiveEffectsMap().containsKey(MobEffects.INVISIBILITY)) serverPlayer.setInvisible(false);
        setEquipmentVisibility(true);
        serverPlayer.getEntityData().set(LEFT_SHOULDER_ACCESSOR, npc.getEntityData().get(LEFT_SHOULDER_ACCESSOR));
        serverPlayer.getEntityData().set(RIGHT_SHOULDER_ACCESSOR, npc.getEntityData().get(RIGHT_SHOULDER_ACCESSOR));
    }

    private void addViewer(Player player) {
        send(player, bundle);
        if (!lying) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            send(player, teleportPacket);
            Bukkit.getScheduler().runTaskLater(plugin, () -> send(player, teleportPacket), 2L);
        }, 2L);
    }

    private void removeViewer(Player player) {
        ServerPlayer handle = ((CraftPlayer) player).getHandle();
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
            ClientboundRotateHeadPacket head = new ClientboundRotateHeadPacket(npc, fixed);
            ClientboundMoveEntityPacket.PosRot move = new ClientboundMoveEntityPacket.PosRot(npc.getId(), (short) 0, (short) 0, (short) 0, fixed, (byte) 0, true);
            for (Player viewer : viewers) { send(viewer, head); send(viewer, move); }
            return;
        }
        float playerYaw = seatPlayer.getLocation().getYaw();
        if (directionCache == playerYaw) return;
        directionCache = playerYaw;
        if (direction == Direction.WEST) playerYaw -= 90;
        if (direction == Direction.EAST) playerYaw += 90;
        if (direction == Direction.NORTH) playerYaw -= 180;
        playerYaw = (playerYaw < 0f ? 360f + playerYaw : playerYaw) % 360f;
        byte fixed = fixedRotation(playerYaw >= 315 ? playerYaw - 360 : playerYaw <= 45 ? playerYaw : playerYaw >= 180 ? -45 : 45);
        ClientboundRotateHeadPacket head = new ClientboundRotateHeadPacket(npc, fixed);
        for (Player viewer : viewers) send(viewer, head);
    }

    private ClientboundSetEquipmentPacket equipmentPacket() {
        List<Pair<EquipmentSlot, ItemStack>> list = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) list.add(Pair.of(slot, serverPlayer.getItemBySlot(slot)));
        return new ClientboundSetEquipmentPacket(npc.getId(), list);
    }

    private void setEquipmentVisibility(boolean visible) {
        if (!visible && hiddenEquipment != null) {
            for (Player viewer : viewers) send(viewer, hiddenEquipment);
            return;
        }
        List<Pair<EquipmentSlot, ItemStack>> list = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) list.add(Pair.of(slot, visible ? serverPlayer.getItemBySlot(slot) : ItemStack.EMPTY));
        ClientboundSetEquipmentPacket packet = new ClientboundSetEquipmentPacket(serverPlayer.getId(), list);
        if (!visible) hiddenEquipment = packet;
        for (Player viewer : viewers) send(viewer, packet);
    }

    private static byte fixedRotation(float rotation) { return (byte) (rotation * 256f / 360f); }

    private static Direction directionOf(float yaw) {
        yaw = net.minecraft.util.Mth.wrapDegrees(yaw);
        return (yaw >= 135f || yaw < -135f) ? Direction.NORTH : (yaw >= -135f && yaw < -45f) ? Direction.EAST
                : (yaw >= -45f && yaw < 45f) ? Direction.SOUTH : yaw >= 45f ? Direction.WEST : Direction.NORTH;
    }

    private ServerPlayer createNpc() {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) seatLocation.getWorld()).getHandle();
        GameProfile profile = new GameProfile(UUID.randomUUID(), seatPlayer.getName());
        profile.getProperties().putAll(serverPlayer.getGameProfile().getProperties());
        ServerPlayer created = new ServerPlayer(server, level, profile);
        created.connection = serverPlayer.connection;
        return created;
    }

    private static void send(Player player, Packet<?> packet) { send(((CraftPlayer) player).getHandle(), packet); }
    private static void send(ServerPlayer player, Packet<?> packet) { player.connection.send(packet); }
}
