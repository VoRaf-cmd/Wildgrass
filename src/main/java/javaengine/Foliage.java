package javaengine;

import org.joml.Vector3f;

import java.util.Random;

final class Foliage {
    private static final float[][] CROWNS = {
            {-.95f, 2.83f, -.18f, .77f, .78f, .67f}, {.80f, 3.04f, .16f, .78f, .81f, .80f},
            {.14f, 3.21f, -.48f, .84f, .91f, .86f}, {-.15f, 3.22f, .34f, .93f, .74f, .83f},
            {.20f, 3.68f, -.09f, .91f, .75f, .75f}, {-.58f, 3.35f, -.53f, .69f, .63f, .65f}
    };
    private static final int[] LEAF_TONES = {0x527334, 0x6C883C, 0x678640, 0x83954B, 0x486C35, 0x77924E};

    private Foliage() { }

    static ObjModel mesh() {
        var random = new Random(73969);
        var mesh = new PlantMesh(CROWNS.length * 146 * 18);
        var root = new Vector3f();
        var axis = new Vector3f();
        var side = new Vector3f();
        var normal = new Vector3f();
        var keel = new Vector3f();
        var tip = new Vector3f();
        Vector3f[] edge = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
        for (float[] c : CROWNS) {
            for (int i = 0; i < 146; i++) {
                float azimuth = random.nextFloat(0.0f, 6.2832f);
                float y = random.nextFloat(-1.0f, 1.0f);
                float radius = (float) Math.sqrt(1.0f - y * y);
                float dx = (float) Math.cos(azimuth) * radius;
                float dz = (float) Math.sin(azimuth) * radius;
                float outer = random.nextFloat(0.63f, 1.15f);
                root.set(c[0] + dx * c[3] * outer, c[1] + y * c[4] * outer * 0.80f,
                        c[2] + dz * c[5] * outer);
                axis.set(dx + random.nextFloat(-0.55f, 0.55f), random.nextFloat(-0.55f, 0.35f),
                        dz + random.nextFloat(-0.55f, 0.55f)).normalize();
                side.set(-axis.z, random.nextFloat(-0.24f, 0.24f), axis.x).normalize();
                normal.set(side).cross(axis).normalize();
                if (normal.y < 0) normal.negate();
                float length = random.nextFloat(0.12f, 0.27f);
                float width = length * random.nextFloat(0.25f, 0.41f);
                keel.set(root).fma(length * 0.52f, axis).fma(0.018f, normal);
                tip.set(root).fma(length, axis).add(0.0f, -0.022f, 0.0f);
                edge[0].set(root).fma(length * 0.34f, axis).fma(width, side);
                edge[1].set(root).fma(length * 0.70f, axis).fma(width * 0.73f, side);
                edge[2].set(root).fma(length * 0.70f, axis).fma(-width * 0.73f, side);
                edge[3].set(root).fma(length * 0.34f, axis).fma(-width, side);
                int color = LEAF_TONES[random.nextInt(LEAF_TONES.length)];
                triangle(mesh, root, edge[0], keel, normal, color);
                triangle(mesh, edge[0], edge[1], keel, normal, color);
                triangle(mesh, edge[1], tip, keel, normal, color);
                int shaded = ((color & 0xfefefe) >> 1) + ((color & 0xfcfcfc) >> 2);
                triangle(mesh, tip, edge[2], keel, normal, shaded);
                triangle(mesh, edge[2], edge[3], keel, normal, shaded);
                triangle(mesh, edge[3], root, keel, normal, shaded);
            }
        }
        return mesh.upload();
    }

    private static void triangle(PlantMesh mesh, Vector3f a, Vector3f b, Vector3f c, Vector3f normal, int color) {
        mesh.vertex(a.x, a.y, a.z, normal.x, normal.y, normal.z, color, 0.0f);
        mesh.vertex(b.x, b.y, b.z, normal.x, normal.y, normal.z, color, 0.0f);
        mesh.vertex(c.x, c.y, c.z, normal.x, normal.y, normal.z, color, 0.0f);
    }
}
