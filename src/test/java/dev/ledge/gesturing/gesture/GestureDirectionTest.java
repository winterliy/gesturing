package dev.ledge.gesturing.gesture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GestureDirectionTest {
    @Test
    void parsesAllFourDirectionsWithoutCaseSensitivity() {
        assertEquals(GestureDirection.EAST, GestureDirection.parse("EaSt").get());
        assertEquals(GestureDirection.WEST, GestureDirection.parse("west").get());
        assertEquals(GestureDirection.NORTH, GestureDirection.parse("NORTH").get());
        assertEquals(GestureDirection.SOUTH, GestureDirection.parse("south").get());
    }

    @Test
    void rejectsUnknownDirections() {
        assertFalse(GestureDirection.parse("up").isPresent());
        assertFalse(GestureDirection.parse("").isPresent());
        assertFalse(GestureDirection.parse(" east ").isPresent());
        assertFalse(GestureDirection.parse(null).isPresent());
    }
}
