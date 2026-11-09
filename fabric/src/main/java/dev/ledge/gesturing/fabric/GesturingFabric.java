package dev.ledge.gesturing.fabric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.legacyfabric.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.legacyfabric.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.command.AbstractCommand;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/** Server-side gestures for Minecraft 1.8.9 on Legacy Fabric: /sit, /lay, /bellyflop, /legsup, /spin, /stand. */
public final class GesturingFabric implements ModInitializer {
    private final Map<UUID, State> active = new HashMap<>();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::registerCommands);
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        System.out.println("[Gesturing] Legacy Fabric 0.3.2 initialized for Minecraft 1.8.9.");
    }

    private void registerCommands(MinecraftServer server) {
        net.minecraft.server.command.CommandManager manager = (net.minecraft.server.command.CommandManager) server.getCommandManager();
        manager.registerCommand(new Cmd("sit", (p, a) -> sit(p)));
        manager.registerCommand(new Cmd("lay", (p, a) -> lay(p, a)));
        manager.registerCommand(new Cmd("stand", (p, a) -> { if (!stop(p, false)) msg(p, "현재 동작 중이 아닙니다."); else msg(p, "동작을 종료했습니다."); }));
    }

    private interface Action { void run(ServerPlayerEntity player, String[] args); }

    private static final class Cmd extends AbstractCommand {
        private final String name;
        private final Action action;
        Cmd(String name, Action action) { this.name = name; this.action = action; }
        @Override public String getCommandName() { return name; }
        @Override public String getUsageTranslationKey(CommandSource source) { return "/" + name; }
        @Override public int getPermissionLevel() { return 0; }
        @Override public void execute(CommandSource source, String[] args) throws CommandException {
            if (!(source.getEntity() instanceof ServerPlayerEntity)) throw new CommandException("이 명령어는 플레이어만 사용할 수 있습니다.");
            action.run((ServerPlayerEntity) source.getEntity(), args);
        }
        @Override public boolean isAccessible(CommandSource source) { return true; }
    }

    private static void msg(ServerPlayerEntity player, String text) { player.sendMessage(new LiteralText(text)); }

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
        PoseNpc npc = new PoseNpc(player, state.seatPos, state.seatYaw);
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
        if (blockState.getBlock() == net.minecraft.block.Blocks.AIR || blockState.getBlock().getMaterial().isFluid()) return null;
        SeatGeometry.Offset offset = SeatGeometry.offsetFor(blockState);
        double x = support.getX() + offset.x, y = support.getY() + offset.y - 0.025d, z = support.getZ() + offset.z;
        float yaw = offset.directional ? offset.yaw : yawOverride != null ? yawOverride : player.yaw;
        ArmorStandEntity seat = new ArmorStandEntity(world, x, y, z);
        configureSeat(seat, yaw);
        if (!world.spawnEntity(seat)) return null;
        Vec3d back = new Vec3d(player.x, player.y, player.z);
        player.startRiding(seat);
        if (player.vehicle != seat) { seat.remove(); return null; }
        return new State(seat, back, new Vec3d(x, y, z), yaw);
    }

    private static void configureSeat(ArmorStandEntity seat, float yaw) {
        seat.setInvisible(true);
        seat.getDataTracker().setProperty(10, (byte) (1 | 8 | 16)); // small, no base plate, marker
        seat.updatePositionAndAngles(seat.x, seat.y, seat.z, yaw, 0f);
    }

    private boolean stop(ServerPlayerEntity player, boolean unused) {
        State state = active.remove(player.getUuid());
        if (state == null) return false;
        if (state.npc != null) state.npc.remove();
        player.startRiding(null);
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
            if (player.isSneaking() || player.vehicle != state.seat || player.hurtTime > 0) { stop(player, false); continue; }
            if (state.npc != null) state.npc.tick();
            state.seat.yaw = player.yaw;
        }
    }

    private static final class State {
        final ArmorStandEntity seat; final Vec3d back; final Vec3d seatPos; final float seatYaw; PoseNpc npc;
        State(ArmorStandEntity seat, Vec3d back, Vec3d seatPos, float seatYaw) { this.seat = seat; this.back = back; this.seatPos = seatPos; this.seatYaw = seatYaw; }
    }
}
