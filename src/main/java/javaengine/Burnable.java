package javaengine;

import org.joml.Vector3f;

final class Burnable {
    private final float duration;
    private final Vector3f ignitionPoint = new Vector3f();
    private float age = -1.0f;

    Burnable(float duration) {
        this.duration = duration;
    }

    boolean canIgnite() {
        return age < 0.0f;
    }

    void ignite(Vector3f localPoint) {
        ignitionPoint.set(localPoint);
        age = 0.0f;
    }

    void update(float seconds) {
        if (burning()) {
            age = Math.min(age + seconds, duration);
        }
    }

    boolean burning() {
        return age >= 0.0f && age < duration;
    }

    float age() {
        return age;
    }

    float flameStrength() {
        return Math.min(1.0f, 0.22f + age * 1.5f) * Math.min(1.0f, (duration - age) * 0.7f);
    }

    float charAmount() {
        return Math.clamp(age / (duration * 0.67f), 0.0f, 1.0f);
    }

    Vector3f ignitionPoint() {
        return ignitionPoint;
    }
}
