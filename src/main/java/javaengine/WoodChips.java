package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.util.Random;

final class WoodChips {
    private static final int CHIPS_PER_HIT = 23;

    private final Chip[] chips = new Chip[92];
    private final Random random = new Random();
    private final Matrix4f model = new Matrix4f();
    private int next;

    WoodChips() {
        for (int i = 0; i < chips.length; i++) {
            chips[i] = new Chip();
        }
    }

    void burst(Vector3f impact, Vector3f trunk) {
        float radius = (float) Math.hypot(impact.x - trunk.x, impact.z - trunk.z);
        float nx = (impact.x - trunk.x) / radius;
        float nz = (impact.z - trunk.z) / radius;
        for (int i = 0; i < CHIPS_PER_HIT; i++) {
            Chip chip = spawn(i >= 16);
            float outward = random.nextFloat(0.7f, 3.5f);
            float sideways = random.nextFloat(-2.1f, 2.1f);
            chip.position.set(impact).add(nx * 0.045f, random.nextFloat(-0.045f, 0.045f), nz * 0.045f);
            chip.velocity.set(nx * outward - nz * sideways, random.nextFloat(0.45f, 2.85f),
                    nz * outward + nx * sideways);
        }
    }

    void landingDust(Vector3f trunk, Vector3f direction) {
        for (int i = 0; i < 36; i++) {
            Chip chip = spawn(true);
            float forward = random.nextFloat(0.40f, 3.65f);
            float side = random.nextFloat(-0.46f, 0.46f);
            float x = trunk.x + forward * direction.x + side * direction.z;
            float z = trunk.z + forward * direction.z - side * direction.x;
            chip.position.set(x, Terrain.height(x, z) + random.nextFloat(0.12f, 0.27f), z);
            float drift = Math.copySign(random.nextFloat(0.46f, 2.0f), side);
            chip.velocity.set(drift * direction.z, random.nextFloat(0.25f, 0.93f), -drift * direction.x);
            chip.size *= 2.1f;
        }
    }

    void grassCut(Vector3f contact, Vector3f aim) {
        for (int i = 0; i < 12; i++) {
            Chip blade = spawn(false);
            blade.grass = true;
            blade.position.set(contact).add(random.nextFloat(-0.56f, 0.56f), random.nextFloat(-0.19f, 0.34f),
                    random.nextFloat(-0.56f, 0.56f));
            float lateral = random.nextFloat(-2.7f, 2.7f);
            blade.velocity.set(aim.x * 1.3f - aim.z * lateral, random.nextFloat(0.5f, 2.1f),
                    aim.z * 1.3f + aim.x * lateral);
            blade.size = random.nextFloat(0.035f, 0.10f);
        }
    }

    void hoofDust(Vector3f point, boolean gallop) {
        for (int i = 0; i < (gallop ? 5 : 2); i++) {
            Chip chip = spawn(true);
            chip.position.set(point).add(random.nextFloat(-.20f, .20f), .07f, random.nextFloat(-.17f, .17f));
            chip.velocity.set(random.nextFloat(-.7f, .7f), random.nextFloat(.25f, .78f),
                    random.nextFloat(-.7f, .7f));
            chip.size *= gallop ? 2.1f : 1.25f;
        }
    }

    void graze(Vector3f mouth, Vector3f forward) {
        for (int i = 0; i < 13; i++) {
            Chip blade = spawn(false);
            blade.grass = true;
            blade.position.set(mouth).add(random.nextFloat(-.15f, .15f), .06f, random.nextFloat(-.15f, .15f));
            blade.velocity.set(forward.x * .6f + random.nextFloat(-.8f, .8f),
                    random.nextFloat(.35f, 1.6f), forward.z * .6f + random.nextFloat(-.8f, .8f));
            blade.size *= .75f;
        }
    }

    private Chip spawn(boolean dust) {
        Chip chip = chips[next];
        next = (next + 1) % chips.length;
        chip.dust = dust;
        chip.grass = false;
        chip.meat = false;
        chip.life = dust ? random.nextFloat(0.26f, 0.65f) : random.nextFloat(0.32f, 0.74f);
        chip.size = dust ? random.nextFloat(0.017f, 0.036f) : random.nextFloat(0.022f, 0.067f);
        chip.angle = random.nextFloat(0.0f, 6.28f);
        chip.spin = random.nextFloat(-14.0f, 14.0f);
        return chip;
    }

    void animalHit(Vector3f point, boolean dead) {
        for (int i = 0; i < (dead ? 21 : 9); i++) {
            Chip chip = spawn(false);
            chip.meat = true;
            chip.position.set(point);
            chip.velocity.set(random.nextFloat(-1.6f, 1.6f), random.nextFloat(.35f, 2.0f),
                    random.nextFloat(-1.6f, 1.6f));
        }
    }

    void update(float delta) {
        for (Chip chip : chips) {
            if (chip.life > 0) {
                chip.life -= delta;
                chip.velocity.y -= (chip.dust ? 1.4f : chip.grass ? 4.4f : 8.8f) * delta;
                if (chip.grass) {
                    chip.velocity.x += Wind.X * 1.6f * delta;
                    chip.velocity.z += Wind.Z * 1.6f * delta;
                }
                chip.position.fma(delta, chip.velocity);
                chip.angle += chip.spin * delta;
                if (chip.position.y < Terrain.height(chip.position.x, chip.position.z)) {
                    chip.life = 0;
                }
            }
        }
    }

    void draw(SceneShader shader, ObjModel wood, ObjModel dust, ObjModel grass, ObjModel meat, FloatBuffer buffer) {
        for (Chip chip : chips) {
            if (chip.life > 0) {
                float size = chip.size * Math.min(1.0f, chip.life * 8.0f);
                model.translation(chip.position).rotateY(chip.angle).rotateX(chip.angle * 0.65f).scale(size);
                shader.draw(chip.dust ? dust : chip.grass ? grass : chip.meat ? meat : wood, model, false, buffer);
            }
        }
    }

    private static final class Chip {
        final Vector3f position = new Vector3f();
        final Vector3f velocity = new Vector3f();
        float life;
        float size;
        float angle;
        float spin;
        boolean dust;
        boolean grass;
        boolean meat;
    }
}
