package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.nio.ByteBuffer;
import org.joml.Intersectionf;
import org.joml.Vector2f;

final class Campfires {
    static final float COOK_SECONDS = 12.0f;
    static final int SNAPSHOT_FIRE_BYTES = 14;
    private static final float REACH = 4.5f;
    private final Campfire[] fires = new Campfire[16];
    private final Matrix4f pose = new Matrix4f();
    private final Vector3f target = new Vector3f();
    private final Vector3f localEye = new Vector3f();
    private int count;
    private final Vector2f ray = new Vector2f();

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
        return placeAt(target, meadow);
    }

    Vector3f lastPlacement() { return fires[count - 1].position; }

    boolean placeAt(Vector3f location, Meadow meadow) {
        if (count == fires.length) return false;
        for (int i = 0; i < count; i++) {
            if (fires[i].position.distanceSquared(location) < 0.01f) return false;
        }
        fires[count++] = new Campfire(new Vector3f(location));
        meadow.blast(location, 0.62f);
        return true;
    }

    void update(float delta, boolean authority) {
        for (int i = 0; i < count; i++) {
            Campfire camp = fires[i];
            camp.fire.update(delta);
            if (authority && camp.fire.burning() && camp.cook >= 0) {
                camp.cook = Math.min(COOK_SECONDS, camp.cook + delta);
            }
        }
    }

    Campfire aimedAt(Vector3f eye, Vector3f aim) {
        float nearest = 2.65f;
        Campfire result = null;
        for (int i = 0; i < count; i++) {
            Campfire camp = fires[i];
            Vector3f p = camp.position;
            if (Intersectionf.intersectRayAab(eye.x, eye.y, eye.z, aim.x, aim.y, aim.z,
                    p.x - .52f, p.y, p.z - .52f, p.x + .52f, p.y + .63f, p.z + .52f, ray)
                    && ray.y >= 0 && ray.x < nearest) {
                nearest = Math.max(0, ray.x);
                result = camp;
            }
        }
        return result;
    }

    void writeRanch(ByteBuffer state) {
        state.put((byte) count);
        for (int i = 0; i < count; i++) {
            Campfire camp = fires[i];
            state.putFloat(camp.position.x).putFloat(camp.position.y).putFloat(camp.position.z);
            state.put((byte) (camp.fire.burning() ? 1 : 0));
            state.put((byte) (camp.cook < 0 ? 0 : 1 + (int) (camp.cook * 254 / COOK_SECONDS)));
        }
    }

    void readRanch(ByteBuffer state, Meadow meadow) {
        int size = Byte.toUnsignedInt(state.get());
        for (int i = 0; i < count; i++) fires[i].cook = -1;
        for (int i = 0; i < size; i++) {
            var position = new Vector3f(state.getFloat(), state.getFloat(), state.getFloat());
            boolean lit = state.get() != 0;
            int cook = Byte.toUnsignedInt(state.get());
            if (!(Math.abs(position.x) <= Terrain.WALK_LIMIT && Math.abs(position.z) <= Terrain.WALK_LIMIT
                    && Math.abs(position.y) < 5)) continue;
            Campfire camp = null;
            for (int j = 0; j < count; j++) {
                if (fires[j].position.distanceSquared(position) < .01f) { camp = fires[j]; break; }
            }
            if (camp == null && placeAt(position, meadow)) camp = fires[count - 1];
            if (camp == null) continue;
            if (lit && camp.fire.canIgnite()) camp.fire.ignite(localEye.set(0, .14f, 0));
            camp.cook = cook == 0 ? -1 : (cook - 1) * COOK_SECONDS / 254;
        }
    }

    void drawFood(SceneShader shader, ObjModel raw, ObjModel cooked, FloatBuffer buffer) {
        for (int i = 0; i < count; i++) {
            Campfire camp = fires[i];
            if (camp.cook < 0) continue;
            pose.translation(camp.position).translate(.14f, .29f, -.15f).rotateY(.42f).scale(.88f);
            shader.draw(camp.ready() ? cooked : raw, pose, false, buffer);
        }
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

    static final class Campfire {
        final Vector3f position;
        final Burnable fire = new Burnable(Float.POSITIVE_INFINITY);
        float cook = -1;

        Campfire(Vector3f position) { this.position = position; }

        boolean ready() { return cook >= COOK_SECONDS; }

        boolean startCooking() {
            if (!fire.burning() || cook >= 0) return false;
            cook = 0;
            return true;
        }
    }
}
