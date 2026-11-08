package dev.ledge.gesturing.fabric;

import com.google.common.collect.ImmutableList;
import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * Copyright (C) Gecolay and contributors.
 * Adapted from GSit 3.8.0 (GPL-3.0-only), mcv/v26_3 model/Pose.java.
 * Gesturing changes: Fabric/Mojang-mapped port without Bukkit; the pose is a packet-only copy of the player
 * attached to Gesturing's seat, so the real player never sleeps (no bed, no bed screen, no darkening).
 */
final class PoseNpc {
    enum Kind { LAY, LEGS_UP, BELLYFLOP, SPIN }

    private static final double BASE_OFFSET = -0.025d;
    private static final double RENDER_RANGE = 160d;
    private static final EntityDataAccessor<Pose> POSE_ACCESSOR = EntityDataSerializers.POSE.createAccessor(6);
    private static final EntityDataAccessor<Byte> DATA_FLAG_ACCESSOR = EntityDataSerializers.BYTE.createAccessor(8);
    private static final EntityDataAccessor<Optional<BlockPos>> SLEEP_BLOCK_POS_ACCESSOR = EntityDataSerializers.OPTIONAL_BLOCK_POS.createAccessor(14);
    private static final EntityDataAccessor<HumanoidArm> MAIN_HAND_ACCESSOR = EntityDataSerializers.HUMANOID_ARM.createAccessor(15);
    private static final EntityDataAccessor<Byte> SKIN_ACCESSOR = EntityDataSerializers.BYTE.createAccessor(16);
    private static final EntityDataAccessor<OptionalInt> LEFT_SHOULDER_ACCESSOR = EntityDataSerializers.OPTIONAL_UNSIGNED_INT.createAccessor(19);
    private static final EntityDataAccessor<OptionalInt> RIGHT_SHOULDER_ACCESSOR = EntityDataSerializers.OPTIONAL_UNSIGNED_INT.createAccessor(20);
    private static Field passengersField;
    private static Field vehicleField;

    private final ServerPlayer player;
    private final ServerLevel level;
    private final Kind kind;
    private final boolean lying;
    private final Vec3 seatPos;
    private final ServerPlayer npc;
    private final AreaEffectCloud vehicleEntity;
    private final AreaEffectCloud hideNameEntity;
    private final BlockPos bedPos;
    private final Direction direction;
    private final Map<ServerPlayer, Integer> viewers = new HashMap<>();
    private final ClientboundPlayerInfoUpdatePacket addInfoPacket;
    private final ClientboundPlayerInfoRemovePacket removeInfoPacket;
    private final ClientboundRemoveEntitiesPacket removePacket;
    private ClientboundBundlePacket bundle;
    private ClientboundTeleportEntityPacket teleportPacket;
    private ClientboundBlockUpdatePacket bedPacket;
    private ClientboundSetEquipmentPacket hiddenEquipment;
    private float directionCache = Float.NaN;

    PoseNpc(ServerPlayer player, Vec3 seatPos, float seatYaw, Kind kind) {
        this.player = player;
        this.level = player.level();
        this.kind = kind;
        this.lying = kind == Kind.LAY || kind == Kind.LEGS_UP;
        this.seatPos = seatPos;
        bedPos = BlockPos.containing(seatPos.x, level.getMinY(), seatPos.z);

        GameProfile profile = new GameProfile(UUID.randomUUID(), player.getGameProfile().name(), player.getGameProfile().properties());
        npc = new ServerPlayer(level.getServer(), level, profile, player.clientInformation());
        npc.connection = player.connection;
        double height = seatPos.y - BASE_OFFSET;
        double scale = player.getScale();
        double offset = height;
        if (lying) offset += 0.1125d * scale;
        if (kind == Kind.BELLYFLOP) offset += -0.19d * scale;
        npc.absSnapTo(seatPos.x, offset, seatPos.z, 0f, 0f);

        direction = directionOf(seatYaw);
        if (lying) bedPacket = new ClientboundBlockUpdatePacket(bedPos, Blocks.WHITE_BED.defaultBlockState()
                .setValue(BedBlock.FACING, direction.getOpposite()).setValue(BedBlock.PART, BedPart.HEAD));
        addInfoPacket = new ClientboundPlayerInfoUpdatePacket(EnumSet.of(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.INITIALIZE_CHAT,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME), Collections.singletonList(npc));
        removeInfoPacket = new ClientboundPlayerInfoRemovePacket(Collections.singletonList(npc.getUUID()));
        removePacket = new ClientboundRemoveEntitiesPacket(npc.getId());
        if (lying) teleportPacket = new ClientboundTeleportEntityPacket(npc.getId(), PositionMoveRotation.of(npc), Set.of(), false);
        if (kind == Kind.LEGS_UP) {
            vehicleEntity = helper(player.position());
            float sleepYaw = direction == Direction.SOUTH ? 270 : direction == Direction.WEST ? 180 : direction == Direction.EAST ? 0 : 90;
            vehicleEntity.absSnapTo(seatPos.x, height - 0.375d * scale, seatPos.z, sleepYaw, 0f);
        } else vehicleEntity = null;
        hideNameEntity = helper(player.position());
    }

