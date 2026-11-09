package dev.ledge.gesturing.fabric;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.BedSleepS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.SetPassengersS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Packet-only copy of the player shown in a pose, adapted from GSit 3.8.0 (GPL-3.0-only) for Legacy Fabric 1.12.2.
 * The real player stays on an invisible seat, so no bed screen, darkening or spawn-point change happens.
 */
final class PoseNpc {
    enum Kind { LAY, LEGS_UP, BELLYFLOP, SPIN }

    private static final double BASE_OFFSET = -0.025d;
    private static final TrackedData<Byte> ENTITY_FLAGS = find(Entity.class, 0, Byte.class);
    private static final TrackedData<Byte> LIVING_FLAGS = find(LivingEntity.class, 6, Byte.class);
    private static final TrackedData<Byte> SKIN = find(PlayerEntity.class, 13, Byte.class);
    private static final TrackedData<Byte> MAIN_HAND = find(PlayerEntity.class, 14, Byte.class);
    private static final TrackedData<net.minecraft.nbt.NbtCompound> LEFT_SHOULDER = find(PlayerEntity.class, 15, net.minecraft.nbt.NbtCompound.class);
    private static final TrackedData<net.minecraft.nbt.NbtCompound> RIGHT_SHOULDER = find(PlayerEntity.class, 16, net.minecraft.nbt.NbtCompound.class);

    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final Kind kind;
    private final ServerPlayerEntity npc;
    private final AreaEffectCloudEntity vehicle;
    private final AreaEffectCloudEntity hideName;
    private final BlockPos bedPos;
    private final Direction direction;
    private final boolean lying;
    private final Vec3d seatPos;
    private final Set<ServerPlayerEntity> viewers = new HashSet<>();
    private List<Packet<?>> bundle = new ArrayList<>();
    private List<Packet<?>> hiddenEquipment;
    private float lastYaw = Float.NaN;
    private int age;

    PoseNpc(ServerPlayerEntity player, Vec3d seatPos, float seatYaw, Kind kind) {
        this.player = player;
        this.world = player.getServerWorld();
        this.kind = kind;
        this.seatPos = seatPos;
        this.lying = kind == Kind.LAY || kind == Kind.LEGS_UP;
        bedPos = new BlockPos((int) Math.floor(seatPos.x), 0, (int) Math.floor(seatPos.z));
        GameProfile profile = new GameProfile(UUID.randomUUID(), player.getGameProfile().getName());
        profile.getProperties().putAll(player.getGameProfile().getProperties());
        npc = new ServerPlayerEntity(world.getServer(), world, profile, new net.minecraft.server.network.ServerPlayerInteractionManager(world));
        npc.networkHandler = player.networkHandler;
        double height = seatPos.y - BASE_OFFSET;
        double offset = height;
        if (lying) offset += 0.1125d;
        if (kind == Kind.BELLYFLOP) offset += -0.19d;
        npc.updatePositionAndAngles(seatPos.x, offset, seatPos.z, 0f, 0f);
        direction = Direction.fromRotation(seatYaw);
        if (kind == Kind.LEGS_UP) {
            vehicle = new AreaEffectCloudEntity(world, seatPos.x, height - 0.375d, seatPos.z);
            vehicle.setRadius(0);
            vehicle.setDuration(Integer.MAX_VALUE);
            vehicle.setNoGravity(true);
        } else vehicle = null;
        hideName = new AreaEffectCloudEntity(world, player.x, player.y, player.z);
        hideName.setRadius(0);
        hideName.setDuration(Integer.MAX_VALUE);
        hideName.setNoGravity(true);
    }

