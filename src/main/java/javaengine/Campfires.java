package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

final class Campfires {
    private static final float REACH = 4.5f;
    private final Campfire[] fires = new Campfire[16];
    private final Matrix4f pose = new Matrix4f();
    private final Vector3f target = new Vector3f();
    private final Vector3f localEye = new Vector3f();
    private int count;

    boolean target(Vector3f eye, Vector3f aim, Forest forest) {
        if (count == fires.length) return false;
        for (float distance = 0.55f; distance <= REACH; distance += 0.07f) {
            float x = eye.x + aim.x * distance;
            float z = eye.z + aim.z * distance;
            float ground = Terrain.height(x, z);
            if (eye.y + aim.y * distance > ground) continue;
            if (Math.hypot(x - eye.x, z - eye.z) < 0.95f
                    || Math.abs(x) > Terrain.WALK_LIMIT - 0.8f
                    || Math.abs(z) > Terrain.WALK_LIMIT - 0.8f
                    || forest.blocksPlayer(x, z)) return false;
            for (int i = 0; i < count; i++) {
                float dx = x - fires[i].position.x;
                float dz = z - fires[i].position.z;
                if (dx * dx + dz * dz < 1.15f * 1.15f) return false;
            }
            target.set(x, ground, z);
            return true;
        }
        return false;
    }

    boolean place(Vector3f eye, Vector3f aim, Forest forest, Meadow meadow, Inventory inventory) {
        if (!target(eye, aim, forest) || !inventory.takeSelected(Inventory.Item.CAMPFIRE)) return false;
        fires[count++] = new Campfire(new Vector3f(target));
        meadow.blast(target, 0.62f);
        return true;
    }

    void update(float delta) {
        for (int i = 0; i < count; i++) fires[i].fire.update(delta);
    }

    void aimFire(IgnitionAim ignition, Vector3f eye, Vector3f aim) {
        for (int i = 0; i < count; i++) {
            Campfire camp = fires[i];
            if (camp.position.distanceSquared(eye) > 36.0f) continue;
            localEye.set(eye).sub(camp.position);
            ignition.consider(camp.fire, localEye, aim, -0.39f, 0.01f, -0.39f, 0.39f, 0.48f, 0.39f);
        }
    }

    void lights(FireVisuals visuals, Vector3f eye) {
        for (int i = 0; i < count; i++) {
            Campfire camp = fires[i];
            if (camp.fire.burning() && camp.position.distanceSquared(eye) < 70.0f) {
                visuals.addLight(camp.fire, pose.translation(camp.position), 1.55f);
            }
        }
    }

    float proximity(Vector3f eye) {
        float nearest = 144.0f;
        for (int i = 0; i < count; i++) {
            if (fires[i].fire.burning()) nearest = Math.min(nearest, fires[i].position.distanceSquared(eye));
        }
        return 1.0f - (float) Math.sqrt(nearest) / 12.0f;
    }

    void draw(SceneShader shader, ObjModel mesh, FireVisuals visuals, FloatBuffer buffer) {
        for (int i = 0; i < count; i++) {
            Campfire camp = fires[i];
            pose.translation(camp.position);
            shader.draw(mesh, pose, false, buffer);
            visuals.draw(shader, camp.fire, pose, 0.72f, buffer);
        }
    }

    void preview(SceneShader shader, ObjModel mesh, Vector3f eye, Vector3f aim,
                 Forest forest, FloatBuffer buffer) {
        if (!target(eye, aim, forest)) return;
        shader.drawTranslucent(mesh, pose.translation(target), 0.56f, false, buffer);
    }

    void drawHeld(SceneShader shader, ObjModel mesh, float aspect, FloatBuffer buffer) {
        pose.translation(0.19f * aspect, -0.37f, -0.67f).rotateY(-0.28f).scale(0.56f);
        shader.draw(mesh, pose, false, buffer);
    }

    private static final class Campfire {
        final Vector3f position;
        final Burnable fire = new Burnable(Float.POSITIVE_INFINITY);

        Campfire(Vector3f position) { this.position = position; }
    }
}
