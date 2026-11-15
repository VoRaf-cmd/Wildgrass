package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

final class FireVisuals {
    private final ObjModel flame;
    private final ObjModel smoke;
    private final Matrix4f pose = new Matrix4f();
    private final Vector3f origin = new Vector3f();
    private final float[] lights = new float[5 * 4];
    private int lightCount;

    FireVisuals(ObjModel flame, ObjModel smoke) {
        this.flame = flame;
        this.smoke = smoke;
    }

    void beginLights() {
        lightCount = 0;
    }

    void addLight(Burnable wood, Matrix4f model, float strength) {
        if (!wood.burning() || lightCount == lights.length / 4) {
            return;
        }
        model.transformPosition(wood.ignitionPoint(), origin);
        int i = lightCount++ * 4;
        lights[i] = origin.x;
        lights[i + 1] = origin.y + 0.25f;
        lights[i + 2] = origin.z;
        lights[i + 3] = strength * wood.flameStrength()
                * (0.87f + 0.13f * (float) Math.sin(wood.age() * 25.0f));
    }

    void applyLights(SceneShader shader) {
        shader.setFireLights(lights, lightCount);
    }

    void heldTorchLight(Vector3f eye, Vector3f aim, float time) {
        if (lightCount == lights.length / 4) return;
        int i = lightCount++ * 4;
        lights[i] = eye.x + aim.x * 0.52f;
        lights[i + 1] = eye.y - 0.06f;
        lights[i + 2] = eye.z + aim.z * 0.52f;
        lights[i + 3] = 0.77f + 0.12f * (float) Math.sin(time * 23.0f);
    }

    void flash(Vector3f point, float strength) {
        if (lightCount == lights.length / 4) return;
        int i = lightCount++ * 4;
        lights[i] = point.x;
        lights[i + 1] = point.y + 0.65f;
        lights[i + 2] = point.z;
        lights[i + 3] = strength;
    }

    void draw(SceneShader shader, Burnable wood, Matrix4f model, float size, FloatBuffer buffer) {
        if (!wood.burning()) {
            return;
        }
        model.transformPosition(wood.ignitionPoint(), origin);
        float strength = wood.flameStrength();
        float time = wood.age();
        flames(shader, origin, time, size * strength, buffer);

        smoke(shader, time, size, strength, 5, buffer);
    }

    void brush(SceneShader shader, Burnable grass, Matrix4f model, float size, boolean nearby, FloatBuffer buffer) {
        model.transformPosition(grass.ignitionPoint(), origin);
        float strength = grass.flameStrength();
        flames(shader, origin, grass.age(), size * strength, nearby ? 3 : 1, buffer);
        if (nearby) {
            smoke(shader, grass.age(), size, strength, 2, buffer);
        }
    }

    private void smoke(SceneShader shader, float time, float size, float strength, int puffs, FloatBuffer buffer) {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        for (int i = 0; i < puffs; i++) {
            float age = time % 1.5f - i * (1.5f / puffs);
            if (age < 0.0f && time >= 1.5f) {
                age += 1.5f;
            }
            if (age >= 0.07f) {
                float puff = size * (0.22f + age * 0.16f) * strength * (1.0f - age / 1.5f);
                float drift = size * age * (0.45f + 0.12f * i);
                pose.translation(origin.x + Wind.X * drift,
                                origin.y + size * (0.76f + age * 1.6f),
                                origin.z + Wind.Z * drift + size * 0.20f * (float) Math.sin(age * 3.5f + i))
                        .rotateY(age + i).scale(puff);
                shader.drawTranslucent(smoke, pose, 0.48f * strength * (1.0f - age / 1.5f), false, buffer);
            }
        }
        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    void flames(SceneShader shader, Vector3f center, float time, float size, FloatBuffer buffer) {
        flames(shader, center, time, size, 3, buffer);
    }

    private void flames(SceneShader shader, Vector3f center, float time, float size, int tongues, FloatBuffer buffer) {
        for (int i = 0; i < tongues; i++) {
            float beat = 0.83f + 0.19f * (float) Math.sin(time * (17.0f + i * 2.8f) + i * 2.1f);
            float offset = i - 1.0f;
            pose.translation(center.x + offset * size * 0.18f, center.y, center.z - offset * size * 0.14f)
                    .rotateY(i * 2.1f + time * 0.8f)
                    .rotateZ(0.12f * (float) Math.sin(time * 10.0f + i))
                    .scale(size * 0.72f, size * beat * (i == 1 ? 1.18f : 0.82f), size * 0.72f);
            shader.drawEmissive(flame, pose, buffer);
        }
    }
}