    void spawn() {
        DataTracker npcData = npc.getDataTracker();
        DataTracker realData = player.getDataTracker();
        if (kind == Kind.SPIN) npcData.set(LIVING_FLAGS, (byte) 4);
        // 1.12 has no swimming pose: the elytra glide flag gives the same face-down horizontal body.
        if (kind == Kind.BELLYFLOP) npcData.set(ENTITY_FLAGS, (byte) 0x80);
        npcData.set(MAIN_HAND, realData.get(MAIN_HAND));
        npcData.set(SKIN, realData.get(SKIN));
        npcData.set(LEFT_SHOULDER, realData.get(LEFT_SHOULDER));
        npcData.set(RIGHT_SHOULDER, realData.get(RIGHT_SHOULDER));
        realData.set(LEFT_SHOULDER, new net.minecraft.nbt.NbtCompound());
        realData.set(RIGHT_SHOULDER, new net.minecraft.nbt.NbtCompound());
        player.setInvisible(true);

        List<Packet<?>> packets = new ArrayList<>();
        packets.add(new PlayerListS2CPacket(PlayerListS2CPacket.Action.ADD_PLAYER, npc));
        packets.add(new PlayerSpawnS2CPacket(npc));
        if (vehicle != null) {
            attach(npc, vehicle);
            packets.add(new EntitySpawnS2CPacket(vehicle, 3));
            packets.add(new EntityTrackerUpdateS2CPacket(vehicle.getEntityId(), vehicle.getDataTracker(), true));
            packets.add(new SetPassengersS2CPacket(vehicle));
        }
        packets.add(new EntityTrackerUpdateS2CPacket(npc.getEntityId(), npcData, true));
        if (lying) packets.add(new BedSleepS2CPacket(npc, bedPos));
        packets.addAll(equipment(npc.getEntityId(), true));
        bundle = packets;
        addViewer(player);
        for (Object other : world.playerEntities) if (other != player) addViewer((ServerPlayerEntity) other);

        attach(hideName, npc);
        send(player, new EntitySpawnS2CPacket(hideName, 3));
        send(player, new EntityTrackerUpdateS2CPacket(hideName.getEntityId(), hideName.getDataTracker(), true));
        send(player, new SetPassengersS2CPacket(npc));
        setEquipmentVisibility(false);
    }

    void tick() {
        age++;
        for (Object other : world.playerEntities) {
            ServerPlayerEntity viewer = (ServerPlayerEntity) other;
            if (!viewers.contains(viewer)) addViewer(viewer);
        }
        if (kind != Kind.SPIN) updateDirection();
        player.setInvisible(true);
        setEquipmentVisibility(false);
    }

