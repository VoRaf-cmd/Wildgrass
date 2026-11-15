package javaengine;

import org.joml.Matrix4f;

import static org.joml.Math.lerp;

final class AxeSwing {
    private static final float WINDUP_END = 0.18f;
    private static final float STRIKE_END = 0.28f;
    private static final float SWING_END = 0.56f;
    private static final float FORWARD_YAW = -(float) (Math.PI / 2.0);

    private static final Pose READY = new Pose(0.17f, -0.39f, -0.52f, -0.08f, 0.10f);
    private static final Pose WINDUP = new Pose(0.12f, -0.36f, -0.65f, 0.68f, -0.05f);
    private static final Pose STRIKE = new Pose(0.08f, -0.33f, -0.70f, -1.02f, 0.13f);

    private float elapsed = SWING_END;
    private float previousElapsed = SWING_END;

    void start() {
        if (elapsed >= SWING_END) {
            elapsed = 0.0f;
        }
    }

    void cancel() {
        elapsed = SWING_END;
        previousElapsed = SWING_END;
    }

    boolean update(float seconds) {
        previousElapsed = elapsed;
        elapsed = Math.min(elapsed + seconds, SWING_END);
        return previousElapsed < WINDUP_END && elapsed >= WINDUP_END;
    }

    boolean reachedImpact() {
        return previousElapsed < STRIKE_END && elapsed >= STRIKE_END;
    }

    void transform(Matrix4f matrix, float aspectRatio) {
        if (elapsed < WINDUP_END) {
            apply(matrix, aspectRatio, READY, WINDUP, smooth(elapsed / WINDUP_END));
        } else if (elapsed < STRIKE_END) {
            apply(matrix, aspectRatio, WINDUP, STRIKE,
                    smooth((elapsed - WINDUP_END) / (STRIKE_END - WINDUP_END)));
        } else {
            apply(matrix, aspectRatio, STRIKE, READY,
                    smooth((elapsed - STRIKE_END) / (SWING_END - STRIKE_END)));
        }
    }

    private static float smooth(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    private static void apply(Matrix4f matrix, float aspect, Pose from, Pose to, float t) {
        float size = 0.32f * Math.min(1.0f, aspect);
        matrix.translation(lerp(from.x(), to.x(), t) * aspect,
                        lerp(from.y(), to.y(), t), lerp(from.z(), to.z(), t))
                .rotateZ(lerp(from.roll(), to.roll(), t))
                .rotateX(lerp(from.pitch(), to.pitch(), t))
                .rotateY(FORWARD_YAW)
                .scale(size)
                .translate(-0.055f, -0.43f, 0.0f);
    }

    private record Pose(float x, float y, float z, float pitch, float roll) { }
}
