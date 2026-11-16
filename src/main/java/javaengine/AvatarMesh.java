package javaengine;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;

final class AvatarMesh {
    private static final int MAGIC = 0x57415631; // WAV1
    private static final int STRIDE = ObjModel.FLOATS_PER_VERTEX + 8;

    private AvatarMesh() { }

    static ObjModel load(String name) throws IOException {
        var resource = AvatarMesh.class.getResourceAsStream("/models/" + name);
        if (resource == null) throw new IOException("Avatar ausente: " + name);
        try (var in = new DataInputStream(new BufferedInputStream(resource))) {
            if (in.readInt() != MAGIC) throw new IOException("Formato inválido para " + name);
            int count = in.readInt();
            if (count <= 0 || count > 120_000 || count % 3 != 0) {
                throw new IOException("Número de vértices inválido para " + name);
            }
            float[] vertices = new float[count * STRIDE];
            for (int i = 0; i < vertices.length; i++) vertices[i] = in.readFloat();
            if (in.read() != -1) throw new IOException("Dados extras em " + name);
            return new ObjModel(vertices, true);
        }
    }
}
