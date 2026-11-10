package dev.ledge.gesturing;

import dev.ledge.gesturing.sit.SeatManager;
import dev.ledge.gesturing.gesture.GestureDirection;
import dev.ledge.gesturing.gesture.PoseKind;
import dev.ledge.gesturing.nms.v26_3.PoseNpc;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GesturingPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Pose> originalPoses = new HashMap<>();
    private final Map<UUID, Gesture> active = new HashMap<>();
    private final Set<UUID> playerSitOptOut = new HashSet<>();
    private final SeatManager seats = new SeatManager();
    private final Map<UUID, PoseNpc> poseNpcs = new HashMap<>();
    private final Map<UUID, Location> anchors = new HashMap<>();
    private final Map<UUID, Location> ceilings = new HashMap<>();

    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        for (String command : new String[]{"sit", "lay", "bellyflop", "legsup", "spin", "crawl", "stand"}) {
            var registered = getCommand(command);
            if (registered == null) throw new IllegalStateException("Missing command in plugin.yml: " + command);
            registered.setExecutor(this);
            if (command.equals("lay")) registered.setTabCompleter(this);
        }
        getLogger().info("Gesturing " + getDescription().getVersion() + " enabled for Paper " + org.bukkit.Bukkit.getBukkitVersion() + ".");
    }

    @Override public void onDisable() {
        for (Player player : getServer().getOnlinePlayers()) stop(player, false);
        seats.clear();
    }

    @Override public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "sit" -> {
                if (args.length > 0 && args[0].equalsIgnoreCase("playertoggle")) {
                    boolean optedOut;
                    if (playerSitOptOut.remove(player.getUniqueId())) optedOut = false;
                    else { playerSitOptOut.add(player.getUniqueId()); optedOut = true; }
                    player.sendMessage(optedOut ? "다른 플레이어가 위에 앉을 수 없습니다." : "다른 플레이어가 위에 앉을 수 있습니다.");
                } else if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
                    if (seats.isSitting(player)) stop(player, true);
                    else startSit(player);
                } else if (args.length == 0) startSit(player);
                else player.sendMessage(ChatColor.YELLOW + "사용법: /sit [toggle|playertoggle]");
            }
            case "lay" -> startPose(player, Pose.SLEEPING, args, "사용법: /lay [east|west|south|north]", Gesture.LAY);
            case "bellyflop" -> startPose(player, Pose.SWIMMING, new String[0], null, Gesture.BELLYFLOP);
            case "legsup" -> startPose(player, Pose.SLEEPING, new String[0], null, Gesture.LEGS_UP);
            case "spin" -> startPose(player, Pose.SPIN_ATTACK, new String[0], null, Gesture.SPIN);
            case "crawl" -> {
                if (active.get(player.getUniqueId()) == Gesture.CRAWL) stop(player, true);
                else startPose(player, Pose.SWIMMING, new String[0], null, Gesture.CRAWL);
            }
            case "stand" -> stop(player, true);
            default -> { return false; }
        }
        return true;
    }

    private void startSit(Player player) {
        stop(player, false);
        remember(player);
        if (!seats.sitOnSupportingBlock(player)) {
            originalPoses.remove(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "앉을 수 있는 블록을 찾지 못했습니다.");
            return;
        }
        active.put(player.getUniqueId(), Gesture.SIT);
        player.sendMessage(ChatColor.GREEN + "앉았습니다. 이동하거나 웅크리면 일어납니다.");
    }

    private void startPose(Player player, Pose pose, String[] args, @Nullable String usage, Gesture gesture) {
        boolean explicitDirection = usage != null && args.length > 0;
        if (explicitDirection && (args.length != 1 || GestureDirection.parse(args[0]).isEmpty())) {
            player.sendMessage(ChatColor.YELLOW + usage);
            return;
        }
        stop(player, false);
        remember(player);
        if (explicitDirection) {
            { org.bukkit.Location look = player.getLocation(); look.setYaw(GestureDirection.parse(args[0]).orElseThrow().yaw()); player.teleport(look); } // setRotation rejects players before 1.19
        }
        PoseKind npcKind = switch (gesture) {
            case LAY -> PoseKind.LAY; case LEGS_UP -> PoseKind.LEGS_UP;
            case BELLYFLOP -> PoseKind.BELLYFLOP; case SPIN -> PoseKind.SPIN; default -> null;
        };
        if (npcKind != null) {
            float yaw = explicitDirection ? GestureDirection.parse(args[0]).orElseThrow().yaw() : player.getLocation().getYaw();
            if (!seats.sitOnSupportingBlock(player, yaw)) {
                originalPoses.remove(player.getUniqueId());
                player.sendMessage(ChatColor.RED + "자세를 취할 블록을 찾지 못했습니다.");
                return;
            }
            PoseNpc npc = new PoseNpc(this, player, seats.seatOf(player), seats.seatLocationOf(player), npcKind);
            npc.spawn();
            poseNpcs.put(player.getUniqueId(), npc);
            active.put(player.getUniqueId(), gesture);
            player.sendMessage(ChatColor.GREEN + "동작을 시작했습니다. 이동하거나 웅크리면 종료됩니다.");
            return;
        }
        switch (gesture) {
            case CRAWL -> {
                Location ceiling = player.getLocation().getBlock().getRelative(BlockFace.UP).getLocation();
                if (ceiling.getBlock().isEmpty()) {
                    player.sendBlockChange(ceiling, Material.BARRIER.createBlockData());
                    ceilings.put(player.getUniqueId(), ceiling);
                }
                setPose(player, pose);
            }
            case SPIN -> {
                setPose(player, pose);
            }
            default -> setPose(player, pose);
        }
        anchors.put(player.getUniqueId(), player.getLocation());
        active.put(player.getUniqueId(), gesture);
        player.sendMessage(ChatColor.GREEN + "동작을 시작했습니다. 이동하거나 웅크리면 종료됩니다.");
    }

    private void cleanupPose(Player player) {
        UUID id = player.getUniqueId();
        anchors.remove(id);
        PoseNpc npc = poseNpcs.remove(id);
        if (npc != null) npc.remove();
        Location ceiling = ceilings.remove(id);
        if (ceiling != null && player.isOnline()) player.sendBlockChange(ceiling, ceiling.getBlock().getBlockData());
    }

    private void remember(Player player) { originalPoses.putIfAbsent(player.getUniqueId(), player.getPose()); }

    private void stop(Player player, boolean message) {
        UUID id = player.getUniqueId();
        boolean wasActive = active.remove(id) != null;
        seats.stop(player);
        cleanupPose(player);
        Pose old = originalPoses.remove(id);
        if (old != null && player.isOnline() && !player.isDead()) setPose(player, old);
        if (message) player.sendMessage(wasActive ? ChatColor.GREEN + "동작을 종료했습니다." : ChatColor.YELLOW + "현재 동작 중이 아닙니다.");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player target)) return;
        Player rider = event.getPlayer();
        if (playerSitOptOut.contains(target.getUniqueId()) || rider == target) return;
        stop(rider, false);
        remember(rider);
        if (seats.sitOnPlayer(rider, target)) {
            active.put(rider.getUniqueId(), Gesture.PLAYER_SIT);
            event.setCancelled(true);
        } else originalPoses.remove(rider.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Location anchor = anchors.get(event.getPlayer().getUniqueId());
        if (anchor != null) {
            Location to = event.getTo();
            if (to.getWorld() != anchor.getWorld() || to.distanceSquared(anchor) > 0.64) stop(event.getPlayer(), false);
        } else if (moved(event.getFrom(), event.getTo())) stop(event.getPlayer(), false);
    }
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { stop(event.getPlayer(), false); }
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) { if (event.isSneaking()) stop(event.getPlayer(), false); }
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) { if (event.getEntity() instanceof Player player) stop(player, false); }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        seats.stopPlayersSittingOn(event.getPlayer());
        stop(event.getPlayer(), false);
    }

    @Override public @Nullable java.util.List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                                     @NotNull String alias, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("lay") || args.length != 1) return java.util.List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(GestureDirection.values()).map(direction -> direction.name().toLowerCase(Locale.ROOT))
                .filter(direction -> direction.startsWith(prefix)).sorted().toList();
    }

    private boolean moved(Location from, Location to) {
        return from.getWorld() != to.getWorld() || from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
    }

    private enum Gesture { SIT, PLAYER_SIT, LAY, BELLYFLOP, LEGS_UP, SPIN, CRAWL }

    /** Bukkit 1.19.4 has no Entity#setPose, so set the NMS pose directly. */
    private static void setPose(Player player, Pose pose) {
        ((org.bukkit.craftbukkit.v1_17_R1.entity.CraftPlayer) player).getHandle()
                .setPose(net.minecraft.world.entity.Pose.valueOf(pose.name()));
    }
}