    private AreaEffectCloud helper(Vec3 at) {
        AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
        cloud.setRadius(0);
        cloud.setDuration(Integer.MAX_VALUE);
        cloud.setNoGravity(true);
        return cloud;
    }

    void spawn() {
        npc.getEntityData().set(POSE_ACCESSOR, switch (kind) {
            case LAY, LEGS_UP -> Pose.SLEEPING;
            case BELLYFLOP -> Pose.SWIMMING;
            case SPIN -> Pose.SPIN_ATTACK;
        });
        if (kind == Kind.SPIN) npc.getEntityData().set(DATA_FLAG_ACCESSOR, (byte) 4);
        if (lying) npc.getEntityData().set(SLEEP_BLOCK_POS_ACCESSOR, Optional.of(bedPos));
        npc.getEntityData().set(MAIN_HAND_ACCESSOR, player.getEntityData().get(MAIN_HAND_ACCESSOR));
        npc.getEntityData().set(SKIN_ACCESSOR, player.getEntityData().get(SKIN_ACCESSOR));
        npc.getEntityData().set(LEFT_SHOULDER_ACCESSOR, player.getEntityData().get(LEFT_SHOULDER_ACCESSOR));
        npc.getEntityData().set(RIGHT_SHOULDER_ACCESSOR, player.getEntityData().get(RIGHT_SHOULDER_ACCESSOR));
        player.getEntityData().set(LEFT_SHOULDER_ACCESSOR, OptionalInt.empty());
        player.getEntityData().set(RIGHT_SHOULDER_ACCESSOR, OptionalInt.empty());
        player.setInvisible(true);

        List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();
        packets.add(addInfoPacket);
        packets.add(addEntity(npc));
        if (kind == Kind.LEGS_UP) {
            attach(npc, vehicleEntity);
            packets.add(addEntity(vehicleEntity));
            packets.add(new ClientboundSetEntityDataPacket(vehicleEntity.getId(), vehicleEntity.getEntityData().getNonDefaultValues()));
            packets.add(new ClientboundSetPassengersPacket(vehicleEntity));
        }
        if (lying) packets.add(bedPacket);
        packets.add(new ClientboundSetEntityDataPacket(npc.getId(),
                npc.getEntityData().isDirty() ? npc.getEntityData().packDirty() : npc.getEntityData().getNonDefaultValues()));
        packets.add(new ClientboundUpdateAttributesPacket(npc.getId(), player.getAttributes().getSyncableAttributes()));
        if (kind == Kind.SPIN) packets.add(new ClientboundMoveEntityPacket.PosRot(npc.getId(), (short) 0, (short) 0, (short) 0, (byte) 0, fixedRotation(-90f), true));
        packets.add(equipmentPacket());
        bundle = new ClientboundBundlePacket(packets);
        refreshViewers();
        setEquipmentVisibility(false);

        attach(hideNameEntity, npc);
        List<Packet<? super ClientGamePacketListener>> own = new ArrayList<>();
        own.add(addEntity(hideNameEntity));
        own.add(new ClientboundSetEntityDataPacket(hideNameEntity.getId(), hideNameEntity.getEntityData().getNonDefaultValues()));
        own.add(new ClientboundSetPassengersPacket(npc));
        player.connection.send(new ClientboundBundlePacket(own));
    }

    void tick() {
        refreshViewers();
        for (Map.Entry<ServerPlayer, Integer> entry : viewers.entrySet()) {
            int age = entry.getValue() + 1;
            entry.setValue(age);
            if (lying && (age == 2 || age == 4)) entry.getKey().connection.send(teleportPacket);
        }
        if (kind != Kind.SPIN) updateDirection();
        player.setInvisible(true);
        setEquipmentVisibility(false);
    }

    void remove() {
        for (ServerPlayer viewer : List.copyOf(viewers.keySet())) removeViewer(viewer);
        viewers.clear();
        if (vehicleEntity != null) player.connection.send(new ClientboundRemoveEntitiesPacket(vehicleEntity.getId()));
        player.connection.send(new ClientboundRemoveEntitiesPacket(hideNameEntity.getId()));
        if (!player.hasEffect(MobEffects.INVISIBILITY)) player.setInvisible(false);
        setEquipmentVisibility(true);
        player.getEntityData().set(LEFT_SHOULDER_ACCESSOR, npc.getEntityData().get(LEFT_SHOULDER_ACCESSOR));
        player.getEntityData().set(RIGHT_SHOULDER_ACCESSOR, npc.getEntityData().get(RIGHT_SHOULDER_ACCESSOR));
    }

