package javaengine;

/** Per-player animation state, independent of rendering and network packet frequency. */
final class AvatarMotion {
    float phase, speed, forward, sideways, run, aim, draw, turn;

    void update(float dt, float dx, float dz, float yaw, float yawDelta, boolean aiming, boolean drawing) {
        if (dt <= 0) return;
        float blend = 1 - (float) Math.exp(-12 * dt);
        float measured = Math.min(6, (float) Math.hypot(dx, dz) / dt);
        speed += (measured - speed) * blend;
        float sin = (float) Math.sin(yaw), cos = (float) Math.cos(yaw);
        forward += ((dx * sin - dz * cos) / dt - forward) * blend;
        sideways += ((dx * cos + dz * sin) / dt - sideways) * blend;
        run += (smooth(2.8f, 4.5f, speed) - run) * blend;
        aim += ((aiming ? 1 : 0) - aim) * blend;
        draw += ((drawing ? 1 : 0) - draw) * (1 - (float) Math.exp(-dt * (drawing ? 4 : 22)));
        turn += (Math.clamp(yawDelta / dt, -3, 3) - turn) * blend;
        // Distance drives cadence, so stopping never leaves the feet walking in place.
        phase = (phase + (float) Math.hypot(dx, dz) * (5.4f - run * 1.2f)) % ((float) Math.PI * 2);
    }

    void reset() { phase = speed = forward = sideways = run = aim = draw = turn = 0; }

    static float attack(float progress) {
        if (progress <= 0 || progress >= 1) return 0;
        if (progress < .27f) return -.28f * smooth(0, .27f, progress);
        if (progress < .43f) return -.28f + 1.28f * smooth(.27f, .43f, progress);
        return 1 - smooth(.43f, 1, progress);
    }

    static float smooth(float a, float b, float value) {
        float t = Math.clamp((value - a) / (b - a), 0, 1);
        return t * t * (3 - 2 * t);
    }
}
