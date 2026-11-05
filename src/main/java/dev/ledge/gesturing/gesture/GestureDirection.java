package dev.ledge.gesturing.gesture;

import java.util.Locale;
import java.util.Optional;

public enum GestureDirection {
    SOUTH(0.0f), WEST(90.0f), NORTH(180.0f), EAST(-90.0f);

    private final float yaw;

    GestureDirection(float yaw) { this.yaw = yaw; }

    public float yaw() { return yaw; }

    public static Optional<GestureDirection> parse(String value) {
        try { return Optional.of(valueOf(value.toUpperCase(Locale.ROOT))); }
        catch (IllegalArgumentException | NullPointerException ignored) { return Optional.empty(); }
    }
}
