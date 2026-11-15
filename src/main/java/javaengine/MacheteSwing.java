package javaengine;

import org.joml.Matrix4f;

import java.nio.FloatBuffer;

import static org.joml.Math.lerp;
import static org.lwjgl.opengl.GL33C.*;

final class MacheteSwing {
    private static final float WINDUP = 0.08f;
    private static final float CONTACT = 0.13f;
    private static final float FOLLOW_THROUGH = 0.20f;
    private static final float END = 0.36f;
    private static final Pose READY = new Pose(.18f, -.39f, -.61f, -.16f, -.26f, .20f);
    private static final Pose RIGHT = new Pose(.18f, -.27f, -.77f, -.10f, -2.52f, 1.37f);
    private static final Pose LEFT = new Pose(.035f, -.36f, -.76f, -.12f, -.24f, 1.43f);
    private float elapsed = END;
    private boolean fromRight;
    private final Matrix4f trailPose = new Matrix4f();

    boolean start() {
        if (elapsed < END) {
            return false;
        }
        fromRight = !fromRight;
        elapsed = 0.0f;
        return true;
    }

    void cancel() {
        elapsed = END;
    }

    boolean update(float delta) {
        float previous = elapsed;
        elapsed = Math.min(END, elapsed + delta);
        return previous < CONTACT && elapsed >= CONTACT;
    }

    void transform(Matrix4f model, float aspect) {
        transformAt(model, aspect, elapsed);
    }

    void drawTrail(SceneShader shader, ObjModel trail, float aspect, FloatBuffer buffer) {
        if (elapsed < CONTACT || elapsed > FOLLOW_THROUGH + 0.06f) {
            return;
        }
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        for (int i = 3; i >= 1; i--) {
            float time = Math.min(FOLLOW_THROUGH, elapsed - i * 0.016f);
            if (time >= WINDUP) {
                transformAt(trailPose, aspect, time);
                float fade = Math.clamp((FOLLOW_THROUGH + 0.06f - elapsed) / 0.08f, 0.0f, 1.0f);
                shader.drawTranslucent(trail, trailPose, (0.34f - i * 0.075f) * fade, true, buffer);
            }
        }
        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    private void transformAt(Matrix4f model, float aspect, float time) {
        Pose raised = fromRight ? RIGHT : LEFT;
        Pose end = fromRight ? LEFT : RIGHT;
        if (time < WINDUP) {
            apply(model, aspect, READY, raised, time / WINDUP);
        } else if (time < FOLLOW_THROUGH) {
            apply(model, aspect, raised, end, (time - WINDUP) / (FOLLOW_THROUGH - WINDUP));
        } else {
            apply(model, aspect, end, READY, (time - FOLLOW_THROUGH) / (END - FOLLOW_THROUGH));
        }
    }

    private static void apply(Matrix4f model, float aspect, Pose start, Pose end, float progress) {
        float ease = progress * progress * (3.0f - 2.0f * progress);
        model.translation(lerp(start.x, end.x, ease) * aspect, lerp(start.y, end.y, ease), lerp(start.z, end.z, ease))
                .rotateY(lerp(start.yaw, end.yaw, ease)).rotateZ(lerp(start.roll, end.roll, ease))
                .rotateX(lerp(start.pitch, end.pitch, ease)).scale(.49f * Math.min(aspect, 1.0f))
                .translate(0.0f, -.21f, 0.0f);
    }

    private record Pose(float x, float y, float z, float pitch, float yaw, float roll) { }
}
