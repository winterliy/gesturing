package dev.ledge.gesturing.fabric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.block.BlockState;
import net.minecraft.class_3915;
import net.minecraft.server.command.CommandManager;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Server-side gestures for Minecraft 1.13.2 on Legacy Fabric (mixin hooks, no Legacy Fabric API): /sit, /lay, /bellyflop, /legsup, /stand. */
public final class GesturingFabric implements ModInitializer {
    private static final GesturingFabric INSTANCE = new GesturingFabric();
    private final Map<UUID, State> active = new HashMap<>();

    @Override
    public void onInitialize() {
        System.out.println("[Gesturing] Legacy Fabric 0.3.2 initialized for Minecraft 1.13.2.");
    }

    /** Called from CommandManagerMixin. */
    public static void registerCommands(CommandDispatcher<class_3915> d) { INSTANCE.register(d); }

    /** Called from MinecraftServerMixin at the end of every server tick. */
    public static void onServerTick(MinecraftServer server) { INSTANCE.tick(server); }

    private void register(CommandDispatcher<class_3915> d) {
        simple(d, "sit", (p, a) -> sit(p));
        d.register(CommandManager.method_17529("lay")
                .executes(c -> run(c, (p, a) -> lay(p, a), new String[0]))
                .then(CommandManager.method_17530("direction", StringArgumentType.word())
                        .executes(c -> run(c, (p, a) -> lay(p, a), new String[] { StringArgumentType.getString(c, "direction") }))));
        simple(d, "bellyflop", (p, a) -> npcPose(p, PoseNpc.Kind.BELLYFLOP, null, "엎드렸습니다."));
        simple(d, "legsup", (p, a) -> npcPose(p, PoseNpc.Kind.LEGS_UP, null, "다리를 올리고 누웠습니다."));
        simple(d, "stand", (p, a) -> { if (!stop(p, false)) msg(p, "현재 동작 중이 아닙니다."); else msg(p, "동작을 종료했습니다."); });
    }

    private interface Action { void run(ServerPlayerEntity player, String[] args); }

    private void simple(CommandDispatcher<class_3915> d, String name, Action action) {
        d.register(CommandManager.method_17529(name).executes(c -> run(c, action, new String[0])));
    }

    /** class_3915 = ServerCommandSource; method_17469 = getEntity, method_17458 = sendError. */
    private static int run(CommandContext<class_3915> c, Action action, String[] args) {
        if (!(c.getSource().method_17469() instanceof ServerPlayerEntity)) {
            c.getSource().method_17458(new LiteralText("이 명령어는 플레이어만 사용할 수 있습니다."));
            return 0;
        }
        action.run((ServerPlayerEntity) c.getSource().method_17469(), args);
        return 1;
    }

    private static void msg(ServerPlayerEntity player, String text) { player.sendMessage(new LiteralText(text), false); }

    private void sit(ServerPlayerEntity player) {
        stop(player, false);
        State state = seat(player, null);
        if (state == null) { msg(player, "앉을 수 있는 블록을 찾지 못했습니다."); return; }
        active.put(player.getUuid(), state);
        msg(player, "앉았습니다. 웅크리거나 /stand를 입력하면 일어납니다.");
    }

    private void lay(ServerPlayerEntity player, String[] args) {
        Float yaw = null;
        if (args.length == 1) {
            switch (args[0].toLowerCase(java.util.Locale.ROOT)) {
                case "south": yaw = 0f; break;
                case "west": yaw = 90f; break;
                case "north": yaw = 180f; break;
                case "east": yaw = -90f; break;
                default: msg(player, "방향은 east, west, south, north 중 하나여야 합니다."); return;
            }
            player.updatePositionAndAngles(player.x, player.y, player.z, yaw, player.pitch);
            player.networkHandler.requestTeleport(player.x, player.y, player.z, yaw, player.pitch);
        } else if (args.length > 1) { msg(player, "사용법: /lay [east|west|south|north]"); return; }
        npcPose(player, PoseNpc.Kind.LAY, yaw, "누웠습니다.");
    }

