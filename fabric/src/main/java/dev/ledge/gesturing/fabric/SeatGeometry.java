package dev.ledge.gesturing.fabric;

import net.minecraft.block.Block;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.world.World;

/** Seat placement adapted from GSit's SitService (GPL-3.0-only) for 1.7.10, where blocks are id + metadata. */
public final class SeatGeometry {
    private static final double STAIR_OFFSET = 0.123d;

    private SeatGeometry() { }

    public static Offset offsetFor(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        int meta = world.getBlockData(x, y, z);
        if (block instanceof SlabBlock && !block.isFullCube()) {
            return (meta & 8) == 0 ? new Offset(0.5d, 0.5d, 0.5d, 0f) : new Offset(0.5d, 1.0d, 0.5d, 0f);
        }
        if (block instanceof StairsBlock && (meta & 4) == 0) {
            // stair meta 0..3 = the direction the stair ascends toward (east, west, south, north); the seat faces back down it
            switch (meta & 3) {
                case 0: return new Offset(0.5d - STAIR_OFFSET, 0.525d, 0.5d, -90f);
                case 1: return new Offset(0.5d + STAIR_OFFSET, 0.525d, 0.5d, 90f);
                case 2: return new Offset(0.5d, 0.525d, 0.5d - STAIR_OFFSET, 180f);
                default: return new Offset(0.5d, 0.525d, 0.5d + STAIR_OFFSET, 0f);
            }
        }
        return new Offset(0.5d, 1.0d, 0.5d, 0f);
    }

    public static final class Offset {
        public final double x, y, z;
        public final float yaw;
        Offset(double x, double y, double z, float yaw) { this.x = x; this.y = y; this.z = z; this.yaw = yaw; }
    }
}
