package dev.ledge.gesturing.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.ledge.gesturing.fabric.SeatGeometry.Offset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class GesturingFabric implements ModInitializer {
    private final Map<UUID, GestureState> active = new HashMap<>();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerCommands(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (entity instanceof ServerPlayer player) stop(player, false);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stop(handler.player, false));
        System.out.println("[Gesturing] Fabric 0.3.1 initialized for Minecraft 26.3.");
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sit")
                .executes(context -> sit(context.getSource()))
                .then(Commands.literal("toggle").executes(context -> toggleSit(context.getSource())))
                .then(Commands.literal("playertoggle").executes(context -> message(context.getSource(), "플레이어 좌석 기능은 서버용 Fabric 판에서 지원하지 않습니다."))));
        dispatcher.register(Commands.literal("lay")
                .then(Commands.argument("direction", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (String direction : new String[]{"east", "west", "south", "north"}) builder.suggest(direction);
                            return builder.buildFuture();
                        })
                        .executes(context -> lay(context.getSource(), StringArgumentType.getString(context, "direction")))));
        dispatcher.register(Commands.literal("bellyflop").executes(context -> pose(context.getSource(), Pose.SWIMMING, "엎드렸습니다.")));
        dispatcher.register(Commands.literal("legsup").executes(context -> pose(context.getSource(), Pose.SLEEPING, "다리를 올리고 누웠습니다.")));
        dispatcher.register(Commands.literal("spin").executes(context -> pose(context.getSource(), Pose.SPIN_ATTACK, "회전을 시작했습니다.")));
        dispatcher.register(Commands.literal("crawl").executes(context -> toggleCrawl(context.getSource())));
        dispatcher.register(Commands.literal("stand").executes(context -> stand(context.getSource())));
    }

    private int sit(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        stop(player, false);
        BlockPos support = player.blockPosition().below();
        ServerLevel level = player.level();
        BlockState state = level.getBlockState(support);
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            player.sendSystemMessage(Component.literal("앉을 수 있는 블록을 찾지 못했습니다."));
            return 0;
        }
        Offset offset = SeatGeometry.offsetFor(state);
        float yaw = state.getBlock() instanceof StairBlock ? offset.yaw() : player.getYRot();
        double x = support.getX() + offset.x();
        double y = support.getY() + offset.y() - 0.025d;
        double z = support.getZ() + offset.z();
        ArmorStand seat = new SeatEntity(level, x, y, z);
        configureSeat(seat, yaw);
        Entity rider = player;
        if (!rider.startRiding(seat, true, true) || !level.addFreshEntity(seat)) {
            rider.stopRiding();
            seat.discard();
            player.sendSystemMessage(Component.literal("좌석을 만들지 못했습니다."));
            return 0;
        }
        GestureState stateBeforeSit = GestureState.sitting(player.getPose(), seat, player.position());
        active.put(player.getUUID(), stateBeforeSit);
        player.sendSystemMessage(Component.literal("앉았습니다. 웅크리거나 /stand를 입력하면 일어납니다."));
        return 1;
    }

    private int toggleSit(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GestureState state = active.get(player.getUUID());
        if (state != null && state.seat() != null) return stand(source);
        return sit(source);
    }

    private int lay(CommandSourceStack source, String rawDirection) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String direction = rawDirection.toLowerCase(Locale.ROOT);
        Float yaw = switch (direction) {
            case "south" -> 0.0f;
            case "west" -> 90.0f;
            case "north" -> 180.0f;
            case "east" -> -90.0f;
            default -> null;
        };
        if (yaw == null) return message(source, "방향은 east, west, south, north 중 하나여야 합니다.");
        ServerPlayer player = source.getPlayerOrException();
        player.setYRot(yaw);
        player.setXRot(0.0f);
        return pose(player, Pose.SLEEPING, direction + " 방향으로 누웠습니다.");
    }

    private int pose(CommandSourceStack source, Pose pose, String message) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return pose(source.getPlayerOrException(), pose, message);
    }

    private int pose(ServerPlayer player, Pose pose, String message) {
        stop(player, false);
        active.put(player.getUUID(), GestureState.pose(player.getPose(), pose, player.position()));
        player.setPose(pose);
        player.sendSystemMessage(Component.literal(message + " 이동하거나 웅크리면 종료됩니다."));
        return 1;
    }

    private int toggleCrawl(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GestureState state = active.get(player.getUUID());
        if (state != null && state.pose() == Pose.SWIMMING) return stand(source);
        return pose(player, Pose.SWIMMING, "기어갑니다.");
    }

    private int stand(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (stop(player, true)) return 1;
        player.sendSystemMessage(Component.literal("현재 동작 중이 아닙니다."));
        return 0;
    }

    private int message(CommandSourceStack source, String text) {
        source.sendFailure(Component.literal(text));
        return 0;
    }

    private boolean stop(ServerPlayer player, boolean notify) {
        GestureState state = active.remove(player.getUUID());
        if (state == null) return false;
        if (state.seat() != null) {
            player.stopRiding();
            if (state.seat().isAlive()) state.seat().discard();
        }
        if (!player.isRemoved()) player.setPose(state.originalPose());
        if (notify) player.sendSystemMessage(Component.literal("동작을 종료했습니다."));
        return true;
    }

    private void tick(MinecraftServer server) {
        for (Map.Entry<UUID, GestureState> entry : Map.copyOf(active).entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) continue;
            GestureState state = entry.getValue();
            if (player.isShiftKeyDown() || (state.seat() == null && player.position().distanceToSqr(state.startedAt()) > 0.01d)) {
                stop(player, false);
            } else if (state.seat() != null && state.seat().getFirstPassenger() != null) {
                state.seat().setYRot(player.getYRot());
                state.seat().setYBodyRot(player.getYRot());
            }
        }
    }

    private static void configureSeat(ArmorStand seat, float yaw) {
        seat.setInvisible(true);
        seat.setNoGravity(true);
        seat.setNoBasePlate(true);
        seat.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS,
                (byte) (ArmorStand.CLIENT_FLAG_MARKER | ArmorStand.CLIENT_FLAG_SMALL | ArmorStand.CLIENT_FLAG_NO_BASEPLATE));
        seat.setYRot(yaw);
        seat.setYBodyRot(yaw);
    }

    private record GestureState(Pose originalPose, Pose pose, ArmorStand seat, net.minecraft.world.phys.Vec3 startedAt) {
        static GestureState sitting(Pose original, ArmorStand seat, net.minecraft.world.phys.Vec3 start) {
            return new GestureState(original, Pose.SITTING, seat, start);
        }
        static GestureState pose(Pose original, Pose pose, net.minecraft.world.phys.Vec3 start) {
            return new GestureState(original, pose, null, start);
        }
    }
}
