package dev.ledge.gesturing.fabric;

import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;

/** Shape-aware block seating, adapted from GSit 3.8.0's 26.3 SitService. */
public final class SeatGeometry {
    private static final double STAIR_OFFSET = 0.123d;

    private SeatGeometry() { }

    public static Offset offsetFor(BlockState state) {
        if (state.getBlock() instanceof SlabBlock) {
            return offsetFor(state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM ? Shape.bottomSlab() : Shape.topSlab());
        }
        if (state.getBlock() instanceof StairBlock) {
            Direction direction = Direction.from(state.getValue(StairBlock.FACING).getName());
            Half half = state.getValue(StairBlock.HALF);
            StairsShape stairsShape = state.getValue(StairBlock.SHAPE);
            if (half == Half.BOTTOM && stairsShape == StairsShape.STRAIGHT) return offsetFor(Shape.straightStair(direction));
            if (half == Half.BOTTOM) {
                Corner corner = switch (stairsShape) {
                    case OUTER_LEFT, INNER_LEFT -> Corner.LEFT;
                    default -> Corner.RIGHT;
                };
                return offsetFor(Shape.cornerStair(direction, corner));
            }
        }
        Direction facing = horizontalFacing(state);
        if (facing != null) return offsetFor(Shape.directional(facing));
        return offsetFor(Shape.full());
    }

    public static boolean hasHorizontalFacing(BlockState state) { return horizontalFacing(state) != null; }

    private static Direction horizontalFacing(BlockState state) {
        for (var property : state.getProperties()) {
            if (!property.getName().equals("facing")) continue;
            Object value = state.getValue(property);
            if (value instanceof net.minecraft.core.Direction direction && direction.getAxis().isHorizontal()) {
                return Direction.from(direction.getName());
            }
        }
        return null;
    }

    public static Offset offsetFor(Shape shape) {
        double x = 0.5d, y = 1.0d, z = 0.5d;
        float yaw = 0.0f;
        if (shape.kind() == Kind.BOTTOM_SLAB) {
            y = 0.5d;
        } else if (shape.kind() == Kind.STAIRS && shape.bottomHalf()) {
            y = 0.525d;
            Direction back = shape.facing().opposite();
            if (shape.corner() == null) {
                switch (back) {
                    case EAST -> { x += STAIR_OFFSET; yaw = -90f; }
                    case SOUTH -> { z += STAIR_OFFSET; yaw = 0f; }
                    case WEST -> { x -= STAIR_OFFSET; yaw = 90f; }
                    case NORTH -> { z -= STAIR_OFFSET; yaw = 180f; }
                }
            } else {
                boolean right = shape.corner() == Corner.RIGHT;
                switch (back) {
                    case NORTH -> { x += right ? STAIR_OFFSET : -STAIR_OFFSET; z -= STAIR_OFFSET; yaw = right ? -135f : 135f; }
                    case EAST -> {
                        x += STAIR_OFFSET;
                        if (right) { z += STAIR_OFFSET; yaw = -45f; }
                        else { z -= STAIR_OFFSET; yaw = -135f; }
                    }
                    case SOUTH -> {
                        z += STAIR_OFFSET;
                        if (right) { x -= STAIR_OFFSET; yaw = 45f; }
                        else { x += STAIR_OFFSET; yaw = -45f; }
                    }
                    case WEST -> {
                        x -= STAIR_OFFSET;
                        if (right) { z += STAIR_OFFSET; yaw = 135f; }
                        else { z -= STAIR_OFFSET; yaw = 45f; }
                    }
                }
            }
        } else if (shape.kind() == Kind.DIRECTIONAL) {
            yaw = shape.facing().yaw();
        }
        return new Offset(x, y, z, yaw);
    }

    public enum Direction {
        NORTH, EAST, SOUTH, WEST;
        Direction opposite() { return values()[(ordinal() + 2) % 4]; }
        static Direction from(String direction) { return valueOf(direction.toUpperCase(java.util.Locale.ROOT)); }
        float yaw() {
            return switch (this) {
                case NORTH -> 180f;
                case EAST -> -90f;
                case SOUTH -> 0f;
                case WEST -> 90f;
            };
        }
    }

    public enum Corner { LEFT, RIGHT }
    public enum Kind { FULL, BOTTOM_SLAB, TOP_SLAB, STAIRS, DIRECTIONAL }

    public static final class Shape {
        private final Kind kind; private final boolean bottomHalf; private final Direction facing; private final Corner corner;
        public Shape(Kind kind, boolean bottomHalf, Direction facing, Corner corner) {
            this.kind = kind; this.bottomHalf = bottomHalf; this.facing = facing; this.corner = corner;
        }
        public Kind kind() { return kind; }
        public boolean bottomHalf() { return bottomHalf; }
        public Direction facing() { return facing; }
        public Corner corner() { return corner; }
        public static Shape full() { return new Shape(Kind.FULL, false, Direction.NORTH, null); }
        public static Shape bottomSlab() { return new Shape(Kind.BOTTOM_SLAB, false, Direction.NORTH, null); }
        public static Shape topSlab() { return new Shape(Kind.TOP_SLAB, false, Direction.NORTH, null); }
        public static Shape straightStair(Direction facing) { return new Shape(Kind.STAIRS, true, facing, null); }
        public static Shape cornerStair(Direction facing, Corner corner) { return new Shape(Kind.STAIRS, true, facing, corner); }
        public static Shape directional(Direction facing) { return new Shape(Kind.DIRECTIONAL, false, facing, null); }
    }

    public static final class Offset {
        private final double x, y, z; private final float yaw;
        public Offset(double x, double y, double z, float yaw) { this.x = x; this.y = y; this.z = z; this.yaw = yaw; }
        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public float yaw() { return yaw; }
    }
}
