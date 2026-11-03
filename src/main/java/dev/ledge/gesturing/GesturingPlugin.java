package dev.ledge.gesturing;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class GesturingPlugin extends JavaPlugin implements Listener {
    private static final Map<String, Float> DIRECTION_YAWS = Map.of(
            "south", 0.0f,
            "west", 90.0f,
            "north", 180.0f,
            "east", -90.0f
    );
    private static final Set<String> DIRECTIONS = DIRECTION_YAWS.keySet();

    private String gsitCommandPrefix;

    @Override
    public void onEnable() {
        var gsit = getServer().getPluginManager().getPlugin("GSit");
        if (gsit == null || !gsit.isEnabled()) {
            getLogger().severe("GSit is required. Gesturing will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        gsitCommandPrefix = gsit.getName().toLowerCase(Locale.ROOT) + ":";
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Gesturing is enabled with GSit on GLAP1.");
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String[] parts = event.getMessage().trim().split("\\s+");
        if (parts.length == 0) {
            return;
        }

        String command = parts[0].substring(1).toLowerCase(Locale.ROOT);
        if (command.contains(":")) {
            return;
        }

        if (command.equals("sit")) {
            event.setCancelled(true);
            executeGsitCommand(event.getPlayer(), "sit");
        } else if (command.equals("lay")) {
            event.setCancelled(true);
            handleLay(event.getPlayer(), parts);
        }
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player)
                || !event.getBuffer().toLowerCase(Locale.ROOT).matches("^/lay\\s+[^\\s]*$")) {
            return;
        }

        String typed = event.getBuffer().substring(event.getBuffer().indexOf(' ') + 1)
                .toLowerCase(Locale.ROOT);
        event.setCompletions(DIRECTIONS.stream()
                .filter(direction -> direction.startsWith(typed))
                .sorted()
                .toList());
    }

    private void handleLay(Player player, String[] parts) {
        if (parts.length != 2) {
            player.sendMessage(ChatColor.YELLOW + "사용법: /lay <east|west|south|north>");
            return;
        }

        String direction = parts[1].toLowerCase(Locale.ROOT);
        Float yaw = DIRECTION_YAWS.get(direction);
        if (yaw == null) {
            player.sendMessage(ChatColor.RED + "방향은 east, west, south, north 중 하나여야 합니다.");
            return;
        }

        Location facing = player.getLocation();
        player.setRotation(yaw, facing.getPitch());
        executeGsitCommand(player, "lay");
    }

    private void executeGsitCommand(Player player, String command) {
        String namespacedCommand = gsitCommandPrefix + command;
        PluginCommand gsitPluginCommand = getServer().getPluginCommand(namespacedCommand);
        if (gsitPluginCommand == null) {
            player.sendMessage(ChatColor.RED + "GSit 명령어를 찾을 수 없습니다: /" + command);
            getLogger().warning("GSit did not register the expected command: " + namespacedCommand);
            return;
        }

        player.performCommand(namespacedCommand);
    }
}
