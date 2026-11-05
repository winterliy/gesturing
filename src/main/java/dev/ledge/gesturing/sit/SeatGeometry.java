package dev.ledge.gesturing.sit;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;

/**
 * Copyright (C) Gecolay and contributors.
 * Shape-aware seat placement adapted from GSit's 26.3 SitService (GPL-3.0-only).
 * Gesturing modifications: compact shape-value API and command-only usage.
 */
public final class SeatGeometry {
    private static final double STAIR_OFFSET = 0.123d;

    private SeatGeometry() { }

    public static Offset offsetFor(BlockData data) {
        if (data instanceof Stairs stairs) {
            return offsetFor(new Shape(Kind.STAIRS, stairs.getHalf() == Bisected.Half.BOTTOM,
                    stairs.getFacing(), stairs.getShape()));
        }
        if (data instanceof Slab slab) {
            return offsetFor(new Shape(slab.getType() == Slab.Type.BOTTOM ? Kind.BOTTOM_SLAB : Kind.FULL,
                    false, BlockFace.SELF, Stairs.Shape.STRAIGHT));
        }
        if (data instanceof Directional directional) {
            return offsetFor(new Shape(Kind.DIRECTIONAL, false, directional.getFacing(), Stairs.Shape.STRAIGHT));
        }
        return offsetFor(new Shape(Kind.FULL, false, BlockFace.SELF, Stairs.Shape.STRAIGHT));
    }

    public static Offset offsetFor(Shape shape) {
        double x = 0.5d, y = 1.0d, z = 0.5d;
        float yaw = 0.0f;
        if (shape.kind() == Kind.BOTTOM_SLAB) {
            y = 0.5d;
        } else if (shape.kind() == Kind.STAIRS && shape.bottomHalf()) {
            y = 0.525d;
            BlockFace back = shape.facing().getOppositeFace();
            if (shape.stairShape() == Stairs.Shape.STRAIGHT) {
                switch (back) {
                    case EAST -> { x += STAIR_OFFSET; yaw = -90f; }
                    case SOUTH -> { z += STAIR_OFFSET; yaw = 0f; }
                    case WEST -> { x -= STAIR_OFFSET; yaw = 90f; }
                    case NORTH -> { z -= STAIR_OFFSET; yaw = 180f; }
                    default -> { }
                }
            } else {
                Stairs.Shape stairShape = shape.stairShape();
                boolean right = stairShape == Stairs.Shape.OUTER_RIGHT || stairShape == Stairs.Shape.INNER_RIGHT;
                if (back == BlockFace.NORTH) {
                    x += right ? STAIR_OFFSET : -STAIR_OFFSET; z -= STAIR_OFFSET; yaw = right ? -135f : 135f;
                } else if (back == BlockFace.EAST) {
                    x += STAIR_OFFSET;
                    if (right) { z += STAIR_OFFSET; yaw = -45f; }
                    else { z -= STAIR_OFFSET; yaw = -135f; }
                } else if (back == BlockFace.SOUTH) {
                    z += STAIR_OFFSET;
                    if (right) { x -= STAIR_OFFSET; yaw = 45f; }
                    else { x += STAIR_OFFSET; yaw = -45f; }
                } else if (back == BlockFace.WEST) {
                    x -= STAIR_OFFSET;
                    if (right) { z += STAIR_OFFSET; yaw = 135f; }
                    else { z -= STAIR_OFFSET; yaw = 45f; }
                }
            }
        } else if (shape.kind() == Kind.DIRECTIONAL) {
            yaw = yawFor(shape.facing());
        }
        return new Offset(x, y, z, yaw);
    }

    private static float yawFor(BlockFace face) {
        return switch (face) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
            default -> 0f;
        };
    }

    public enum Kind { FULL, BOTTOM_SLAB, STAIRS, DIRECTIONAL }
    public record Shape(Kind kind, boolean bottomHalf, BlockFace facing, Stairs.Shape stairShape) { }
    public record Offset(double x, double y, double z, float yaw) { }
}
