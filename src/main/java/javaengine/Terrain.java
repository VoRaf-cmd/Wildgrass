package javaengine;

final class Terrain {
    static final float WALK_LIMIT = 23.0f;
    private static final float TILE = 0.8f;
    private static final int HALF_GRID = 56;

    private Terrain() { }

    static float height(float x, float z) {
        float distance = (float) Math.hypot(x, z);
        float rise = Math.clamp((distance - 4.0f) / 10.0f, 0.0f, 1.0f);
        return rise * rise * (0.68f * (float) Math.sin(x * 0.15f + z * 0.16f)
                + 0.54f * (float) Math.sin(z * 0.23f - 0.4f)
                + 0.24f * (float) Math.cos(x * 0.31f - z * 0.12f));
    }

    static ObjModel mesh() {
        var mesh = new PlantMesh(HALF_GRID * HALF_GRID * 24);
        for (int z = -HALF_GRID; z < HALF_GRID; z++) {
            for (int x = -HALF_GRID; x < HALF_GRID; x++) {
                float left = x * TILE;
                float top = z * TILE;
                float right = left + TILE;
                float bottom = top + TILE;
                vertex(mesh, left, top);
                vertex(mesh, left, bottom);
                vertex(mesh, right, top);
                vertex(mesh, right, top);
                vertex(mesh, left, bottom);
                vertex(mesh, right, bottom);
            }
        }
        return mesh.upload();
    }

    private static void vertex(PlantMesh mesh, float x, float z) {
        float dx = height(x - 0.24f, z) - height(x + 0.24f, z);
        float dz = height(x, z - 0.24f) - height(x, z + 0.24f);
        mesh.vertex(x, height(x, z), z, dx, 0.48f, dz, 0x819446, 0.0f);
    }
}
