package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

final class WoodDrops {
    private static final float PICKUP_RADIUS = 0.75f;
    private final Vector3f[] drops = {new Vector3f(), new Vector3f(), new Vector3f()};
    private final boolean[] onGround = new boolean[drops.length];
    private final Matrix4f model = new Matrix4f();
    private final Vector3f localEye = new Vector3f();
    private final Burnable[] fires = {new Burnable(7.0f), new Burnable(7.0f), new Burnable(7.0f)};
    private float age;

    void dropLogs(Vector3f trunk, Vector3f fallDirection) {
        age = 0.0f;
        for (int i = 0; i < drops.length; i++) {
            float forward = 0.56f + i * 0.54f;
            float sideways = i == 1 ? 0.82f : -0.82f;
            float x = trunk.x + forward * fallDirection.x + sideways * fallDirection.z;
            float z = trunk.z + forward * fallDirection.z - sideways * fallDirection.x;
            drops[i].set(x, Terrain.height(x, z), z);
            onGround[i] = true;
        }
    }

    boolean update(float seconds, Vector3f player, Inventory inventory) {
        age += seconds;
        boolean collected = false;
        for (int i = 0; i < drops.length; i++) {
            fires[i].update(seconds);
            float dx = player.x - drops[i].x;
            float dz = player.z - drops[i].z;
            if (onGround[i] && !fires[i].burning() && dx * dx + dz * dz <= PICKUP_RADIUS * PICKUP_RADIUS
                    && inventory.add(Inventory.Item.LOG)) {
                onGround[i] = false;
                collected = true;
            }
        }
        return collected;
    }

    private float bob(int index) {
        return drops[index].y + 0.31f + 0.053f * (float) Math.sin(age * 5.0f + index);
    }

    void aimFire(IgnitionAim ignition, Vector3f eye, Vector3f aim) {
        for (int i = 0; i < drops.length; i++) {
            if (onGround[i]) {
                localEye.set(eye).sub(drops[i].x, bob(i), drops[i].z);
                ignition.consider(fires[i], localEye, aim, -0.21f, -0.13f, -0.21f, 0.21f, 0.13f, 0.21f);
            }
        }
    }

    void fireLights(FireVisuals fire) {
        for (int i = 0; i < drops.length; i++) {
            if (onGround[i]) {
                fire.addLight(fires[i], model.translation(drops[i].x, bob(i), drops[i].z), 0.55f);
            }
        }
    }

    void spreadFire(Meadow meadow) {
        for (int i = 0; i < drops.length; i++) {
            if (onGround[i] && fires[i].burning() && fires[i].age() > 0.95f) {
                meadow.catchFromWood(drops[i].x, drops[i].z);
            }
        }
    }

    void draw(SceneShader shader, ObjModel log, FireVisuals fire, FloatBuffer buffer) {
        for (int i = 0; i < drops.length; i++) {
            if (onGround[i]) {
                model.translation(drops[i].x, bob(i), drops[i].z).rotateY(age * 1.5f + i);
                shader.drawCharred(log, model, fires[i].charAmount(), buffer);
                fire.draw(shader, fires[i], model.translation(drops[i].x, bob(i), drops[i].z), 0.32f, buffer);
            }
        }
    }
}
