package javaengine;

import org.joml.Intersectionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

final class IgnitionAim {
    private static final float REACH = 1.45f;
    private static final float HEAT_TIME = 0.45f;
    private final Vector2f interval = new Vector2f();
    private final Vector3f contact = new Vector3f();
    private Burnable candidate;
    private Burnable heating;
    private float nearest;
    private float elapsed;

    void begin() {
        candidate = null;
        nearest = REACH;
    }

    void consider(Burnable object, Vector3f eye, Vector3f aim,
                  float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (Intersectionf.intersectRayAab(eye.x, eye.y, eye.z, aim.x, aim.y, aim.z,
                minX, minY, minZ, maxX, maxY, maxZ, interval)) {
            float distance = Math.max(interval.x, 0.0f);
            if (distance < nearest) {
                nearest = distance;
                candidate = object;
                contact.set(eye).fma(distance, aim);
            }
        }
    }

    boolean heat(float seconds) {
        if (candidate == null || !candidate.canIgnite()) {
            reset();
            return false;
        }
        if (candidate != heating) {
            elapsed = 0.0f;
            heating = candidate;
        }
        elapsed += seconds;
        if (elapsed < HEAT_TIME) {
            return false;
        }
        candidate.ignite(contact);
        reset();
        return true;
    }

    void reset() {
        heating = null;
        elapsed = 0.0f;
    }
}
