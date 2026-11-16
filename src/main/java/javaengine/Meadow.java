package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class Meadow implements AutoCloseable {
    private static final int GRID = 29;
    static final int GRAZING_BYTES = GRID * GRID;
    private static final float REGROW_SECONDS = 32.0f;
    private static final float CELL_SIZE = 1.65f;
    private static final float HALF_CELL = CELL_SIZE * 0.5f;
    private static final float BORDER_DEPTH = 5.4f;
    private static final float BORDER_SEGMENT = 3.6f;
    private static final float REACH_SQUARED = 2.45f * 2.45f;
    private static final int[] ROOTS = {0x465F28, 0x4D682B, 0x506428, 0x607430};
    private static final int[] TIPS = {0xA0A754, 0x859B49, 0xA9AC59, 0xB8AE65, 0x8D9E49};
    private static final int[] BORDER_ROOTS = {0x344B22, 0x3F5726, 0x465A27};
    private static final int[] BORDER_TIPS = {0x80924B, 0x949E55, 0xA7A05A, 0xB1AE64};
    private final Patch[][] patches = new Patch[GRID][GRID];
    private final List<Border> border = new ArrayList<>();
    private final Matrix4f pose = new Matrix4f();
    private final Vector3f localEye = new Vector3f();
    private final Vector3f contact = new Vector3f();

    Meadow() {
        var random = new Random(351377);
        try {
            for (int row = 0; row < GRID; row++) {
                for (int col = 0; col < GRID; col++) {
                    float x = (col - GRID / 2) * CELL_SIZE;
                    float z = (row - GRID / 2) * CELL_SIZE;
                    double waves = Math.sin(x * 0.39 - z * 0.19) + 0.58 * Math.sin(z * 0.48 + 1.7)
                            + 0.36 * Math.cos(x * 0.64 + z * 0.41);
                    boolean trail = Math.abs(x - Math.sin(z * 0.22) * 1.8) < 1.15 && Math.abs(z) < 12.0;
                    boolean frontPrairie = Math.abs(z - 1.8f) < 3.8f && Math.abs(x) > 1.5f && Math.abs(x) < 7.0f;
                    boolean tall = !trail && (frontPrairie || waves > 0.11);
                    patches[row][col] = new Patch(x, z, tall, random);
                }
            }
            createBorder();
        } catch (RuntimeException | Error e) {
            close();
            throw e;
        }
    }

    private void createBorder() {
        var random = new Random(890713);
        float inner = Terrain.WALK_LIMIT;
        float outer = inner + BORDER_DEPTH;
        for (float x = -outer; x < outer; x += BORDER_SEGMENT) {
            float end = Math.min(x + BORDER_SEGMENT, outer);
            border.add(borderSection(x, end, -outer, -inner, random));
            border.add(borderSection(x, end, inner, outer, random));
        }
        for (float z = -inner; z < inner; z += BORDER_SEGMENT) {
            float end = Math.min(z + BORDER_SEGMENT, inner);
            border.add(borderSection(-outer, -inner, z, end, random));
            border.add(borderSection(inner, outer, z, end, random));
        }
    }

    private static Border borderSection(float x1, float x2, float z1, float z2, Random random) {
        float x = (x1 + x2) * 0.5f;
        float z = (z1 + z2) * 0.5f;
        float y = Terrain.height(x, z);
        int count = Math.round((x2 - x1) * (z2 - z1) * 25.0f);
        var mesh = new PlantMesh(count * 12);
        for (int i = 0; i < count; i++) {
            float rootX = random.nextFloat(x1, x2);
            float rootZ = random.nextFloat(z1, z2);
            float outward = Math.max(Math.abs(rootX), Math.abs(rootZ)) - Terrain.WALK_LIMIT;
            float rise = Math.min(outward / 2.3f, 1.0f);
            float irregularity = 0.24f * (float) Math.sin(rootX * 0.81f + rootZ * 0.67f);
            float height = 1.70f + rise * 0.92f + irregularity + random.nextFloat(0.0f, 0.82f);
            float azimuth = random.nextFloat(0.0f, 6.284f);
            blade(mesh, rootX - x, Terrain.height(rootX, rootZ) - y - 0.028f, rootZ - z, height,
                    random.nextFloat(0.025f, 0.082f), (float) Math.cos(azimuth), (float) Math.sin(azimuth),
                    BORDER_ROOTS[random.nextInt(BORDER_ROOTS.length)], BORDER_TIPS[random.nextInt(BORDER_TIPS.length)],
                    i % 5 == 0);
        }
        return new Border(x, y, z, mesh.upload());
    }

    void update(float seconds, float windStrength) {
        for (Patch[] row : patches) {
            for (Patch patch : row) {
                patch.fire.update(seconds);
                if (patch.cut) {
                    patch.cutHeight = Math.max(0.16f, patch.cutHeight - 6.5f * seconds);
                }
            }
        }
        for (int row = 0; row < GRID; row++) {
            for (int col = 0; col < GRID; col++) {
                Patch patch = patches[row][col];
                if (!patch.fire.burning()) {
                    continue;
                }
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx != 0 || dz != 0) {
                            spread(patch, row + dz, col + dx, dx, dz, windStrength);
                        }
                    }
                }
            }
        }
    }

    private void spread(Patch source, int row, int col, int dx, int dz, float windStrength) {
        if (row < 0 || col < 0 || row >= GRID || col >= GRID) {
            return;
        }
        Patch target = patches[row][col];
        float downwind = dx * Wind.X + dz * Wind.Z;
        float delay = (target.tall ? 1.20f : 2.25f) - windStrength * downwind * 0.47f
                + (dx != 0 && dz != 0 ? 0.48f : 0.0f);
        if (!target.cut && target.fire.canIgnite() && source.fire.age() >= delay) {
            contact.set(-dx * 0.38f, 0.08f, -dz * 0.38f);
            target.fire.ignite(contact);
        }
    }

    boolean cut(Vector3f eye, Vector3f aim, WoodChips particles) {
        if (aim.y > 0.60f) {
            return false;
        }
        float looking = (float) Math.hypot(aim.x, aim.z);
        boolean hit = false;
        for (Patch[] row : patches) {
            for (Patch patch : row) {
                if (!patch.tall || patch.cut || !patch.fire.canIgnite()) {
                    continue;
                }
                float dx = patch.x - eye.x;
                float dz = patch.z - eye.z;
                float distanceSquared = dx * dx + dz * dz;
                float forward = (dx * aim.x + dz * aim.z) / Math.max(0.001f, looking);
                if (distanceSquared <= REACH_SQUARED && (distanceSquared < 0.45f
                        || forward > 0.44f * Math.sqrt(distanceSquared))) {
                    patch.cut = true;
                    hit = true;
                    contact.set(patch.x, patch.y + 0.44f, patch.z);
                    particles.grassCut(contact, aim);
                }
            }
        }
        return hit;
    }

    void aimFire(IgnitionAim ignition, Vector3f eye, Vector3f aim) {
        for (Patch[] row : patches) {
            for (Patch patch : row) {
                if (patch.cut || Math.abs(patch.x - eye.x) > 2.4f || Math.abs(patch.z - eye.z) > 2.4f) {
                    continue;
                }
                localEye.set(eye).sub(patch.x, patch.y, patch.z);
                float height = patch.tall ? 0.95f : 0.31f;
                ignition.consider(patch.fire, localEye, aim, -HALF_CELL + 0.05f, -0.13f, -HALF_CELL + 0.05f,
                        HALF_CELL - 0.05f, height, HALF_CELL - 0.05f);
            }
        }
    }

    boolean burningNear(float x, float z) {
        Patch patch = patchAt(x, z);
        return patch != null && patch.fire.burning() && patch.fire.age() > 1.25f;
    }

    boolean tallAt(float x, float z) {
        Patch patch = patchAt(x, z);
        return patch != null && patch.tall && !patch.cut && patch.fire.canIgnite();
    }

    void catchFromWood(float x, float z) {
        Patch patch = patchAt(x, z);
        if (patch != null && !patch.cut && patch.fire.canIgnite()) {
            contact.set(x - patch.x, 0.08f, z - patch.z);
            patch.fire.ignite(contact);
        }
    }

    void blast(Vector3f origin, float radius) {
        float squared = radius * radius;
        for (Patch[] row : patches) {
            for (Patch patch : row) {
                if (patch.distanceSquared(origin) <= squared) {
                    patch.cut = true;
                    patch.cutHeight = 0.16f;
                }
            }
        }
    }

    private Patch patchAt(float x, float z) {
        int col = Math.round(x / CELL_SIZE) + GRID / 2;
        int row = Math.round(z / CELL_SIZE) + GRID / 2;
        if (row < 0 || col < 0 || row >= GRID || col >= GRID) {
            return null;
        }
        return patches[row][col];
    }

    boolean findGrazing(Vector3f cow, Forest forest, Vector3f destination) {
        float nearest = 9 * 9;
        boolean found = false;
        for (Patch[] row : patches) for (Patch patch : row) {
            float distance = patch.distanceSquared(cow);
            if (distance >= nearest || !edible(patch) || forest.blocksPlayer(patch.x, patch.z)) continue;
            nearest = distance;
            destination.set(patch.x, patch.y, patch.z);
            found = true;
        }
        return found;
    }

    boolean graze(float x, float z) {
        Patch patch = patchAt(x, z);
        if (!edible(patch)) return false;
        patch.grazeTime = REGROW_SECONDS;
        return true;
    }

    private static boolean edible(Patch patch) {
        return patch != null && patch.tall && !patch.cut && patch.fire.canIgnite() && patch.grazeTime < 4;
    }

    void regrow(float delta) {
        for (Patch[] row : patches) for (Patch patch : row) {
            patch.grazeTime = Math.max(0, patch.grazeTime - delta);
        }
    }

    void writeGrazing(java.nio.ByteBuffer state) {
        for (Patch[] row : patches) for (Patch patch : row) {
            state.put((byte) Math.round(patch.grazeTime * 255 / REGROW_SECONDS));
        }
    }

    void readGrazing(java.nio.ByteBuffer state) {
        for (Patch[] row : patches) for (Patch patch : row) {
            patch.grazeTime = Byte.toUnsignedInt(state.get()) * REGROW_SECONDS / 255;
        }
    }

    float fireProximity(Vector3f player) {
        float distanceSquared = 144.0f;
        for (Patch[] row : patches) {
            for (Patch patch : row) {
                if (patch.fire.burning()) {
                    distanceSquared = Math.min(distanceSquared, patch.distanceSquared(player));
                }
            }
        }
        return 1.0f - (float) Math.sqrt(distanceSquared) / 12.0f;
    }

    void fireLights(FireVisuals fire, Vector3f eye) {
        for (Patch[] row : patches) {
            for (Patch patch : row) {
                if (patch.fire.burning() && patch.distanceSquared(eye) < 42.0f) {
                    fire.addLight(patch.fire, firePose(patch), 0.58f);
                }
            }
        }
    }

    void draw(SceneShader shader, Vector3f eye, Vector3f aim, FloatBuffer buffer) {
        for (Patch[] row : patches) {
            for (Patch patch : row) {
                if (patch.visible(eye, aim)) {
                    float charAmount = patch.fire.charAmount();
                    float height = patch.cutHeight * (1.0f - charAmount * 0.78f)
                            * (1 - .83f * patch.grazeTime / REGROW_SECONDS);
                    shader.drawGrass(patch.mesh, pose.translation(patch.x, patch.y, patch.z), height, charAmount, buffer);
                }
            }
        }
        for (Border section : border) {
            float dx = section.x - eye.x;
            float dz = section.z - eye.z;
            float distance = dx * dx + dz * dz;
            if (distance < 1450.0f && (distance < 28.0f || dx * aim.x + dz * aim.z > -4.0f)) {
                shader.drawGrass(section.mesh, pose.translation(section.x, section.y, section.z), 1.0f, 0.0f, buffer);
            }
        }
    }

    void drawFire(SceneShader shader, FireVisuals fire, Vector3f eye, Vector3f aim, FloatBuffer buffer) {
        for (Patch[] row : patches) {
            for (Patch patch : row) {
                if (patch.fire.burning() && patch.visible(eye, aim)) {
                    fire.brush(shader, patch.fire, firePose(patch), patch.tall ? 0.82f : 0.55f,
                            patch.distanceSquared(eye) < 185.0f, buffer);
                }
            }
        }
    }

    private Matrix4f firePose(Patch patch) {
        return pose.translation(patch.x, patch.y + 0.06f - patch.fire.ignitionPoint().y, patch.z);
    }

    @Override
    public void close() {
        for (Patch[] row : patches) {
            for (Patch patch : row) {
                if (patch != null) {
                    patch.mesh.close();
                }
            }
        }
        for (Border section : border) {
            section.mesh.close();
        }
    }

    private record Border(float x, float y, float z, ObjModel mesh) { }

    private static final class Patch {
        final float x;
        final float y;
        final float z;
        final boolean tall;
        final Burnable fire;
        final ObjModel mesh;
        boolean cut;
        float cutHeight = 1.0f;
        float grazeTime;

        Patch(float x, float z, boolean tall, Random random) {
            this.x = x;
            y = Terrain.height(x, z);
            this.z = z;
            this.tall = tall;
            fire = new Burnable(tall ? 7.0f : 5.1f);
            int blades = tall ? 74 : 34;
            var builder = new PlantMesh(blades * 12);
            for (int i = 0; i < blades; i++) {
                float bx = random.nextFloat(-HALF_CELL, HALF_CELL);
                float bz = random.nextFloat(-HALF_CELL, HALF_CELL);
                float ground = Terrain.height(x + bx, z + bz) - y - 0.018f;
                float height = tall ? random.nextFloat(0.43f, 1.16f) : random.nextFloat(0.13f, 0.37f);
                float speck = (float) Math.sin((x + bx) * 6.7 + (z + bz) * 3.4);
                height *= 0.90f + 0.15f * speck;
                float azimuth = random.nextFloat(0.0f, 6.284f);
                float rx = (float) Math.cos(azimuth);
                float rz = (float) Math.sin(azimuth);
                float width = random.nextFloat(0.012f, tall ? 0.049f : 0.037f);
                int root = ROOTS[random.nextInt(ROOTS.length)];
                int tip = TIPS[random.nextInt(TIPS.length)];
                blade(builder, bx, ground, bz, height, width, rx, rz, root, tip, tall && i % 6 == 0);
            }
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    float ax = -HALF_CELL + i * CELL_SIZE / 4.0f;
                    float az = -HALF_CELL + j * CELL_SIZE / 4.0f;
                    float bx = ax + CELL_SIZE / 4.0f;
                    float bz = az + CELL_SIZE / 4.0f;
                    ash(builder, ax, az);
                    ash(builder, ax, bz);
                    ash(builder, bx, az);
                    ash(builder, bx, az);
                    ash(builder, ax, bz);
                    ash(builder, bx, bz);
                }
            }
            mesh = builder.upload();
        }

        private void ash(PlantMesh mesh, float localX, float localZ) {
            float ground = Terrain.height(x + localX, z + localZ) - y + 0.021f;
            mesh.vertex(localX, ground, localZ, 0, 1, 0, 0x6A5D4E, -1.0f);
        }

        float distanceSquared(Vector3f eye) {
            float dx = x - eye.x;
            float dz = z - eye.z;
            return dx * dx + dz * dz;
        }

        boolean visible(Vector3f eye, Vector3f aim) {
            float distance = distanceSquared(eye);
            float facing = (x - eye.x) * aim.x + (z - eye.z) * aim.z;
            return distance <= 760.0f && (distance <= 18.0f || facing >= -1.65f);
        }
    }

    private static void blade(PlantMesh mesh, float x, float y, float z, float height, float width,
                              float sideX, float sideZ, int root, int tip, boolean seed) {
        float midX = x - sideZ * height * 0.15f;
        float midZ = z + sideX * height * 0.15f;
        float tipX = x - sideZ * height * 0.40f;
        float tipZ = z + sideX * height * 0.40f;
        float mid = height * 0.54f;
        float mx = sideX * width;
        float mz = sideZ * width;
        mesh.vertex(x - mx, y, z - mz, 0, 1, 0, root, 0);
        mesh.vertex(x + mx, y, z + mz, 0, 1, 0, root, 0);
        mesh.vertex(midX + mx * .68f, y + mid, midZ + mz * .68f, 0, 1, 0, tip, mid);
        mesh.vertex(x - mx, y, z - mz, 0, 1, 0, root, 0);
        mesh.vertex(midX + mx * .68f, y + mid, midZ + mz * .68f, 0, 1, 0, tip, mid);
        mesh.vertex(midX - mx * .68f, y + mid, midZ - mz * .68f, 0, 1, 0, tip, mid);
        mesh.vertex(midX - mx * .68f, y + mid, midZ - mz * .68f, 0, 1, 0, tip, mid);
        mesh.vertex(midX + mx * .68f, y + mid, midZ + mz * .68f, 0, 1, 0, tip, mid);
        mesh.vertex(tipX, y + height, tipZ, 0, 1, 0, tip, height);
        if (seed) {
            float grow = height * 0.84f;
            mesh.vertex(tipX - sideX * 0.037f, y + grow, tipZ - sideZ * 0.037f, 0, 1, 0, 0xAD9656, grow);
            mesh.vertex(tipX + sideX * 0.037f, y + grow, tipZ + sideZ * 0.037f, 0, 1, 0, 0xAD9656, grow);
            mesh.vertex(tipX, y + height * 1.10f, tipZ, 0, 1, 0, 0xD1B779, height * 1.10f);
        }
    }
}
