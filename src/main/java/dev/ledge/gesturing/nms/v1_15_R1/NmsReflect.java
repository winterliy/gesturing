package dev.ledge.gesturing.nms.v1_15_R1;

import java.lang.reflect.Method;
import net.minecraft.server.v1_15_R1.Entity;
import net.minecraft.server.v1_15_R1.EntityPose;

/** Access to members that are protected in this release. */
final class NmsReflect {
    private static Method setPose;

    private NmsReflect() { }

    static synchronized void setPose(Entity entity, EntityPose pose) {
        try {
            if (setPose == null) {
                setPose = Entity.class.getDeclaredMethod("setPose", EntityPose.class);
                setPose.setAccessible(true);
            }
            setPose.invoke(entity, pose);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
