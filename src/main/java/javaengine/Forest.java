package javaengine;

import org.joml.Vector3f;

import java.nio.FloatBuffer;

final class Forest {
    private final GroveTree[] trees;
    private final Vector3f candidate = new Vector3f();
    private final Vector3f fallShadow = new Vector3f();
    private float shadowContact;

    Forest() {
        float[][] positions = {
                {0, 0}, {-5.8f, -4.6f}, {6.1f, -5.0f}, {-10.2f, -9.5f}, {-5.3f, -13.8f},
                {0.7f, -12.2f}, {9.5f, -13.1f}, {15.1f, -7.7f}, {-15.7f, -15.0f}, {5.0f, -18.2f},
                {-13.2f, 2.0f}, {13.3f, 1.2f}, {-6.3f, 9.6f}, {5.9f, 11.2f}, {-15.0f, 13.8f},
                {16.0f, 12.8f}, {-0.4f, 18.0f}, {17.8f, -17.1f}, {-17.1f, -2.6f}
        };
        trees = new GroveTree[positions.length];
        for (int i = 0; i < positions.length; i++) {
            trees[i] = new GroveTree(new TreeTarget(positions[i][0], positions[i][1]), new WoodDrops());
        }
    }

    boolean blocksPlayer(float x, float z) {
        for (GroveTree members : trees) {
            if (members.tree().blocksPlayer(x, z)) {
                return true;
            }
        }
        return false;
    }

    TreeTarget chop(Vector3f eye, Vector3f aim, Vector3f impact) {
        TreeTarget nearestTree = null;
        float nearest = Float.POSITIVE_INFINITY;
        for (GroveTree members : trees) {
            float distance = members.tree().axeDistance(eye, aim, candidate);
            if (distance < nearest) {
                nearest = distance;
                nearestTree = members.tree();
                impact.set(candidate);
            }
        }
        if (nearestTree != null) {
            nearestTree.chop(impact);
        }
        return nearestTree;
    }

    float projectileDistance(Vector3f origin, Vector3f direction, float segmentLength) {
        float nearest = Float.POSITIVE_INFINITY;
        float reach = segmentLength + 4.5f;
        for (GroveTree members : trees) {
            Vector3f center = members.tree().center();
            float dx = center.x - origin.x;
            float dz = center.z - origin.z;
            if (dx * dx + dz * dz < reach * reach) {
                nearest = Math.min(nearest, members.tree().projectileDistance(origin, direction));
            }
        }
        return nearest;
    }

    void blast(Vector3f origin, float radius) {
        for (GroveTree members : trees) members.tree().blast(origin, radius);
    }

    void update(float delta, Meadow meadow, WoodChips chips, ImpactMotion motion, SoundEffect landing) {
        for (GroveTree members : trees) {
            TreeTarget tree = members.tree();
            tree.catchFromGrass(meadow);
            members.drops().spreadFire(meadow);
            if (tree.update(delta, members.drops())) {
                landing.play();
                motion.land();
                chips.landingDust(tree.center(), tree.fallDirection());
            }
        }
    }

    boolean collect(float delta, Vector3f player, Inventory inventory) {
        boolean collected = false;
        for (GroveTree members : trees) {
            collected |= members.drops().update(delta, player, inventory);
        }
        return collected;
    }

    void aimFire(IgnitionAim ignition, Vector3f eye, Vector3f aim) {
        for (GroveTree members : trees) {
            if (members.tree().center().distanceSquared(eye) < 48.0f) {
                members.tree().aimFire(ignition, eye, aim);
                members.drops().aimFire(ignition, eye, aim);
            }
        }
    }

    void applyShadows(SceneShader shader, Vector3f eye) {
        float nearest = Float.POSITIVE_INFINITY;
        shadowContact = 0.0f;
        for (GroveTree members : trees) {
            TreeTarget tree = members.tree();
            float distance = tree.center().distanceSquared(eye);
            if (tree.groundContact() > 0.0f && distance < nearest) {
                nearest = distance;
                shadowContact = tree.groundContact();
                fallShadow.set(tree.fallDirection());
                shader.setShadowOrigin(tree.center().x, tree.center().z);
            }
        }
        shader.setTreeShadow(fallShadow.x, fallShadow.z, shadowContact);
    }

    void fireLights(FireVisuals fire, Vector3f eye) {
        for (GroveTree members : trees) {
            if (members.tree().center().distanceSquared(eye) < 60.0f) {
                members.tree().fireLights(fire);
                members.drops().fireLights(fire);
            }
        }
    }

    void draw(SceneShader shader, ObjModel tree, ObjModel leaves, ObjModel stump, ObjModel scar, ObjModel log,
              FireVisuals fire, Vector3f eye, Vector3f aim, FloatBuffer buffer) {
        for (GroveTree members : trees) {
            Vector3f center = members.tree().center();
            float dx = center.x - eye.x;
            float dz = center.z - eye.z;
            if (dx * dx + dz * dz < 1700.0f && dx * aim.x + dz * aim.z > -7.0f) {
                members.tree().draw(shader, tree, leaves, stump, scar, fire, buffer);
                members.drops().draw(shader, log, fire, buffer);
            }
        }
    }

    private record GroveTree(TreeTarget tree, WoodDrops drops) { }
}
