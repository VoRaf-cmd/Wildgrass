package javaengine;

import org.joml.Matrix4f;
import org.joml.Intersectionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

final class TreeTarget {
    private static final int HITS_TO_FELL = 5;
    private static final float FALL_DURATION = 1.1f;
    private static final float LAND_PAUSE = 0.26f;
    private static final float STUMP_HEIGHT = 0.32f;
    private static final float FALL_ANGLE = 1.33f;
    private static final float FALL_SLIDE = 0.62f;
    private static final float HIT_SWAY_DURATION = 0.30f;
    private static final float TRUNK_RADIUS = 0.31f;
    private static final float PLAYER_CLEARANCE = 0.62f;
    private static final float AXE_REACH = 2.0f;

    private final Vector3f[] cuts = new Vector3f[HITS_TO_FELL];
    private final Vector3f fallDirection = new Vector3f();
    private final Matrix4f treeModel = new Matrix4f();
    private final Matrix4f cutModel = new Matrix4f();
    private final Matrix4f inverseTree = new Matrix4f();
    private final Matrix4f stumpModel = new Matrix4f();
    private final Vector3f localEye = new Vector3f();
    private final Vector3f localAim = new Vector3f();
    private final Vector2f projectileInterval = new Vector2f();
    private final Vector3f center;
    private final float yaw;
    private final Burnable trunkFire = new Burnable(17.0f);
    private final Burnable stumpFire = new Burnable(10.0f);
    private int hits;
    private float fallTime;
    private float hitTime = HIT_SWAY_DURATION;
    private boolean blasted;

    TreeTarget(float x, float z) {
        center = new Vector3f(x, Terrain.height(x, z), z);
        yaw = (x * 0.62f + z * 0.45f) % 6.2832f;
        stumpModel.translation(center).rotateY(yaw);
        updatePose();
    }

    boolean blocksPlayer(float x, float z) {
        x -= center.x;
        z -= center.z;
        if (x * x + z * z < PLAYER_CLEARANCE * PLAYER_CLEARANCE) {
            return true;
        }
        if (fallTime < FALL_DURATION) {
            return false;
        }
        float alongTrunk = Math.clamp(x * fallDirection.x + z * fallDirection.z, FALL_SLIDE, 2.72f);
        float dx = x - alongTrunk * fallDirection.x;
        float dz = z - alongTrunk * fallDirection.z;
        return dx * dx + dz * dz < 0.46f * 0.46f;
    }

    float axeDistance(Vector3f eye, Vector3f aim, Vector3f impact) {
        localEye.set(eye).sub(center);
        if (isFalling() || !intersect(localEye, aim, impact)) {
            return Float.POSITIVE_INFINITY;
        }
        return impact.add(center).distance(eye);
    }

    void chop(Vector3f impact) {
        cuts[hits++] = new Vector3f(impact).sub(center).rotateY(-yaw);
        hitTime = 0.0f;
        fallDirection.set(center.x - impact.x, 0.0f, center.z - impact.z).normalize();
    }

    boolean isFalling() {
        return hits == HITS_TO_FELL || blasted;
    }

    void blast(Vector3f origin, float radius) {
        float dx = center.x - origin.x;
        float dz = center.z - origin.z;
        if (isFalling() || dx * dx + dz * dz > radius * radius || Math.abs(center.y - origin.y) > radius) return;
        blasted = true;
        fallDirection.set(dx, 0.0f, dz);
        if (fallDirection.lengthSquared() < 0.001f) fallDirection.set(0.0f, 0.0f, -1.0f);
        else fallDirection.normalize();
    }

    boolean update(float seconds, WoodDrops drops) {
        trunkFire.update(seconds);
        stumpFire.update(seconds);
        hitTime = Math.min(HIT_SWAY_DURATION, hitTime + seconds);
        float previous = fallTime;
        if (isFalling() && fallTime < FALL_DURATION + LAND_PAUSE) {
            fallTime += seconds;
            if (fallTime >= FALL_DURATION + LAND_PAUSE) {
                drops.dropLogs(center, fallDirection);
            }
        }
        updatePose();
        return previous < FALL_DURATION && fallTime >= FALL_DURATION;
    }

    Vector3f fallDirection() {
        return fallDirection;
    }

    Vector3f center() {
        return center;
    }

    void catchFromGrass(Meadow meadow) {
        if (trunkFire.canIgnite() && meadow.burningNear(center.x, center.z)) {
            localEye.set(0.0f, 0.42f, 0.28f);
            trunkFire.ignite(localEye);
        }
        if (isFalling() && stumpFire.canIgnite() && meadow.burningNear(center.x, center.z)) {
            localEye.set(0.0f, 0.26f, 0.20f);
            stumpFire.ignite(localEye);
        }
        if (trunkFire.burning() && trunkFire.age() > 1.1f) {
            treeModel.transformPosition(trunkFire.ignitionPoint(), localEye);
            if (localEye.y - Terrain.height(localEye.x, localEye.z) < 1.48f) {
                meadow.catchFromWood(localEye.x, localEye.z);
            }
        }
        if (stumpFire.burning() && stumpFire.age() > 0.95f) {
            meadow.catchFromWood(center.x, center.z);
        }
    }

