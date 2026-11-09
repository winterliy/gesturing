package dev.ledge.gesturing.nms.v1_14_R1;

import net.minecraft.server.v1_14_R1.EntityPose;
import org.bukkit.craftbukkit.v1_14_R1.entity.CraftPlayer;
import org.bukkit.entity.Player;

/** Sets the real player's NMS pose (Bukkit has no Entity#setPose on this version). */
public final class PoseAccess {
    private PoseAccess() { }

    public static void setPose(Player player, String poseName) {
        NmsReflect.setPose(((CraftPlayer) player).getHandle(), EntityPose.valueOf(poseName));
    }
}
