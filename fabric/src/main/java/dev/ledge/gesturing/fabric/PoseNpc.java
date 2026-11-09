package dev.ledge.gesturing.fabric;

import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.BedSleepS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySetHeadYawS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerSpawnS2CPacket;
import net.minecraft.server.world.ServerWorld;

/**
 * Packet-only copy of the player lying in a bed pose, adapted from GSit (GPL-3.0-only) for Legacy Fabric 1.8.9.
 * The real player stays on an invisible seat, so no bed screen, darkening or spawn-point change happens.
 */
final class PoseNpc {
    enum Kind { LAY }

    private static final double BASE_OFFSET = -0.025d;

    private final ServerPlayerEntity player;
    private final ServerWorld world;
    private final ServerPlayerEntity npc;
    private final int bedX, bedY, bedZ;
    private final int bedFacing;
    private final Set<ServerPlayerEntity> viewers = new HashSet<>();
    private List<Packet> bundle = new ArrayList<>();
    private List<Packet> hiddenEquipment;
    private float lastYaw = Float.NaN;
    private int intIndex;

    PoseNpc(ServerPlayerEntity player, double[] seatPos, float seatYaw) {
        this.player = player;
        this.world = player.getServerWorld();
        bedX = (int) Math.floor(seatPos[0]); bedY = 0; bedZ = (int) Math.floor(seatPos[2]);
        float yaw = seatYaw % 360f;
        if (yaw >= 180f) yaw -= 360f;
        if (yaw < -180f) yaw += 360f;
        // 1.8 bed meta: bits 0-1 = direction the head points (0 south, 1 west, 2 north, 3 east), bit 3 = head half.
        bedFacing = (yaw >= 135f || yaw < -135f) ? 0 : (yaw >= -135f && yaw < -45f) ? 1 : (yaw >= -45f && yaw < 45f) ? 2 : 3;
        GameProfile profile = new GameProfile(UUID.randomUUID(), player.getGameProfile().getName());
        profile.getProperties().putAll(player.getGameProfile().getProperties());
        npc = new ServerPlayerEntity(world.getServer(), world, profile, new net.minecraft.server.network.ServerPlayerInteractionManager(world));
        npc.networkHandler = player.networkHandler;
        npc.updatePositionAndAngles(seatPos[0], seatPos[1] - BASE_OFFSET + 0.1125d, seatPos[2], 0f, 0f);
    }

    void spawn() {
        DataTracker npcData = npc.getDataTracker();
        npcData.setProperty(10, player.getDataTracker().getByte(10));
        player.setInvisible(true);

        List<Packet> packets = new ArrayList<>();
        packets.add(new PlayerListS2CPacket(npc.getGameProfile().getName(), true, 0));
        packets.add(new PlayerSpawnS2CPacket(npc));
        packets.add(new EntityTrackerUpdateS2CPacket(npc.getEntityId(), npcData, true));
        packets.add(new BedSleepS2CPacket(npc, bedX, bedY, bedZ));
        packets.addAll(equipment(npc.getEntityId(), true));
        bundle = packets;
        addViewer(player);
        for (Object other : world.playerEntities) if (other != player) addViewer((ServerPlayerEntity) other);
        setEquipmentVisibility(false);
    }

    void tick() {
        for (Object other : world.playerEntities) {
            ServerPlayerEntity viewer = (ServerPlayerEntity) other;
            if (!viewers.contains(viewer)) addViewer(viewer);
        }
        updateDirection();
        player.setInvisible(true);
        setEquipmentVisibility(false);
    }

    void remove() {
        for (ServerPlayerEntity viewer : new ArrayList<>(viewers)) removeViewer(viewer);
        viewers.clear();
        player.setInvisible(false);
        setEquipmentVisibility(true);
    }

    private void addViewer(ServerPlayerEntity viewer) {
        viewers.add(viewer);
        send(viewer, bedBlock());
        for (Packet packet : bundle) send(viewer, packet);
        send(viewer, new EntityPositionS2CPacket(npc));
    }

    private void removeViewer(ServerPlayerEntity viewer) {
        send(viewer, new PlayerListS2CPacket(npc.getGameProfile().getName(), false, 0));
        send(viewer, new EntitiesDestroyS2CPacket(npc.getEntityId()));
        send(viewer, new BlockUpdateS2CPacket(bedX, bedY, bedZ, world));
    }

    /** Fake bed block: a block-update packet whose state is replaced by the bed head with the pose's facing. */
    private Packet bedBlock() {
        intIndex = 0;
        BlockUpdateS2CPacket packet = new BlockUpdateS2CPacket();
        try {
            for (Field field : BlockUpdateS2CPacket.class.getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == net.minecraft.block.Block.class) field.set(packet, net.minecraft.block.Blocks.BED);
                else if (field.getType() == int.class) {
                    // the packet stores x, y, z then the block metadata in declaration order
                    switch (field.getName().length() > 0 ? intIndex++ : 0) {
                        case 0: field.setInt(packet, bedX); break;
                        case 1: field.setInt(packet, bedY); break;
                        case 2: field.setInt(packet, bedZ); break;
                        default: field.setInt(packet, bedFacing | 8);
                    }
                }
            }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        return packet;
    }

    private void updateDirection() {
        float yaw = player.yaw;
        if (lastYaw == yaw) return;
        lastYaw = yaw;
        float adjusted = (yaw < 0f ? 360f + yaw : yaw) % 360f;
        adjusted = adjusted >= 315 ? adjusted - 360 : adjusted <= 45 ? adjusted : adjusted >= 180 ? -45 : 45;
        EntitySetHeadYawS2CPacket head = new EntitySetHeadYawS2CPacket(npc, (byte) (adjusted * 256f / 360f));
        for (ServerPlayerEntity viewer : viewers) send(viewer, head);
    }

    private List<Packet> equipment(int entityId, boolean visible) {
        List<Packet> list = new ArrayList<>();
        for (int slot = 0; slot < 5; slot++) {
            list.add(new EntityEquipmentUpdateS2CPacket(entityId, slot, visible ? player.getMainSlot(slot) : null));
        }
        return list;
    }

    private void setEquipmentVisibility(boolean visible) {
        List<Packet> list;
        if (!visible && hiddenEquipment != null) list = hiddenEquipment;
        else {
            list = equipment(player.getEntityId(), visible);
            if (!visible) hiddenEquipment = list;
        }
        for (ServerPlayerEntity viewer : viewers) for (Packet packet : list) send(viewer, packet);
    }

    private static void send(ServerPlayerEntity viewer, Packet packet) { viewer.networkHandler.sendPacket(packet); }
}
