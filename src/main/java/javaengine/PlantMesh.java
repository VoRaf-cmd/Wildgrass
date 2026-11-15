package javaengine;

import java.util.Arrays;

final class PlantMesh {
    private float[] vertices;
    private int size;

    PlantMesh(int estimatedVertices) {
        vertices = new float[estimatedVertices * ObjModel.FLOATS_PER_VERTEX];
    }

    void vertex(float x, float y, float z, float nx, float ny, float nz, int color, float growth) {
        if (size + ObjModel.FLOATS_PER_VERTEX > vertices.length) {
            vertices = Arrays.copyOf(vertices, vertices.length * 2);
        }
        vertices[size++] = x;
        vertices[size++] = y;
        vertices[size++] = z;
        vertices[size++] = nx;
        vertices[size++] = ny;
        vertices[size++] = nz;
        vertices[size++] = ((color >> 16) & 255) / 255.0f;
        vertices[size++] = ((color >> 8) & 255) / 255.0f;
        vertices[size++] = (color & 255) / 255.0f;
        vertices[size++] = growth;
    }

    ObjModel upload() {
        return new ObjModel(Arrays.copyOf(vertices, size));
    }
}
