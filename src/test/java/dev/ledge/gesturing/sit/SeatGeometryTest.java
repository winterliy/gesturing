package dev.ledge.gesturing.sit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;

class SeatGeometryTest {
    @Test
    void fullBlocksUseCenterAndStandardHeight() {
        SeatGeometry.Offset offset = SeatGeometry.offsetFor(shape(SeatGeometry.Kind.FULL, false, BlockFace.SELF, SeatGeometry.StairShape.STRAIGHT));
        assertEquals(0.5, offset.x(), 1e-9);
        assertEquals(1.0, offset.y(), 1e-9);
        assertEquals(0.5, offset.z(), 1e-9);
    }

    @Test
    void slabHalvesUseTheirActualTopSurface() {
        assertEquals(0.5, SeatGeometry.offsetFor(shape(SeatGeometry.Kind.BOTTOM_SLAB, false, BlockFace.SELF, SeatGeometry.StairShape.STRAIGHT)).y(), 1e-9);
        assertEquals(1.0, SeatGeometry.offsetFor(shape(SeatGeometry.Kind.FULL, false, BlockFace.SELF, SeatGeometry.StairShape.STRAIGHT)).y(), 1e-9);
    }

    @Test
    void straightStairsUseStairSeatOffsetForAllFacings() {
        BlockFace[] facings = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        float[] yaws = {0f, 180f, 90f, -90f};
        double[] xs = {0.5, 0.5, 0.377, 0.623};
        double[] zs = {0.623, 0.377, 0.5, 0.5};
        for (int index = 0; index < facings.length; index++) {
            SeatGeometry.Offset offset = SeatGeometry.offsetFor(shape(SeatGeometry.Kind.STAIRS, true, facings[index], SeatGeometry.StairShape.STRAIGHT));
            assertEquals(xs[index], offset.x(), 1e-9);
            assertEquals(0.525, offset.y(), 1e-9);
            assertEquals(zs[index], offset.z(), 1e-9);
            assertEquals(yaws[index], offset.yaw());
        }
    }

    @Test
    void directionalBlocksFaceTheirCardinalDirection() {
        BlockFace[] facings = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        float[] yaws = {180f, 0f, -90f, 90f};
        for (int index = 0; index < facings.length; index++) {
            SeatGeometry.Offset offset = SeatGeometry.offsetFor(shape(SeatGeometry.Kind.DIRECTIONAL, false, facings[index], SeatGeometry.StairShape.STRAIGHT));
            assertEquals(0.5, offset.x(), 1e-9);
            assertEquals(1.0, offset.y(), 1e-9);
            assertEquals(0.5, offset.z(), 1e-9);
            assertEquals(yaws[index], offset.yaw());
        }
    }

    @Test
    void upperStairsAndAllCornerShapesRemainSupported() {
        for (SeatGeometry.StairShape shape : SeatGeometry.StairShape.values()) {
            assertEquals(1.0, SeatGeometry.offsetFor(shape(SeatGeometry.Kind.STAIRS, false, BlockFace.NORTH, shape)).y(), 1e-9);
            assertEquals(0.525, SeatGeometry.offsetFor(shape(SeatGeometry.Kind.STAIRS, true, BlockFace.NORTH, shape)).y(), 1e-9);
        }
        BlockFace[] facings = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        SeatGeometry.StairShape[] shapes = {SeatGeometry.StairShape.OUTER_LEFT, SeatGeometry.StairShape.OUTER_RIGHT, SeatGeometry.StairShape.INNER_LEFT, SeatGeometry.StairShape.INNER_RIGHT};
        boolean[] right = {false, true, false, true};
        for (BlockFace facing : facings) {
            for (int index = 0; index < shapes.length; index++) {
                boolean isRight = right[index];
                double expectedX = switch (facing) {
                    case NORTH -> isRight ? 0.377 : 0.623;
                    case SOUTH -> isRight ? 0.623 : 0.377;
                    case EAST -> 0.377;
                    case WEST -> 0.623;
                    default -> 0.5;
                };
                double expectedZ = switch (facing) {
                    case NORTH -> 0.623;
                    case SOUTH -> 0.377;
                    case EAST, WEST -> isRight ? 0.623 : 0.377;
                    default -> 0.5;
                };
                float expectedYaw = switch (facing) {
                    case NORTH -> isRight ? 45f : -45f;
                    case SOUTH -> isRight ? -135f : 135f;
                    case EAST -> isRight ? 135f : 45f;
                    case WEST -> isRight ? -45f : -135f;
                    default -> 0f;
                };
                SeatGeometry.Offset offset = SeatGeometry.offsetFor(shape(SeatGeometry.Kind.STAIRS, true, facing, shapes[index]));
                assertEquals(expectedX, offset.x(), 1e-9);
                assertEquals(0.525, offset.y(), 1e-9);
                assertEquals(expectedZ, offset.z(), 1e-9);
                assertEquals(expectedYaw, offset.yaw());
            }
        }
    }

    private static SeatGeometry.Shape shape(SeatGeometry.Kind kind, boolean bottomHalf, BlockFace face, SeatGeometry.StairShape stairShape) {
        return new SeatGeometry.Shape(kind, bottomHalf, face, stairShape);
    }
}
