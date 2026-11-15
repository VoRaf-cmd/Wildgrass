package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.util.Random;

import static org.lwjgl.opengl.GL33C.*;

final class BlastEffects {
    private static final float RADIUS = 4.1f;
    private static final float WAVE_TIME = 0.66f;
    private final float[] marks = new float[8 * 4];
    private final Particle[] particles = new Particle[300];
    private final Wave[] waves = {new Wave(), new Wave(), new Wave()};
    private final Matrix4f pose = new Matrix4f();
    private final Random random = new Random();
    private int markCount;
    private int nextMark;
    private int nextWave;
    private int nextParticle;

    BlastEffects() {
        for (int i = 0; i < particles.length; i++) particles[i] = new Particle();
    }

    void explode(Vector3f position, Vector3f player, Forest forest, Meadow meadow, ImpactMotion motion,
                 SoundEffect sound) {
        forest.blast(position, RADIUS);
        meadow.blast(position, RADIUS * 0.87f);
        float distance = position.distance(player);
        motion.blast(1.0f - distance / 22.0f);
        sound.volume(-Math.min(29.0f, distance * 1.40f));
        sound.play();
        int mark = nextMark * 4;
        nextMark = (nextMark + 1) % (marks.length / 4);
        markCount = Math.min(markCount + 1, marks.length / 4);
        marks[mark] = position.x;
        marks[mark + 1] = position.z;
        marks[mark + 2] = 3.45f;
        marks[mark + 3] = random.nextFloat(1f, 55f);
        Wave wave = waves[nextWave];
        nextWave = (nextWave + 1) % waves.length;
        wave.center.set(position);
        wave.age = 0.0f;
        for (int i = 0; i < 104; i++) {
            Particle p = particles[nextParticle];
            nextParticle = (nextParticle + 1) % particles.length;
            p.kind = i < 42 ? 0 : i < 77 ? 1 : 2;
            float angle = random.nextFloat(0.0f, 6.2832f);
            float x = (float) Math.cos(angle);
            float z = (float) Math.sin(angle);
            float offset = random.nextFloat(0.0f, p.kind == 2 ? 0.91f : 0.32f);
            float speed = p.kind == 2 ? random.nextFloat(0.47f, 3.1f) : random.nextFloat(1.8f, 8.0f);
            p.position.set(position).add(x * offset, random.nextFloat(0.04f, 0.26f), z * offset);
            p.velocity.set(x * speed, p.kind == 2 ? random.nextFloat(0.5f, 2.4f) : random.nextFloat(1.5f, 6.6f),
                    z * speed);
            p.duration = p.life = p.kind == 2 ? random.nextFloat(1.05f, 2.2f) : random.nextFloat(0.29f, 0.89f);
            p.size = p.kind == 2 ? random.nextFloat(0.15f, 0.41f) : random.nextFloat(0.023f, 0.095f);
            p.rotation = angle;
        }
    }

    void update(float delta) {
        for (Wave wave : waves) wave.age = Math.min(wave.age + delta, WAVE_TIME);
        for (Particle p : particles) {
            if (p.life <= 0.0f) continue;
            p.life -= delta;
            if (p.kind != 2) p.velocity.y -= (p.kind == 1 ? 11.0f : 5.2f) * delta;
            else p.velocity.add(Wind.X * delta * 0.8f, 0.0f, Wind.Z * delta * 0.8f).mul(1.0f - delta * 1.3f);
            p.position.fma(delta, p.velocity);
            p.rotation += delta * 8.0f;
            if (p.kind != 2 && p.position.y < Terrain.height(p.position.x, p.position.z)) p.life = 0.0f;
        }
    }

    void ground(SceneShader shader) {
        shader.setBlastMarks(marks, markCount);
    }

    void lights(FireVisuals fire) {
        for (Wave wave : waves) {
            if (wave.age < 0.17f) fire.flash(wave.center, 2.5f * (1.0f - wave.age / 0.17f));
        }
    }

    void draw(SceneShader shader, ObjModel ember, ObjModel debris, ObjModel smoke, ObjModel ring, FloatBuffer buffer) {
        for (Particle p : particles) {
            if (p.life <= 0.0f || p.kind == 2) continue;
            float size = p.size * Math.min(1.0f, p.life * 6.8f);
            pose.translation(p.position).rotateY(p.rotation).rotateX(p.rotation * 0.6f).scale(size);
            if (p.kind == 0) shader.drawEmissive(ember, pose.scale(0.78f, 1.9f, 0.78f), buffer);
            else shader.draw(debris, pose, false, buffer);
        }
        for (Wave wave : waves) {
            if (wave.age < 0.15f) {
                float size = 0.18f + wave.age * 3.6f;
                pose.translation(wave.center.x, wave.center.y + size * 0.72f, wave.center.z)
                        .rotateY(wave.age * 3.0f).scale(size, size * 1.42f, size);
                shader.drawEmissive(ember, pose, buffer);
            }
        }
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        for (Wave wave : waves) {
            if (wave.age >= WAVE_TIME) continue;
            float t = wave.age / WAVE_TIME;
            float radius = 0.25f + 4.7f * (1.0f - (1.0f - t) * (1.0f - t));
            pose.translation(wave.center.x, Terrain.height(wave.center.x, wave.center.z) + 0.14f, wave.center.z)
                    .scale(radius, 1.0f, radius);
            shader.drawTranslucent(ring, pose, 0.71f * (1.0f - t), true, buffer);
        }
        for (Particle p : particles) {
            if (p.life <= 0.0f || p.kind != 2) continue;
            float fade = p.life / p.duration;
            float size = p.size * (2.0f - fade);
            pose.translation(p.position).rotateY(p.rotation * 0.32f).scale(size, size * 0.68f, size);
            shader.drawTranslucent(smoke, pose, 0.35f * fade, false, buffer);
        }
        glDepthMask(true);
        glDisable(GL_BLEND);
    }

    private static final class Particle {
        final Vector3f position = new Vector3f();
        final Vector3f velocity = new Vector3f();
        int kind;
        float life, duration, size, rotation;
    }

    private static final class Wave {
        final Vector3f center = new Vector3f();
        float age = WAVE_TIME;
    }
}
