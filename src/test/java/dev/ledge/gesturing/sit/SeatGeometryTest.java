package dev.ledge.gesturing.sit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Stairs;
import org.junit.jupiter.api.Test;

class SeatGeometryTest {
    @Test
    void fullBlocksUseCenterAndStandardHeight() {
        SeatGeometry.Offset offset = SeatGeometry.offsetFor(shape(SeatGeometry.Kind.FULL, false, BlockFace.SELF, Stairs.Shape.STRAIGHT));
        assertEquals(0.5, offset.x(), 1e-9);
        assertEquals(1.0, offset.y(), 1e-9);
        assertEquals(0.5, offset.z(), 1e-9);
    }

    @Test
    void slabHalvesUseTheirActualTopSurface() {
        assertEquals(0.5, SeatGeometry.offsetFor(shape(SeatGeometry.Kind.BOTTOM_SLAB, false, BlockFace.SELF, Stairs.Shape.STRAIGHT)).y(), 1e-9);
        assertEquals(1.0, SeatGeometry.offsetFor(shape(SeatGeometry.Kind.FULL, false, BlockFace.SELF, Stairs.Shape.STRAIGHT)).y(), 1e-9);
    }

    @Test
    void straightStairsUseStairSeatOffsetForAllFacings() {
        for (BlockFace facing : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            assertEquals(0.525, SeatGeometry.offsetFor(shape(SeatGeometry.Kind.STAIRS, true, facing, Stairs.Shape.STRAIGHT)).y(), 1e-9);
        }
    }

    @Test
    void upperStairsAndAllCornerShapesRemainSupported() {
        for (Stairs.Shape shape : Stairs.Shape.values()) {
            assertEquals(1.0, SeatGeometry.offsetFor(shape(SeatGeometry.Kind.STAIRS, false, BlockFace.NORTH, shape)).y(), 1e-9);
            assertEquals(0.525, SeatGeometry.offsetFor(shape(SeatGeometry.Kind.STAIRS, true, BlockFace.NORTH, shape)).y(), 1e-9);
        }
    }

    private static SeatGeometry.Shape shape(SeatGeometry.Kind kind, boolean bottomHalf, BlockFace face, Stairs.Shape stairShape) {
        return new SeatGeometry.Shape(kind, bottomHalf, face, stairShape);
    }
}
