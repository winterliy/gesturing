package dev.ledge.gesturing.fabric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.ModInitializer;
import net.minecraft.command.AbstractCommand;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandRegistry;
import net.minecraft.server.world.ServerWorld;

/**
 * Server-side seating for Minecraft 1.5.2 on Legacy Fabric: /sit and /stand. There is no Legacy Fabric API for 1.5.2,
 * so commands and the server tick are hooked by the mixins in dev.ledge.gesturing.fabric.mixin. Lying poses need
 * player-NPC packets that 1.5.2 does not have in a usable form, so /lay is not offered on this version.
 */
public final class GesturingFabric implements ModInitializer {
    private static final Map<String, State> ACTIVE = new HashMap<>();

    @Override
    public void onInitialize() {
        System.out.println("[Gesturing] Legacy Fabric 0.3.2 initialized for Minecraft 1.5.2.");
    }

    /** Called when the server's command manager is built. */
    public static void registerCommands(CommandRegistry registry) {
        registry.registerCommand(new Cmd("sit", GesturingFabric::sit));
        registry.registerCommand(new Cmd("stand", p -> msg(p, stop(p) ? "동작을 종료했습니다." : "현재 동작 중이 아닙니다.")));
    }

    private interface Action { void run(ServerPlayerEntity player); }

    private static final class Cmd extends AbstractCommand {
        private final String name;
        private final Action action;
        Cmd(String name, Action action) { this.name = name; this.action = action; }
        @Override public String getCommandName() { return name; }
        @Override public String getUsageTranslationKey(CommandSource source) { return "/" + name; }
        @Override public int getPermissionLevel() { return 0; }
        @Override public void execute(CommandSource source, String[] args) {
            if (!(source instanceof ServerPlayerEntity)) { source.method_3331("이 명령어는 플레이어만 사용할 수 있습니다."); return; }
            action.run((ServerPlayerEntity) source);
        }
        @Override public boolean isAccessible(CommandSource source) { return true; }
        @Override public int compareTo(Object other) { return name.compareTo(((net.minecraft.command.Command) other).getCommandName()); }
    }

    private static void msg(ServerPlayerEntity player, String text) { player.method_3331(text); }

    private static void sit(ServerPlayerEntity player) {
        stop(player);
        State state = seat(player);
        if (state == null) { msg(player, "앉을 수 있는 블록을 찾지 못했습니다."); return; }
        ACTIVE.put(player.username, state);
        msg(player, "앉았습니다. 웅크리거나 /stand를 입력하면 일어납니다.");
    }

    /** Mounts the player on a motionless arrow on the supporting block (1.5 has no armor stands). */
    private static State seat(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        int bx = (int) Math.floor(player.x), by = (int) Math.floor(player.y - 0.2d), bz = (int) Math.floor(player.z);
        int id = world.getBlock(bx, by, bz);
        if (id == 0 || world.getMaterial(bx, by, bz).isFluid()) return null;
        SeatGeometry.Offset offset = SeatGeometry.offsetFor(world, bx, by, bz);
        double x = bx + offset.x, y = by + offset.y - 0.025d, z = bz + offset.z;
        float yaw = offset.yaw != 0f ? offset.yaw : player.yaw;
        SeatEntity seat = new SeatEntity(world, x, y, z);
        seat.refreshPositionAndAngles(x, y, z, yaw, 0f);
        if (!world.spawnEntity(seat)) return null;
        double[] back = {player.x, player.y, player.z};
        player.startRiding(seat);
        if (player.vehicle != seat) { seat.remove(); return null; }
        return new State(seat, back);
    }

    /** Motionless arrow used as the rideable seat. */
    private static final class SeatEntity extends AbstractArrowEntity {
        SeatEntity(net.minecraft.world.World world, double x, double y, double z) { super(world, x, y, z); }
        @Override public void tick() { }
    }

    private static boolean stop(ServerPlayerEntity player) {
        State state = ACTIVE.remove(player.username);
        if (state == null) return false;
        if (player.vehicle == state.seat) player.startRiding(null);
        if (state.seat.isAlive()) state.seat.remove();
        if (!player.removed && player.field_2823 != null) {
            player.field_2823.requestTeleport(state.back[0], state.back[1], state.back[2], player.yaw, player.pitch);
        }
        return true;
    }

    /** Called at the end of every server tick. */
    public static void tick(MinecraftServer server) {
        for (Map.Entry<String, State> entry : new ArrayList<>(ACTIVE.entrySet())) {
            State state = entry.getValue();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || player.removed) { ACTIVE.remove(entry.getKey()); state.seat.remove(); continue; }
            if (player.isSneaking() || player.vehicle != state.seat || player.hurtTime > 0) { stop(player); continue; }
            state.seat.yaw = player.yaw;
        }
    }

    private static final class State {
        final SeatEntity seat; final double[] back;
        State(SeatEntity seat, double[] back) { this.seat = seat; this.back = back; }
    }
}
