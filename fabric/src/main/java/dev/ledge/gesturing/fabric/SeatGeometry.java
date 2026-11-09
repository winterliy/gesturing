package dev.ledge.gesturing.fabric;

import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;

/** Shape-aware block seating, adapted from GSit 3.8.0's SitService (GPL-3.0-only); 1.12 has no stair corner states. */
public final class SeatGeometry {
    private static final double STAIR_OFFSET = 0.123d;

    private SeatGeometry() { }

    public static Offset offsetFor(BlockState state) {
        if (state.getBlock() instanceof SlabBlock) {
            return state.get(SlabBlock.HALF) == SlabBlock.SlabType.BOTTOM ? new Offset(0.5d, 0.5d, 0.5d, 0f, false) : new Offset(0.5d, 1.0d, 0.5d, 0f, false);
        }
        if (state.getBlock() instanceof StairsBlock && state.get(StairsBlock.HALF) == StairsBlock.Half.BOTTOM) {
            return stair(state.get(StairsBlock.FACING).getOpposite());
        }
        Direction facing = horizontalFacing(state);
        if (facing != null) return new Offset(0.5d, 1.0d, 0.5d, yawFor(facing), true);
        return new Offset(0.5d, 1.0d, 0.5d, 0f, false);
    }

    private static Offset stair(Direction back) {
        double x = 0.5d, z = 0.5d;
        float yaw = 0f;
        switch (back) {
            case EAST: x += STAIR_OFFSET; yaw = -90f; break;
            case SOUTH: z += STAIR_OFFSET; yaw = 0f; break;
            case WEST: x -= STAIR_OFFSET; yaw = 90f; break;
            default: z -= STAIR_OFFSET; yaw = 180f; break;
        }
        return new Offset(x, 0.525d, z, yaw, true);
    }

    private static Direction horizontalFacing(BlockState state) {
        for (Object property : state.getProperties()) {
            net.minecraft.state.property.Property<?> p = (net.minecraft.state.property.Property<?>) property;
            if (!p.getName().equals("facing")) continue;
            Object value = state.get(p);
            if (value instanceof Direction && ((Direction) value).getAxis().isHorizontal()) return (Direction) value;
        }
        return null;
    }

    static float yawFor(Direction direction) {
        switch (direction) {
            case NORTH: return 180f;
            case EAST: return -90f;
            case WEST: return 90f;
            default: return 0f;
        }
    }

    public static final class Offset {
        public final double x, y, z;
        public final float yaw;
        public final boolean directional;
        Offset(double x, double y, double z, float yaw, boolean directional) {
            this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.directional = directional;
        }
    }
}
