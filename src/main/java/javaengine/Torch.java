package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

final class Torch {
    private final Matrix4f pose = new Matrix4f();
    private final Vector3f tip = new Vector3f();
    private boolean lit;
    private float time;

    void toggle() { lit = !lit; }
    boolean lit() { return lit; }
    void update(float delta) { time += delta; }
    void stow() { lit = false; }

    void draw(SceneShader shader, ObjModel mesh, FireVisuals fire, float aspect, FloatBuffer buffer) {
        pose.translation(0.15f * aspect, -0.39f, -0.62f).rotateZ(0.21f).rotateY(-0.23f);
        shader.draw(mesh, pose, false, buffer);
        if (lit) {
            pose.transformPosition(0.0f, 0.44f, 0.0f, tip);
            fire.flames(shader, tip, time, 0.145f, buffer);
        }
    }
}