    void remove() {
        for (ServerPlayerEntity viewer : new ArrayList<>(viewers)) removeViewer(viewer);
        viewers.clear();
        if (vehicle != null) send(player, new EntitiesDestroyS2CPacket(vehicle.getEntityId()));
        send(player, new EntitiesDestroyS2CPacket(hideName.getEntityId()));
        if (!player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.INVISIBILITY)) player.setInvisible(false);
        setEquipmentVisibility(true);
        player.getDataTracker().set(LEFT_SHOULDER, npc.getDataTracker().get(LEFT_SHOULDER));
        player.getDataTracker().set(RIGHT_SHOULDER, npc.getDataTracker().get(RIGHT_SHOULDER));
    }

    private void addViewer(ServerPlayerEntity viewer) {
        viewers.add(viewer);
        if (lying) send(viewer, bedBlock());
        for (Packet<?> packet : bundle) send(viewer, packet);
        if (lying) send(viewer, new EntityPositionS2CPacket(npc));
    }

    private void removeViewer(ServerPlayerEntity viewer) {
        send(viewer, new PlayerListS2CPacket(PlayerListS2CPacket.Action.REMOVE_PLAYER, npc));
        send(viewer, new EntitiesDestroyS2CPacket(npc.getEntityId()));
        if (lying) send(viewer, new BlockUpdateS2CPacket(world, bedPos));
    }

    /** Fake bed block: a block-update packet whose state is replaced by the bed head facing the pose direction. */
    private Packet<?> bedBlock() {
        BlockUpdateS2CPacket packet = new BlockUpdateS2CPacket();
        try {
            for (Field field : BlockUpdateS2CPacket.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == BlockPos.class) field.set(packet, bedPos);
                else if (field.getType() == net.minecraft.block.BlockState.class) {
                    net.minecraft.block.BlockState bed = net.minecraft.block.Blocks.BED.getDefaultState()
                            .with(net.minecraft.block.HorizontalFacingBlock.DIRECTION, direction.getOpposite())
                            .with(net.minecraft.block.BedBlock.BED_TYPE, net.minecraft.block.BedBlock.BedBlockType.HEAD);
                    field.set(packet, bed);
                }
            }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        return packet;
    }

    private void updateDirection() {
        float yaw = player.yaw;
        if (lastYaw == yaw) return;
        lastYaw = yaw;
        float adjusted = yaw;
        if (kind != Kind.BELLYFLOP) {
            if (direction == Direction.WEST) adjusted -= 90;
            if (direction == Direction.EAST) adjusted += 90;
            if (direction == Direction.NORTH) adjusted -= 180;
            adjusted = (adjusted < 0f ? 360f + adjusted : adjusted) % 360f;
            adjusted = adjusted >= 315 ? adjusted - 360 : adjusted <= 45 ? adjusted : adjusted >= 180 ? -45 : 45;
        }
        EntitySetHeadYawS2CPacket head = new EntitySetHeadYawS2CPacket(npc, (byte) (adjusted * 256f / 360f));
        for (ServerPlayerEntity viewer : viewers) send(viewer, head);
    }

    private List<Packet<?>> equipment(int entityId, boolean visible) {
        List<Packet<?>> list = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            list.add(new EntityEquipmentUpdateS2CPacket(entityId, slot, visible ? player.getStack(slot) : ItemStack.EMPTY));
        }
        return list;
    }

    private void setEquipmentVisibility(boolean visible) {
        List<Packet<?>> list;
        if (!visible && hiddenEquipment != null) list = hiddenEquipment;
        else {
            list = equipment(player.getEntityId(), visible);
            if (!visible) hiddenEquipment = list;
        }
        for (ServerPlayerEntity viewer : viewers) for (Packet<?> packet : list) send(viewer, packet);
    }

    /** Links {@code passenger} to {@code vehicle} without server-side riding logic (packet-only use). */
    private static void attach(Entity passenger, Entity vehicle) {
        try {
            Field field = Entity.class.getDeclaredField("vehicle");
            field.setAccessible(true);
            field.set(passenger, vehicle);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        try {
            Field list = Entity.class.getDeclaredField("passengerList");
            list.setAccessible(true);
            @SuppressWarnings("unchecked") List<Entity> passengers = (List<Entity>) list.get(vehicle);
            if (!passengers.contains(passenger)) passengers.add(passenger);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    /** Finds a static TrackedData with the given entity-data id (obfuscated field names differ per release). */
    @SuppressWarnings("unchecked")
    private static <T> TrackedData<T> find(Class<?> owner, int id, Class<?> valueType) {
        for (Field field : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != TrackedData.class) continue;
            try {
                field.setAccessible(true);
                TrackedData<?> data = (TrackedData<?>) field.get(null);
                if (data.method_12713() != id) continue;
                java.lang.reflect.Type generic = ((java.lang.reflect.ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
                Class<?> raw = generic instanceof Class ? (Class<?>) generic : (Class<?>) ((java.lang.reflect.ParameterizedType) generic).getRawType();
                if (raw == valueType) return (TrackedData<T>) data;
            } catch (ReflectiveOperationException ignored) { }
        }
        throw new IllegalStateException("No entity-data accessor " + id + " in " + owner.getSimpleName());
    }

    private static void send(ServerPlayerEntity viewer, Packet<?> packet) { viewer.networkHandler.sendPacket(packet); }
}