    float groundContact() {
        return Math.clamp((fallTime / FALL_DURATION - 0.7f) / 0.3f, 0.0f, 1.0f);
    }

    private void updatePose() {
        float progress = Math.min(1.0f, fallTime / FALL_DURATION);
        float fall = progress * progress;
        float groundRise = Terrain.height(center.x + 3.1f * fallDirection.x, center.z + 3.1f * fallDirection.z)
                - center.y;
        float angle = fall * (FALL_ANGLE - Math.clamp(groundRise / 3.1f, -0.17f, 0.17f));
        float slide = fall * FALL_SLIDE;
        treeModel.translation(center.x + slide * fallDirection.x, center.y + STUMP_HEIGHT + fall * 0.09f,
                center.z + slide * fallDirection.z);
        if (hits > 0 || blasted) {
            float sway = hitTime / HIT_SWAY_DURATION;
            float wobble = (float) Math.sin(sway * Math.PI * 2.0) * (1.0f - sway) * 0.031f;
            treeModel.rotate(angle + wobble, fallDirection.z, 0.0f, -fallDirection.x);
        }
        treeModel.translate(0.0f, -STUMP_HEIGHT, 0.0f).rotateY(yaw);
    }

    void aimFire(IgnitionAim ignition, Vector3f eye, Vector3f aim) {
        treeModel.invert(inverseTree);
        inverseTree.transformPosition(eye, localEye);
        inverseTree.transformDirection(aim, localAim);
        ignition.consider(trunkFire, localEye, localAim, -0.28f, isFalling() ? STUMP_HEIGHT : 0.0f, -0.28f,
                0.28f, 2.65f, 0.28f);
        if (isFalling()) {
            localEye.set(eye).sub(center).rotateY(-yaw);
            localAim.set(aim).rotateY(-yaw);
            ignition.consider(stumpFire, localEye, localAim, -0.32f, 0.0f, -0.32f, 0.32f, STUMP_HEIGHT, 0.32f);
        }
    }

    float projectileDistance(Vector3f origin, Vector3f direction) {
        treeModel.invert(inverseTree);
        inverseTree.transformPosition(origin, localEye);
        inverseTree.transformDirection(direction, localAim);
        float distance = rayBox(-0.27f, STUMP_HEIGHT, -0.27f, 0.27f, 2.68f, 0.27f);
        localEye.set(origin).sub(center);
        localAim.set(direction);
        return Math.min(distance, rayBox(-0.32f, 0.0f, -0.32f, 0.32f, STUMP_HEIGHT, 0.32f));
    }

    private float rayBox(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        return Intersectionf.intersectRayAab(localEye.x, localEye.y, localEye.z, localAim.x, localAim.y, localAim.z,
                minX, minY, minZ, maxX, maxY, maxZ, projectileInterval)
                ? Math.max(0.0f, projectileInterval.x) : Float.POSITIVE_INFINITY;
    }

    void fireLights(FireVisuals fire) {
        fire.addLight(trunkFire, treeModel, 1.25f);
        fire.addLight(stumpFire, stumpModel, 0.95f);
    }

    void draw(SceneShader shader, ObjModel tree, ObjModel leaves, ObjModel stump, ObjModel scar, FireVisuals fire,
              FloatBuffer buffer) {
        shader.drawCharred(stump, stumpModel, stumpFire.charAmount(), buffer);
        shader.drawTree(tree, treeModel, trunkFire.charAmount(), buffer);
        shader.drawTree(leaves, treeModel, trunkFire.charAmount(), buffer);

        for (int i = 0; i < hits; i++) {
            Vector3f cut = cuts[i];
            // Desloca levemente as marcas para mostrar golpes sucessivos no mesmo ponto.
            float azimuth = (float) Math.atan2(cut.x, cut.z) + (i % 3 - 1) * 0.09f;
            float height = cut.y + (i / 3 - 0.4f) * 0.10f;
            float radius = 0.340f + 0.16f * Math.max(0.0f, 1.0f - height / 0.68f);
            float size = 0.83f + 0.13f * i;
            cutModel.set(treeModel).rotateY(azimuth).translate(0.0f, height, radius + 0.002f * i)
                    .rotateZ((i % 3 - 1) * 0.14f).scale(size);
            shader.drawCharred(scar, cutModel, trunkFire.charAmount(), buffer);
        }
        fire.draw(shader, trunkFire, treeModel, 0.65f, buffer);
        fire.draw(shader, stumpFire, stumpModel, 0.46f, buffer);
    }

    private static boolean intersect(Vector3f eye, Vector3f aim, Vector3f impact) {
        float a = aim.x * aim.x + aim.z * aim.z;
        if (a < 0.00001f) {
            return false;
        }
        float halfB = eye.x * aim.x + eye.z * aim.z;
        float c = eye.x * eye.x + eye.z * eye.z - TRUNK_RADIUS * TRUNK_RADIUS;
        float discriminant = halfB * halfB - a * c;
        if (discriminant < 0) {
            return false;
        }
        float distance = (-halfB - (float) Math.sqrt(discriminant)) / a;
        float height = eye.y + aim.y * distance;
        if (distance < 0 || distance > AXE_REACH || height < STUMP_HEIGHT || height > 2.6f) {
            return false;
        }
        impact.set(eye).fma(distance, aim);
        return true;
    }
}
