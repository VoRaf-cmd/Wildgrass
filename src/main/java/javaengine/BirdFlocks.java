package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

final class BirdFlocks {
    private static final float TURN = (float) (2.0 * Math.PI);
    private static final float HINGE = 0.072f;
    private final Flock[] flocks = {
            new Flock(-9.0f, 5.8f, -9.6f, 5.8f, 3.55f, 0.26f, 0.76f, 5),
            new Flock(9.1f, 8.5f, -19.6f, 6.7f, 4.65f, -0.21f, 2.2f, 4)
    };
    private final Matrix4f base = new Matrix4f();
    private final Matrix4f wingPose = new Matrix4f();
    private float time;

    BirdFlocks() {
        update(0.0f);
    }

    void update(float seconds) {
        time += seconds;
        for (Flock flock : flocks) {
            for (int i = 0; i < flock.birds.length; i++) {
                Bird bird = flock.birds[i];
                float angle = flock.start + time * flock.speed - Math.copySign(i * 0.081f, flock.speed);
                float sin = (float) Math.sin(angle);
                float cos = (float) Math.cos(angle);
                float dx = -sin * flock.radiusX * flock.speed;
                float dz = cos * flock.radiusZ * flock.speed;
                float speed = (float) Math.hypot(dx, dz);
                float spread = (i == 0 ? 0.0f : (i + 1) / 2 * (i % 2 == 0 ? -0.67f : 0.72f));
                bird.position.set(flock.x + cos * flock.radiusX - dz / speed * spread,
                        flock.height + 0.47f * (float) Math.sin(angle * 1.6f + i * 0.55f) - i * 0.10f,
                        flock.z + sin * flock.radiusZ + dx / speed * spread);
                bird.yaw = -(float) Math.atan2(dx, -dz);
                bird.bank = -Math.copySign(0.14f + 0.12f * cos * cos, flock.speed);
                float cycle = (time + i * 0.23f) % 3.2f;
                float flapAmount = 1.0f - Math.clamp((cycle - 1.62f) * 2.3f, 0.0f, 1.0f);
                bird.flap = 0.09f + flapAmount * 0.72f
                        * (float) Math.sin((time * (3.7f + i * 0.11f)) * TURN + i * 2.1f);
            }
        }
    }

    int flockCount() {
        return flocks.length;
    }

    Vector3f position(int flockIndex) {
        return flocks[flockIndex].birds[0].position;
    }

    void draw(SceneShader shader, ObjModel body, ObjModel wing, FloatBuffer buffer) {
        for (Flock flock : flocks) {
            for (Bird bird : flock.birds) {
                base.translation(bird.position).rotateY(bird.yaw).rotateZ(bird.bank).scale(bird.size);
                shader.draw(body, base, false, buffer);
                drawWing(shader, wing, bird.flap, 1.0f, buffer);
                drawWing(shader, wing, bird.flap, -1.0f, buffer);
            }
        }
    }

    private void drawWing(SceneShader shader, ObjModel wing, float angle, float side, FloatBuffer buffer) {
        wingPose.set(base).translate(HINGE * side, 0.012f, -0.016f)
                .rotateZ(angle * side).scale(side, 1.0f, 1.0f);
        shader.draw(wing, wingPose, false, buffer);
    }

    private static final class Flock {
        final float x, height, z, radiusX, radiusZ, speed, start;
        final Bird[] birds;

        Flock(float x, float height, float z, float radiusX, float radiusZ, float speed, float start, int count) {
            this.x = x;
            this.height = height;
            this.z = z;
            this.radiusX = radiusX;
            this.radiusZ = radiusZ;
            this.speed = speed;
            this.start = start;
            birds = new Bird[count];
            for (int i = 0; i < count; i++) {
                birds[i] = new Bird(1.18f - i * 0.07f);
            }
        }
    }

    private static final class Bird {
        final Vector3f position = new Vector3f();
        final float size;
        float yaw;
        float bank;
        float flap;

        Bird(float size) {
            this.size = size;
        }
    }
}