    private void refreshViewers() {
        Set<ServerPlayer> current = new java.util.HashSet<>();
        for (ServerPlayer other : level.players()) {
            if (other.position().distanceToSqr(seatPos) <= RENDER_RANGE * RENDER_RANGE) current.add(other);
        }
        for (ServerPlayer other : current) {
            if (!viewers.containsKey(other)) {
                viewers.put(other, 0);
                other.connection.send(bundle);
            }
        }
        for (ServerPlayer other : List.copyOf(viewers.keySet())) {
            if (!current.contains(other)) { removeViewer(other); viewers.remove(other); }
        }
    }

    private void removeViewer(ServerPlayer viewer) {
        viewer.connection.send(removeInfoPacket);
        viewer.connection.send(removePacket);
        viewer.connection.send(new ClientboundBlockUpdatePacket(level, bedPos));
    }

    private void updateDirection() {
        if (kind == Kind.BELLYFLOP) {
            byte fixed = fixedRotation(player.getYRot());
            if (directionCache == fixed) return;
            directionCache = fixed;
            ClientboundRotateHeadPacket head = new ClientboundRotateHeadPacket(npc, fixed);
            ClientboundMoveEntityPacket.PosRot move = new ClientboundMoveEntityPacket.PosRot(npc.getId(), (short) 0, (short) 0, (short) 0, fixed, (byte) 0, true);
            for (ServerPlayer viewer : viewers.keySet()) { viewer.connection.send(head); viewer.connection.send(move); }
            return;
        }
        float playerYaw = player.getYRot();
        if (directionCache == playerYaw) return;
        directionCache = playerYaw;
        if (direction == Direction.WEST) playerYaw -= 90;
        if (direction == Direction.EAST) playerYaw += 90;
        if (direction == Direction.NORTH) playerYaw -= 180;
        playerYaw = (playerYaw < 0f ? 360f + playerYaw % 360f : playerYaw) % 360f;
        byte fixed = fixedRotation(playerYaw >= 315 ? playerYaw - 360 : playerYaw <= 45 ? playerYaw : playerYaw >= 180 ? -45 : 45);
        ClientboundRotateHeadPacket head = new ClientboundRotateHeadPacket(npc, fixed);
        for (ServerPlayer viewer : viewers.keySet()) viewer.connection.send(head);
    }

    private ClientboundSetEquipmentPacket equipmentPacket() {
        List<Pair<EquipmentSlot, ItemStack>> list = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) list.add(Pair.of(slot, player.getItemBySlot(slot)));
        return new ClientboundSetEquipmentPacket(npc.getId(), list);
    }

    private void setEquipmentVisibility(boolean visible) {
        if (!visible && hiddenEquipment != null) {
            for (ServerPlayer viewer : viewers.keySet()) viewer.connection.send(hiddenEquipment);
            return;
        }
        List<Pair<EquipmentSlot, ItemStack>> list = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) list.add(Pair.of(slot, visible ? player.getItemBySlot(slot) : ItemStack.EMPTY));
        ClientboundSetEquipmentPacket packet = new ClientboundSetEquipmentPacket(player.getId(), list);
        if (!visible) hiddenEquipment = packet;
        for (ServerPlayer viewer : viewers.keySet()) viewer.connection.send(packet);
        if (visible) for (ServerPlayer viewer : level.players()) {
            if (!viewers.containsKey(viewer)) viewer.connection.send(packet);
        }
    }

    private static ClientboundAddEntityPacket addEntity(Entity entity) {
        return new ClientboundAddEntityPacket(entity.getId(), entity.getUUID(), entity.getX(), entity.getY(), entity.getZ(),
                entity.getXRot(), entity.getYRot(), entity.getType(), 0, entity.getDeltaMovement(), entity.getYHeadRot());
    }

    private static void attach(Entity passenger, Entity vehicle) {
        try {
            if (vehicleField == null || passengersField == null) {
                for (Field field : Entity.class.getDeclaredFields()) {
                    if (vehicleField == null && field.getType().equals(Entity.class)) vehicleField = field;
                    if (passengersField == null && field.getType().equals(ImmutableList.class)) passengersField = field;
                }
                vehicleField.setAccessible(true);
                passengersField.setAccessible(true);
            }
            vehicleField.set(passenger, vehicle);
            @SuppressWarnings("unchecked") ImmutableList<Entity> current = (ImmutableList<Entity>) passengersField.get(vehicle);
            passengersField.set(vehicle, ImmutableList.<Entity>builder().addAll(current).add(passenger).build());
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Name tag stays visible if the passenger link cannot be written.
        }
    }

    private static byte fixedRotation(float rotation) { return (byte) (rotation * 256f / 360f); }

    private static Direction directionOf(float yaw) {
        yaw = net.minecraft.util.Mth.wrapDegrees(yaw);
        return (yaw >= 135f || yaw < -135f) ? Direction.NORTH : (yaw >= -135f && yaw < -45f) ? Direction.EAST
                : (yaw >= -45f && yaw < 45f) ? Direction.SOUTH : Direction.WEST;
    }
}
