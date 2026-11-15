package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

final class Projectiles {
    private static final float GRAVITY = 10.5f;
    private final Shot[] shots = new Shot[48];
    private final Matrix4f pose = new Matrix4f();
    private final Vector3f nextPoint = new Vector3f();
    private final Vector3f segment = new Vector3f();
    private final Vector3f previewPoint = new Vector3f();
    private final Vector3f previewVelocity = new Vector3f();
    private int nextSlot;

    Projectiles() {
        for (int i = 0; i < shots.length; i++) shots[i] = new Shot();
    }

    void launch(Inventory.Item item, Vector3f eye, Vector3f aim, float speed) {
        launch(item, eye, aim, speed, false);
    }

    void launch(Inventory.Item item, Vector3f eye, Vector3f aim, float speed, boolean remote) {
        Shot shot = shots[nextSlot];
        nextSlot = (nextSlot + 1) % shots.length;
        shot.item = item;
        origin(eye, aim, shot.point);
        shot.velocity.set(aim).mul(speed);
        shot.direction.set(aim);
        shot.age = 0.0f;
        shot.stuck = false;
        shot.remote = remote;
    }

    boolean update(float delta, Forest forest, Vector3f player, Inventory inventory, SoundEffect impact) {
        boolean picked = false;
        for (Shot shot : shots) {
            if (shot.item == null) continue;
            shot.age += delta;
            if (shot.stuck) {
                if (shot.remote) {
                    if (shot.age > 12.0f) shot.item = null;
                    continue;
                }
                float dx = player.x - shot.point.x;
                float dz = player.z - shot.point.z;
                if (inventory != null && shot.age > 0.65f && dx * dx + dz * dz < 0.58f * 0.58f
                        && Math.abs(player.y - shot.point.y) < 2.0f && inventory.add(shot.item)) {
                    shot.item = null;
                    picked = true;
                }
            } else {
                if (advance(shot.point, shot.velocity, delta, forest)) {
                    shot.stuck = true;
                    impact.play();
                } else {
                    shot.direction.set(shot.velocity).normalize();
                    if (shot.age > 8.0f) shot.item = null;
                }
            }
        }
        return picked;
    }

    static void origin(Vector3f eye, Vector3f aim, Vector3f target) {
        target.set(eye).fma(0.13f, aim);
    }

    boolean advance(Vector3f point, Vector3f velocity, float delta, Forest forest) {
        nextPoint.set(point).fma(delta, velocity).add(0.0f, -0.5f * GRAVITY * delta * delta, 0.0f);
        float length = nextPoint.sub(point, segment).length();
        if (length < 0.00001f) return false;
        segment.div(length);
        float obstacle = forest.projectileDistance(point, segment, length);
        if (nextPoint.y <= Terrain.height(nextPoint.x, nextPoint.z)) {
            float low = 0.0f;
            float high = length;
            for (int i = 0; i < 9; i++) {
                float mid = (low + high) * 0.5f;
                float x = point.x + mid * segment.x;
                float y = point.y + mid * segment.y;
                float z = point.z + mid * segment.z;
                if (y <= Terrain.height(x, z)) high = mid;
                else low = mid;
            }
            obstacle = Math.min(obstacle, high);
        }
        if (obstacle <= length) {
            point.fma(Math.max(0.0f, obstacle - 0.015f), segment);
            return true;
        }
        point.set(nextPoint);
        velocity.y -= GRAVITY * delta;
        return false;
    }

    void draw(SceneShader shader, ObjModel arrow, ObjModel stone, FloatBuffer buffer) {
        for (Shot shot : shots) {
            if (shot.item == null) continue;
            Vector3f dir = shot.direction;
            if (shot.item == Inventory.Item.ARROW) {
                pose.translation(shot.point.x - dir.x * 0.59f, shot.point.y - dir.y * 0.59f,
                                shot.point.z - dir.z * 0.59f).rotateTowards(-dir.x, -dir.y, -dir.z,
                        Math.abs(dir.y) > 0.97f ? 1.0f : 0.0f, Math.abs(dir.y) > 0.97f ? 0.0f : 1.0f, 0.0f);
                shader.draw(arrow, pose, false, buffer);
            } else {
                pose.translation(shot.point).rotateY(shot.age * 9.0f).scale(0.095f);
                shader.draw(stone, pose, false, buffer);
            }
        }
    }

    void preview(SceneShader shader, ObjModel marker, Forest forest, Vector3f eye, Vector3f aim, float speed,
                 FloatBuffer buffer) {
        origin(eye, aim, previewPoint);
        previewVelocity.set(aim).mul(speed);
        for (int i = 0; i < 78; i++) {
            boolean hit = advance(previewPoint, previewVelocity, 0.025f, forest);
            if (hit || (i >= 2 && i % 2 == 0)) {
                pose.translation(previewPoint).scale(hit ? 0.085f : 0.020f + i * 0.00042f);
                shader.drawEmissive(marker, pose, buffer);
            }
            if (hit) break;
        }
    }

    private static final class Shot {
        final Vector3f point = new Vector3f();
        final Vector3f velocity = new Vector3f();
        final Vector3f direction = new Vector3f();
        Inventory.Item item;
        boolean stuck, remote;
        float age;
    }
}
