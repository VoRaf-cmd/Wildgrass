package javaengine;

import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

final class ObjModel implements AutoCloseable {
    static final int FLOATS_PER_VERTEX = 10;
    @FunctionalInterface
    interface MaterialSource {
        BufferedReader open(String name) throws IOException;
    }

    private final int vao;
    private final int buffer;
    private final int vertexCount;

    ObjModel(float[] vertices) {
        vertexCount = vertices.length / FLOATS_PER_VERTEX;
        vao = glGenVertexArrays();
        buffer = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        for (int attribute = 0; attribute < 3; attribute++) {
            glVertexAttribPointer(attribute, 3, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES,
                    (long) attribute * 3 * Float.BYTES);
            glEnableVertexAttribArray(attribute);
        }
        glVertexAttribPointer(3, 1, GL_FLOAT, false, FLOATS_PER_VERTEX * Float.BYTES, 9L * Float.BYTES);
        glEnableVertexAttribArray(3);
        glBindVertexArray(0);
    }

    static ObjModel read(BufferedReader reader, MaterialSource materials) throws IOException {
        var positions = new ArrayList<Vector3f>();
        var normals = new ArrayList<Vector3f>();
        var colors = new HashMap<String, Vector3f>();
        var defaultColor = new Vector3f(0.65f);
        Vector3f color = defaultColor;
        var vertices = new ArrayList<Float>();
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            String[] fields = fields(line);
            try {
                switch (fields[0]) {
                    case "v" -> positions.add(vector(fields));
                    case "vn" -> normals.add(vector(fields));
                    case "mtllib" -> {
                        String name = String.join(" ", Arrays.copyOfRange(fields, 1, fields.length));
                        try (BufferedReader material = materials.open(name)) {
                            readMaterials(material, colors);
                        }
                    }
                    case "usemtl" -> color = colors.getOrDefault(fields[1], defaultColor);
                    case "f" -> {
                        if (fields.length < 4) {
                            throw new IllegalArgumentException("A face precisa de pelo menos 3 vértices");
                        }
                        for (int i = 2; i < fields.length - 1; i++) {
                            triangle(fields[1], fields[i], fields[i + 1], positions, normals, color, vertices);
                        }
                    }
                    default -> { }
                }
            } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                throw new IOException("Linha " + lineNumber + " do OBJ: " + line, e);
            }
        }
        if (vertices.isEmpty()) {
            throw new IOException("O OBJ não contém faces renderizáveis");
        }
        float[] data = new float[vertices.size()];
        for (int i = 0; i < data.length; i++) {
            data[i] = vertices.get(i);
        }
        return new ObjModel(data);
    }

    private static void triangle(String first, String second, String third,
                                     List<Vector3f> positions, List<Vector3f> normals,
                                     Vector3f color, List<Float> vertices) {
        String[][] corners = {first.split("/", -1), second.split("/", -1), third.split("/", -1)};
        Vector3f a = resolve(positions, corners[0][0]);
        Vector3f b = resolve(positions, corners[1][0]);
        Vector3f c = resolve(positions, corners[2][0]);
        Vector3f faceNormal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if (faceNormal.lengthSquared() == 0.0f) {
            return;
        }
        faceNormal.normalize();
        for (String[] corner : corners) {
            append(vertices, resolve(positions, corner[0]));
            append(vertices, corner.length > 2 && !corner[2].isEmpty()
                    ? resolve(normals, corner[2]) : faceNormal);
            append(vertices, color);
            vertices.add(0.0f);
        }
    }

    private static Vector3f resolve(List<Vector3f> values, String indexText) {
        int index = Integer.parseInt(indexText);
        return values.get(index < 0 ? values.size() + index : index - 1);
    }

    private static void readMaterials(BufferedReader reader, Map<String, Vector3f> colors) throws IOException {
        String name = null;
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = fields(line);
            if (fields[0].equals("newmtl")) {
                name = fields[1];
            } else if (fields[0].equals("Kd") && name != null) {
                colors.put(name, vector(fields));
            }
        }
    }

    private static String[] fields(String line) {
        int comment = line.indexOf('#');
        String data = comment < 0 ? line : line.substring(0, comment);
        return data.strip().split("\\s+");
    }

    private static Vector3f vector(String[] fields) {
        return new Vector3f(Float.parseFloat(fields[1]), Float.parseFloat(fields[2]),
                Float.parseFloat(fields[3]));
    }

    private static void append(List<Float> vertices, Vector3f value) {
        vertices.add(value.x);
        vertices.add(value.y);
        vertices.add(value.z);
    }

    void draw() {
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);
    }

    @Override
    public void close() {
        glDeleteBuffers(buffer);
        glDeleteVertexArrays(vao);
    }
}
