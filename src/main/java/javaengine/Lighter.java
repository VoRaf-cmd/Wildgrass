package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

final class Lighter {
    private final Matrix4f bodyPose = new Matrix4f();
    private final Matrix4f lidPose = new Matrix4f();
    private final Vector3f wick = new Vector3f();
    private boolean open;
    private float position;
    private float flameTime;

    void toggle() {
        open = !open;
    }

    void stow() {
        open = false;
        position = 0.0f;
        flameTime = 0.0f;
    }

    void update(float seconds) {
        position = Math.clamp(position + (open ? 7.5f : -10.0f) * seconds, 0.0f, 1.0f);
        flameTime = lit() ? flameTime + seconds : 0.0f;
    }

    boolean lit() {
        return open && position >= 0.8f;
    }

    void draw(SceneShader shader, ObjModel body, ObjModel lid, FireVisuals fire, float aspect, FloatBuffer buffer) {
        float size = 0.56f * Math.min(aspect, 1.0f);
        bodyPose.translation(0.13f * aspect, -0.34f, -0.61f)
                .rotateZ(0.065f).rotateY(-0.29f).rotateX(0.10f).scale(size);
        float angle = 2.75f * position * position * (3.0f - 2.0f * position);
        lidPose.set(bodyPose).translate(-0.145f, 0.397f, 0.0f)
                .rotateZ(angle).translate(0.145f, -0.397f, 0.0f);
        shader.draw(body, bodyPose, false, buffer);
        shader.draw(lid, lidPose, false, buffer);
        if (lit()) {
            bodyPose.transformPosition(0.049f, 0.48f, 0.01f, wick);
            fire.flames(shader, wick, flameTime, size * 0.125f, buffer);
        }
    }
}