    private void npcPose(ServerPlayerEntity player, PoseNpc.Kind kind, Float yaw, String message) {
        stop(player, false);
        State state = seat(player, yaw);
        if (state == null) { msg(player, "앉을 수 있는 블록을 찾지 못했습니다."); return; }
        PoseNpc npc = new PoseNpc(player, state.seatPos, state.seatYaw, kind);
        npc.spawn();
        state.npc = npc;
        active.put(player.getUuid(), state);
        msg(player, message + " 웅크리거나 /stand를 입력하면 일어납니다.");
    }

    /** Mounts the player on an invisible armor stand on the supporting block. */
    private State seat(ServerPlayerEntity player, Float yawOverride) {
        ServerWorld world = player.getServerWorld();
        BlockPos support = new BlockPos(player.x, player.y - 0.2d, player.z);
        BlockState blockState = world.getBlockState(support);
        if (blockState.getBlock() == net.minecraft.block.Blocks.AIR || blockState.getBlock().getMaterial(blockState).isFluid()) return null;
        SeatGeometry.Offset offset = SeatGeometry.offsetFor(blockState);
        double x = support.getX() + offset.x, y = support.getY() + offset.y - 0.025d, z = support.getZ() + offset.z;
        float yaw = offset.directional ? offset.yaw : yawOverride != null ? yawOverride : player.yaw;
        ArmorStandEntity seat = new ArmorStandEntity(world, x, y, z);
        configureSeat(seat, yaw);
        if (!world.method_3686(seat)) return null;
        Vec3d back = new Vec3d(player.x, player.y, player.z);
        if (!player.startRiding(seat, true)) { seat.remove(); return null; }
        return new State(seat, back, new Vec3d(x, y, z), yaw);
    }

    private static void configureSeat(ArmorStandEntity seat, float yaw) {
        seat.setInvisible(true);
        seat.setNoGravity(true);
        net.minecraft.nbt.NbtCompound tag = new net.minecraft.nbt.NbtCompound();
        seat.toNbt(tag);
        tag.putBoolean("Marker", true);
        tag.putBoolean("Small", true);
        tag.putBoolean("NoBasePlate", true);
        seat.fromNbt(tag);
        seat.updatePositionAndAngles(seat.x, seat.y, seat.z, yaw, 0f);
    }

    private boolean stop(ServerPlayerEntity player, boolean unused) {
        State state = active.remove(player.getUuid());
        if (state == null) return false;
        if (state.npc != null) state.npc.remove();
        player.stopRiding();
        if (state.seat.isAlive()) state.seat.remove();
        if (!player.removed && player.networkHandler != null) {
            player.networkHandler.requestTeleport(state.back.x, state.back.y, state.back.z, player.yaw, player.pitch);
        }
        return true;
    }

    private void tick(MinecraftServer server) {
        for (Map.Entry<UUID, State> entry : new HashMap<>(active).entrySet()) {
            ServerPlayerEntity player = (ServerPlayerEntity) server.getPlayerManager().getPlayer(entry.getKey());
            State state = entry.getValue();
            if (player == null || player.removed) { active.remove(entry.getKey()); if (state.npc != null) state.npc.remove(); state.seat.remove(); continue; }
            if (player.isSneaking() || player.getVehicle() != state.seat || player.hurtTime > 0) { stop(player, false); continue; }
            if (state.npc != null) state.npc.tick();
            state.seat.yaw = player.yaw;
        }
    }

    private static final class State {
        final ArmorStandEntity seat; final Vec3d back; final Vec3d seatPos; final float seatYaw; PoseNpc npc;
        State(ArmorStandEntity seat, Vec3d back, Vec3d seatPos, float seatYaw) { this.seat = seat; this.back = back; this.seatPos = seatPos; this.seatYaw = seatYaw; }
    }
}
