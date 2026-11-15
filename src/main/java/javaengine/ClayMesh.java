package javaengine;

import org.joml.Vector3f;

import java.util.Arrays;

/** Extracts one continuous rest mesh from rounded forms, then binds it to the skeleton. */
final class ClayMesh {
    private static final int[][] TETRAHEDRA = {
            {0, 1, 3, 7}, {0, 3, 2, 7}, {0, 2, 6, 7},
            {0, 6, 4, 7}, {0, 4, 5, 7}, {0, 5, 1, 7}
    };
    private static final float STEP = 0.038f;
    private static final int NX = 33, NY = 57, NZ = 19;
    private static final float MIN_X = -(NX - 1) * STEP / 2, MIN_Y = -0.11f, MIN_Z = -(NZ - 1) * STEP / 2;
    private static final Form[] FORMS = {
            new Form(0, 1.11f, 0.015f, .283f, .389f, .192f, 0, 0),
            new Form(0, .82f, .02f, .25f, .171f, .172f, 0, 0),
            new Form(0, 1.415f, .006f, .121f, .172f, .121f, 0, 0),
            new Form(0, 1.653f, -.017f, .276f, .273f, .237f, 0, 1),
            new Form(-.307f, 1.21f, .005f, .102f, .201f, .107f, .34f, 2),
            new Form(-.396f, .975f, -.012f, .096f, .213f, .105f, .21f, 3),
            new Form(.307f, 1.21f, .005f, .102f, .201f, .107f, -.34f, 4),
            new Form(.396f, .975f, -.012f, .096f, .213f, .105f, -.21f, 5),
            new Form(-.143f, .638f, .013f, .115f, .253f, .128f, .06f, 6),
            new Form(-.153f, .285f, .016f, .097f, .244f, .105f, 0, 7),
            new Form(-.153f, .107f, -.048f, .109f, .107f, .18f, 0, 7),
            new Form(.143f, .638f, .013f, .115f, .253f, .128f, -.06f, 8),
            new Form(.153f, .285f, .016f, .097f, .244f, .105f, 0, 9),
            new Form(.153f, .107f, -.048f, .109f, .107f, .18f, 0, 9)
    };

    private float[] vertices = new float[32768];
    private int size;

    ObjModel build() {
        var grid = new Sample[NX][NY][NZ];
        for (int x = 0; x < NX; x++) for (int y = 0; y < NY; y++) for (int z = 0; z < NZ; z++) {
            var point = new Vector3f(MIN_X + x * STEP, MIN_Y + y * STEP, MIN_Z + z * STEP);
            grid[x][y][z] = new Sample(point, field(point.x, point.y, point.z));
        }
        var corners = new Sample[8];
        for (int x = 0; x < NX - 1; x++) for (int y = 0; y < NY - 1; y++) for (int z = 0; z < NZ - 1; z++) {
            int inside = 0;
            for (int i = 0; i < 8; i++) {
                corners[i] = grid[x + (i & 1)][y + (i >> 1 & 1)][z + (i >> 2 & 1)];
                if (corners[i].distance < 0) inside++;
            }
            if (inside > 0 && inside < 8) {
                for (int[] tetrahedron : TETRAHEDRA) polygonize(corners, tetrahedron);
            }
        }
        return new ObjModel(Arrays.copyOf(vertices, size), true);
    }

    private void polygonize(Sample[] corners, int[] tetrahedron) {
        var in = new Sample[4];
        var out = new Sample[4];
        int inside = 0;
        int outside = 0;
        for (int index : tetrahedron) {
            Sample sample = corners[index];
            if (sample.distance < 0) in[inside++] = sample;
            else out[outside++] = sample;
        }
        if (inside == 1) {
            triangle(edge(in[0], out[0]), edge(in[0], out[1]), edge(in[0], out[2]));
        } else if (inside == 3) {
            triangle(edge(in[0], out[0]), edge(in[1], out[0]), edge(in[2], out[0]));
        } else if (inside == 2) {
            Vector3f a = edge(in[0], out[0]), b = edge(in[0], out[1]);
            Vector3f c = edge(in[1], out[0]), d = edge(in[1], out[1]);
            triangle(a, b, c);
            triangle(c, b, d);
        }
    }

