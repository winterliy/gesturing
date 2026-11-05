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
        SeatGeometry.Direction[] directions = {SeatGeometry.Direction.NORTH, SeatGeometry.Direction.SOUTH,
                SeatGeometry.Direction.EAST, SeatGeometry.Direction.WEST};
        for (SeatGeometry.Direction direction : directions) {
            for (SeatGeometry.Corner corner : SeatGeometry.Corner.values()) {
                boolean right = corner == SeatGeometry.Corner.RIGHT;
                double expectedX = switch (direction) {
                    case NORTH -> right ? 0.377 : 0.623;
                    case SOUTH -> right ? 0.623 : 0.377;
                    case EAST -> 0.377;
                    case WEST -> 0.623;
                };
                double expectedZ = switch (direction) {
                    case NORTH -> 0.623;
                    case SOUTH -> 0.377;
                    case EAST, WEST -> right ? 0.623 : 0.377;
                };
                float expectedYaw = switch (direction) {
                    case NORTH -> right ? 45f : -45f;
                    case SOUTH -> right ? -135f : 135f;
                    case EAST -> right ? 135f : 45f;
                    case WEST -> right ? -45f : -135f;
                };
                SeatGeometry.Offset offset = SeatGeometry.offsetFor(SeatGeometry.Shape.cornerStair(direction, corner));
                assertEquals(expectedX, offset.x(), 1e-9);
                assertEquals(0.525, offset.y(), 1e-9);
                assertEquals(expectedZ, offset.z(), 1e-9);
                assertEquals(expectedYaw, offset.yaw());
            }
        }
    }

    @Test
    void topStairsAndDirectionalSeatYawUseExpectedDefaults() {
        var upperStair = new SeatGeometry.Shape(SeatGeometry.Kind.STAIRS, false, SeatGeometry.Direction.NORTH, null);
        assertEquals(1.0, SeatGeometry.offsetFor(upperStair).y(), 1e-9);
        var cornerStair = SeatGeometry.Shape.cornerStair(SeatGeometry.Direction.NORTH, SeatGeometry.Corner.LEFT);
        assertEquals(0.525, SeatGeometry.offsetFor(cornerStair).y(), 1e-9);

        SeatGeometry.Direction[] directions = {SeatGeometry.Direction.NORTH, SeatGeometry.Direction.SOUTH,
                SeatGeometry.Direction.EAST, SeatGeometry.Direction.WEST};
        float[] yaws = {180f, 0f, -90f, 90f};
        for (int index = 0; index < directions.length; index++) {
            assertEquals(yaws[index], SeatGeometry.offsetFor(SeatGeometry.Shape.directional(directions[index])).yaw());
        }
    }
}
