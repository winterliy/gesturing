package dev.ledge.gesturing.gesture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GestureDirectionTest {
    @Test
    void parsesAllFourDirectionsWithoutCaseSensitivity() {
        assertEquals(GestureDirection.EAST, GestureDirection.parse("EaSt").orElseThrow());
        assertEquals(GestureDirection.WEST, GestureDirection.parse("west").orElseThrow());
        assertEquals(GestureDirection.NORTH, GestureDirection.parse("NORTH").orElseThrow());
        assertEquals(GestureDirection.SOUTH, GestureDirection.parse("south").orElseThrow());
    }

    @Test
    void rejectsUnknownDirections() {
        assertTrue(GestureDirection.parse("up").isEmpty());
        assertTrue(GestureDirection.parse("").isEmpty());
        assertTrue(GestureDirection.parse(" east ").isEmpty());
        assertTrue(GestureDirection.parse(null).isEmpty());
    }
}