    private static Vector3f edge(Sample inside, Sample outside) {
        float t = inside.distance / (inside.distance - outside.distance);
        return new Vector3f(inside.point).lerp(outside.point, t);
    }

    private void triangle(Vector3f a, Vector3f b, Vector3f c) {
        Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if (normal.lengthSquared() < 1e-15f) return;
        Vector3f na = normalAt(a);
        vertex(a, na);
        if (normal.dot(na) < 0) {
            vertex(c, normalAt(c));
            vertex(b, normalAt(b));
        } else {
            vertex(b, normalAt(b));
            vertex(c, normalAt(c));
        }
    }

    private void vertex(Vector3f point, Vector3f normal) {
        if (size + 18 > vertices.length) vertices = Arrays.copyOf(vertices, vertices.length * 2);
        float x = point.x, y = point.y, z = point.z;
        vertices[size++] = x;
        vertices[size++] = y;
        vertices[size++] = z;
        vertices[size++] = normal.x;
        vertices[size++] = normal.y;
        vertices[size++] = normal.z;
        float mottling = .94f + .035f * (float) Math.sin(x * 23 + y * 13) * (float) Math.sin(z * 25 - y * 15);
        float feetShade = .79f + .21f * Math.clamp(y / .72f, 0, 1);
        vertices[size++] = mottling * feetShade;
        vertices[size++] = mottling * feetShade;
        vertices[size++] = mottling * feetShade;
        vertices[size++] = 0;

        float[] distance = new float[10];
        Arrays.fill(distance, Float.POSITIVE_INFINITY);
        float nearest = Float.POSITIVE_INFINITY;
        for (Form form : FORMS) {
            float d = form.distance(x, y, z);
            distance[form.bone] = Math.min(distance[form.bone], d);
            nearest = Math.min(nearest, d);
        }
        float[] weights = new float[4];
        float total = 0;
        for (int slot = 0; slot < 4; slot++) {
            int joint = 0;
            for (int i = 1; i < distance.length; i++) if (distance[i] < distance[joint]) joint = i;
            vertices[size++] = joint;
            float weight = Math.max(0, 1 - (distance[joint] - nearest) / .145f);
            weights[slot] = weight * weight;
            total += weights[slot];
            distance[joint] = Float.POSITIVE_INFINITY;
        }
        for (float weight : weights) vertices[size++] = weight / total;
    }

    private static Vector3f normalAt(Vector3f p) {
        float e = 0.003f;
        return new Vector3f(field(p.x + e, p.y, p.z) - field(p.x - e, p.y, p.z),
                field(p.x, p.y + e, p.z) - field(p.x, p.y - e, p.z),
                field(p.x, p.y, p.z + e) - field(p.x, p.y, p.z - e)).normalize();
    }

    private static float field(float x, float y, float z) {
        float distance = FORMS[0].distance(x, y, z);
        for (int i = 1; i < FORMS.length; i++) {
            float next = FORMS[i].distance(x, y, z);
            float overlap = Math.max(.089f - Math.abs(distance - next), 0) / .089f;
            distance = Math.min(distance, next) - overlap * overlap * .02225f;
        }
        return distance + .002f * (float) Math.sin(x * 32 + y * 19) * (float) Math.cos(z * 29 - y * 13);
    }

    private record Sample(Vector3f point, float distance) { }

    private record Form(float x, float y, float z, float rx, float ry, float rz, float lean, int bone) {
        float distance(float px, float py, float pz) {
            float dy = py - y;
            float sx = (px - x - dy * lean) / rx;
            float sy = dy / ry;
            float sz = (pz - z) / rz;
            return ((float) Math.sqrt(sx * sx + sy * sy + sz * sz) - 1) * Math.min(rx, Math.min(ry, rz));
        }
    }
}
