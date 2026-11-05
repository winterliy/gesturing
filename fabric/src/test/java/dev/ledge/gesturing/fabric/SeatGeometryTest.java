package dev.ledge.gesturing.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SeatGeometryTest {
    @Test
    void fullBlockAndSlabSurfacesHaveExpectedOffsets() {
        assertEquals(1.0, SeatGeometry.offsetFor(SeatGeometry.Shape.full()).y(), 1e-9);
        assertEquals(0.5, SeatGeometry.offsetFor(SeatGeometry.Shape.bottomSlab()).y(), 1e-9);
        assertEquals(1.0, SeatGeometry.offsetFor(SeatGeometry.Shape.topSlab()).y(), 1e-9);
    }

    @Test
    void straightStairsPositionSeatsAtTheLowerTread() {
        for (SeatGeometry.Direction direction : SeatGeometry.Direction.values()) {
            SeatGeometry.Offset offset = SeatGeometry.offsetFor(SeatGeometry.Shape.straightStair(direction));
            assertEquals(0.525, offset.y(), 1e-9);
        }
    }

    @Test
    void everyStairCornerRemainsSupported() {
        for (SeatGeometry.Direction direction : SeatGeometry.Direction.values()) {
            for (SeatGeometry.Corner corner : SeatGeometry.Corner.values()) {
                assertEquals(0.525, SeatGeometry.offsetFor(SeatGeometry.Shape.cornerStair(direction, corner)).y(), 1e-9);
            }
        }
    }
}
