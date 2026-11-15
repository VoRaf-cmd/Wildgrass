package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

final class RemotePlayer {
    private final Vector3f displayed = new Vector3f();
    private final Vector3f target = new Vector3f();
    private final Matrix4f pose = new Matrix4f();
    private boolean visible;
    private float yaw;
    private float stride;
    private float movement;

    void receive(Vector3f position) {
        target.set(position);
        if (!visible) displayed.set(position);
        visible = true;
    }

    void hide() {
        visible = false;
        movement = 0.0f;
    }

    void update(float delta) {
        if (!visible) return;
        float dx = target.x - displayed.x;
        float dz = target.z - displayed.z;
        float distance = (float) Math.hypot(dx, dz);
        if (distance > 3.0f) {
            displayed.set(target);
            movement = 0.0f;
            return;
        }
        if (distance > 0.015f) yaw = (float) Math.atan2(dx, -dz);
        float blend = 1.0f - (float) Math.exp(-delta * 12.0f);
        displayed.lerp(target, blend);
        float speed = Math.min(1.0f, distance * 5.0f);
        movement += (speed - movement) * blend;
        stride += distance * blend * 7.0f;
    }

    void draw(SceneShader shader, ObjModel mesh, FloatBuffer buffer) {
        if (!visible) return;
        float bob = (float) Math.sin(stride * 2.0f) * 0.026f * movement;
        pose.translation(displayed.x, displayed.y - 1.53f + bob, displayed.z).rotateY(yaw);
        shader.draw(mesh, pose, false, buffer);
    }
}
