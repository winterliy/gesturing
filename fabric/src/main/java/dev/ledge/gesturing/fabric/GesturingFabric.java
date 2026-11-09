package dev.ledge.gesturing.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.ledge.gesturing.fabric.SeatGeometry.Offset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class GesturingFabric implements ModInitializer {
    private final Map<UUID, GestureState> active = new HashMap<>();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> registerCommands(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.player, false));
        System.out.println("[Gesturing] Fabric 0.3.2 initialized for Minecraft 1.17.1.");
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sit")
                .executes(context -> sit(context.getSource()))
                .then(Commands.literal("toggle").executes(context -> toggleSit(context.getSource())))
                .then(Commands.literal("playertoggle").executes(context -> message(context.getSource(), "플레이어 좌석 기능은 서버용 Fabric 판에서 지원하지 않습니다."))));
        dispatcher.register(Commands.literal("lay")
                .executes(context -> lay(context.getSource(), null))
                .then(Commands.argument("direction", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (String direction : new String[]{"east", "west", "south", "north"}) builder.suggest(direction);
                            return builder.buildFuture();
                        })
                        .executes(context -> lay(context.getSource(), StringArgumentType.getString(context, "direction")))));
        dispatcher.register(Commands.literal("bellyflop").executes(context -> npcPose(context.getSource().getPlayerOrException(), PoseNpc.Kind.BELLYFLOP, null, "엎드렸습니다.")));
        dispatcher.register(Commands.literal("legsup").executes(context -> npcPose(context.getSource().getPlayerOrException(), PoseNpc.Kind.LEGS_UP, null, "다리를 올리고 누웠습니다.")));
        dispatcher.register(Commands.literal("spin").executes(context -> npcPose(context.getSource().getPlayerOrException(), PoseNpc.Kind.SPIN, null, "회전을 시작했습니다.")));
        dispatcher.register(Commands.literal("crawl").executes(context -> toggleCrawl(context.getSource())));
        dispatcher.register(Commands.literal("stand").executes(context -> stand(context.getSource())));
    }

    private int sit(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        stop(player, false);
        GestureState state = seat(player, null, Pose.STANDING);
        if (state == null) return 0;
        active.put(player.getUUID(), state);
        player.sendMessage(new net.minecraft.network.chat.TextComponent("앉았습니다. 웅크리거나 /stand를 입력하면 일어납니다."), net.minecraft.Util.NIL_UUID);
        return 1;
    }

    /** Mounts the player on an invisible seat on the supporting block; yawOverride is used for non-directional blocks. */
    private GestureState seat(ServerPlayer player, Float yawOverride, Pose pose) {
        BlockPos support = player.blockPosition().below();
        ServerLevel level = (ServerLevel) player.level;
        BlockState state = level.getBlockState(support);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            player.sendMessage(new net.minecraft.network.chat.TextComponent("앉을 수 있는 블록을 찾지 못했습니다."), net.minecraft.Util.NIL_UUID);
            return null;
        }
        Offset offset = SeatGeometry.offsetFor(state);
        float yaw = SeatGeometry.hasHorizontalFacing(state) ? offset.yaw() : yawOverride != null ? yawOverride : player.getYRot();
        double x = support.getX() + offset.x();
        double y = support.getY() + offset.y() - 0.025d;
        double z = support.getZ() + offset.z();
        Vec3 returnPos = player.position();
        ArmorStand seat = new SeatEntity(level, x, y, z);
        configureSeat(seat, yaw);
        Entity rider = player;
        if (!rider.startRiding(seat, true) || !level.addFreshEntity(seat)) {
            rider.stopRiding();
            seat.discard();
            player.sendMessage(new net.minecraft.network.chat.TextComponent("좌석을 만들지 못했습니다."), net.minecraft.Util.NIL_UUID);
            return null;
        }
        return new GestureState(player.getPose(), pose, seat, returnPos, null, null, new Vec3(x, y, z), yaw);
    }

    private int toggleSit(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GestureState state = active.get(player.getUUID());
        if (state != null && state.seat() != null && state.npc() == null) return stand(source);
        return sit(source);
    }

    private int lay(CommandSourceStack source, String rawDirection) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (rawDirection == null) return npcPose(player, PoseNpc.Kind.LAY, null, "누웠습니다.");
        String direction = rawDirection.toLowerCase(Locale.ROOT);
        Float yaw = switch (direction) {
            case "south" -> 0.0f;
            case "west" -> 90.0f;
            case "north" -> 180.0f;
            case "east" -> -90.0f;
            default -> null;
        };
        if (yaw == null) return message(source, "방향은 east, west, south, north 중 하나여야 합니다.");
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        return npcPose(player, PoseNpc.Kind.LAY, yaw, direction + " 방향으로 누웠습니다.");
    }

    private int npcPose(ServerPlayer player, PoseNpc.Kind kind, Float yaw, String message) {
        stop(player, false);
        GestureState state = seat(player, yaw, kind == PoseNpc.Kind.SPIN ? Pose.SPIN_ATTACK : Pose.SLEEPING);
        if (state == null) return 0;
        PoseNpc npc = new PoseNpc(player, state.seatPos(), state.seatYaw(), kind);
        npc.spawn();
        active.put(player.getUUID(), state.withNpc(npc));
        player.sendMessage(new net.minecraft.network.chat.TextComponent(message + " 웅크리거나 /stand를 입력하면 일어납니다."), net.minecraft.Util.NIL_UUID);
        return 1;
    }

    private int crawl(ServerPlayer player) {
        stop(player, false);
        GestureState state = new GestureState(player.getPose(), Pose.SWIMMING, null, player.position(), null, null, null, 0f);
        BlockPos ceiling = player.blockPosition().above();
        if (player.level.getBlockState(ceiling).isAir()) {
            player.connection.send(new ClientboundBlockUpdatePacket(ceiling, Blocks.BARRIER.defaultBlockState()));
            state = state.withCeiling(ceiling);
        }
        player.setPose(Pose.SWIMMING);
        active.put(player.getUUID(), state);
        player.sendMessage(new net.minecraft.network.chat.TextComponent("기어갑니다. 이동하거나 웅크리면 종료됩니다."), net.minecraft.Util.NIL_UUID);
        return 1;
    }

    private int toggleCrawl(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GestureState state = active.get(player.getUUID());
        if (state != null && state.pose() == Pose.SWIMMING && state.seat() == null) return stand(source);
        return crawl(player);
    }

    private int stand(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (stop(player, true)) return 1;
        player.sendMessage(new net.minecraft.network.chat.TextComponent("현재 동작 중이 아닙니다."), net.minecraft.Util.NIL_UUID);
        return 0;
    }

    private int message(CommandSourceStack source, String text) {
        source.sendFailure(new net.minecraft.network.chat.TextComponent(text));
        return 0;
    }

    private boolean stop(ServerPlayer player, boolean notify) {
        GestureState state = active.remove(player.getUUID());
        if (state == null) return false;
        if (state.npc() != null) state.npc().remove();
        if (state.seat() != null) {
            player.stopRiding();
            if (state.seat().isAlive()) state.seat().discard();
            if (!player.isRemoved() && player.isAlive()) {
                Vec3 back = state.startedAt();
                player.connection.teleport(back.x, back.y, back.z, player.getYRot(), player.getXRot());
            }
        }
        if (state.ceiling() != null && !player.isRemoved()) {
            player.connection.send(new ClientboundBlockUpdatePacket(player.level, state.ceiling()));
        }
        if (!player.isRemoved() && state.seat() == null) player.setPose(state.originalPose());
        if (notify) player.sendMessage(new net.minecraft.network.chat.TextComponent("동작을 종료했습니다."), net.minecraft.Util.NIL_UUID);
        return true;
    }

    private void tick(MinecraftServer server) {
        for (Map.Entry<UUID, GestureState> entry : Map.copyOf(active).entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            GestureState state = entry.getValue();
            boolean dismounted = state.seat() != null && player.getVehicle() != state.seat();
            if (player.hurtTime > 0) { stop(player, false); continue; }
            if (player.isShiftKeyDown() || dismounted
                    || (state.seat() == null && player.position().distanceToSqr(state.startedAt()) > 0.64d)) {
                stop(player, false);
                continue;
            }
            if (state.npc() != null) state.npc().tick();
            if (state.seat() != null) {
                state.seat().setYRot(player.getYRot());
                state.seat().setYBodyRot(player.getYRot());
            }
        }
    }

    private static void configureSeat(ArmorStand seat, float yaw) {
        seat.setInvisible(true);
        seat.setNoGravity(true);
        seat.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS,
                (byte) (ArmorStand.CLIENT_FLAG_MARKER | ArmorStand.CLIENT_FLAG_SMALL | ArmorStand.CLIENT_FLAG_NO_BASEPLATE));
        seat.setYRot(yaw);
        seat.setYBodyRot(yaw);
    }

    private record GestureState(Pose originalPose, Pose pose, ArmorStand seat, Vec3 startedAt,
                                PoseNpc npc, BlockPos ceiling, Vec3 seatPos, float seatYaw) {
        GestureState withNpc(PoseNpc npc) {
            return new GestureState(originalPose, pose, seat, startedAt, npc, ceiling, seatPos, seatYaw);
        }
        GestureState withCeiling(BlockPos ceiling) {
            return new GestureState(originalPose, pose, seat, startedAt, npc, ceiling, seatPos, seatYaw);
        }
    }
}
