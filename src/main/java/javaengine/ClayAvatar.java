package javaengine;

import org.joml.Matrix4f;

import java.nio.FloatBuffer;

final class ClayAvatar implements AutoCloseable {
    private final ObjModel skin = new ClayMesh().build();
    private final ObjModel whites = sphere(0xFFF1D3);
    private final ObjModel iris = sphere(0xAA713C);
    private final ObjModel pupil = sphere(0x22191D);
    private final ObjModel glint = sphere(0xFFFAEE);
    private final ObjModel mouth = sphere(0x563039);
    private final Matrix4f[] bones = new Matrix4f[10];
    private final float[] palette = new float[160];
    private final Matrix4f face = new Matrix4f();
    private final Matrix4f feature = new Matrix4f();

    ClayAvatar() {
        for (int i = 0; i < bones.length; i++) bones[i] = new Matrix4f();
    }

    void draw(SceneShader shader, Matrix4f body, float pitch, float step, float sway, float swing,
              float rightArm, float leftArm, float slash, float alarm, float time,
              boolean aiming, boolean guest, FloatBuffer buffer) {
        float squash = 0.022f * sway - 0.028f * swing + 0.042f * alarm;
        bones[0].scaling(1 - squash * .5f, 1 + squash, 1 - squash * .5f);
        joint(1, 0, 0, 1.47f, 0, pitch * 0.76f - alarm * .18f, 0, sway * .048f);
        joint(2, 0, -.267f, 1.34f, 0, leftArm, -.08f * swing, .09f * alarm);
        joint(3, 2, -.355f, 1.09f, 0, aiming ? .20f : .13f + .08f * step, 0, 0);
        joint(4, 0, .267f, 1.34f, 0, rightArm, slash, -.10f * alarm);
        joint(5, 4, .355f, 1.09f, 0, aiming ? .09f : .16f + swing * .20f, 0, 0);
        joint(6, 0, -.14f, .83f, 0, -step * .58f, 0, 0);
        joint(7, 6, -.153f, .47f, 0, .60f * Math.max(0, step), 0, 0);
        joint(8, 0, .14f, .83f, 0, step * .58f, 0, 0);
        joint(9, 8, .153f, .47f, 0, .60f * Math.max(0, -step), 0, 0);
        for (int i = 0; i < bones.length; i++) bones[i].get(palette, i * 16);
        shader.drawClay(skin, body, palette, guest ? 0xD27B50 : 0x9BA65A, buffer);
        face.set(body).mul(bones[1]);
        drawFace(shader, pitch, aiming, swing, alarm, time + (guest ? 1.7f : 0), buffer);
    }

    void grip(Matrix4f body, Matrix4f destination) {
        destination.set(body).mul(bones[5]).translate(.405f, .822f, -.062f);
    }

    private void joint(int index, int parent, float x, float y, float z, float pitch, float yaw, float roll) {
        bones[index].set(bones[parent]).translate(x, y, z).rotateY(yaw).rotateZ(roll).rotateX(pitch)
                .translate(-x, -y, -z);
    }

    private void drawFace(SceneShader shader, float pitch, boolean aiming, float swing,
                          float alarm, float time, FloatBuffer buffer) {
        float dartClock = time * 6.0f;
        float dartPhase = (float) (dartClock - Math.floor(dartClock));
        float dart = (float) Math.sin(Math.floor(dartClock) * 2.7) * (1 - smooth(.12f, .56f, dartPhase));
        float mouthHeight = .017f + .053f * alarm + .016f * swing;
        feature.set(face).translate(.016f, 1.526f, -.228f).rotateZ(-.10f).scale(.054f, mouthHeight, .016f);
        shader.draw(mouth, feature, false, buffer);
        for (int side = -1; side <= 1; side += 2) {
            float cycle = (time + side * .041f) % 3.1f;
            float blink = 1 - smooth(.015f, .14f, Math.abs(cycle - 2.35f));
            float doubleBlink = (float) Math.sin(Math.floor((time + side * .041f) / 3.1f) * 2.3);
            if (doubleBlink > .45f) blink = Math.max(blink, 1 - smooth(.01f, .095f, Math.abs(cycle - 2.64f)));
            float squint = aiming ? (side < 0 ? .32f : .11f) : swing * (side < 0 ? .41f : .15f);
            float opening = Math.clamp(1 + .30f * alarm - blink * .94f - squint, .06f, 1.30f);
            float centerX = side < 0 ? -.112f : .109f;
            float centerY = side < 0 ? 1.66f : 1.65f;
            float centerZ = -.229f - alarm * .024f;
            float size = side < 0 ? .091f : .079f;
            float jitter = (float) Math.sin(time * (side < 0 ? 41 : 49)) * alarm * .013f;
            if (opening < .18f) {
                feature.set(face).translate(centerX, centerY, -.238f).rotateZ(side * .11f)
                        .scale(size * .67f, .007f, .032f);
                shader.draw(mouth, feature, false, buffer);
                continue;
            }
            centerZ += blink * .033f;
            feature.set(face).translate(centerX, centerY, centerZ).scale(size, size * opening, .069f);
            shader.draw(whites, feature, false, buffer);

            float pupilX = centerX + (aiming ? -side * .021f : dart * (side < 0 ? .027f : -.019f))
                    + jitter + side * .017f * alarm;
            float pupilY = centerY + Math.clamp(-pitch * .018f, -.018f, .018f) + jitter;
            float pupilZ = centerZ - .061f;
            feature.set(face).translate(pupilX, pupilY, pupilZ).scale(.039f, .044f * opening, .017f);
            shader.draw(iris, feature, false, buffer);
            float pupilSize = 1 - alarm * .37f;
            feature.set(face).translate(pupilX, pupilY, pupilZ - .013f)
                    .scale(.026f * pupilSize, .032f * opening * pupilSize, .009f);
            shader.draw(pupil, feature, false, buffer);
            feature.set(face).translate(pupilX - .010f, pupilY + .014f * opening, pupilZ - .021f)
                    .scale(.009f, .009f * opening, .006f);
            shader.drawEmissive(glint, feature, buffer);
        }
    }

    private static float smooth(float from, float to, float value) {
        float t = Math.clamp((value - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static ObjModel sphere(int color) {
        final int rows = 9, sides = 16;
        var mesh = new PlantMesh(rows * sides * 6);
        for (int row = 0; row < rows; row++) for (int side = 0; side < sides; side++) {
            if (row > 0) {
                sphereVertex(mesh, row, side, rows, sides, color);
                sphereVertex(mesh, row, side + 1, rows, sides, color);
                sphereVertex(mesh, row + 1, side, rows, sides, color);
            }
            if (row < rows - 1) {
                sphereVertex(mesh, row, side + 1, rows, sides, color);
                sphereVertex(mesh, row + 1, side + 1, rows, sides, color);
                sphereVertex(mesh, row + 1, side, rows, sides, color);
            }
        }
        return mesh.upload();
    }

    private static void sphereVertex(PlantMesh mesh, int row, int side, int rows, int sides, int color) {
        double height = Math.PI * row / rows;
        double longitude = Math.PI * 2 * side / sides;
        float x = (float) (Math.sin(height) * Math.cos(longitude));
        float y = (float) Math.cos(height);
        float z = (float) (Math.sin(height) * Math.sin(longitude));
        mesh.vertex(x, y, z, x, y, z, color, 0);
    }

    @Override public void close() {
        skin.close();
        whites.close();
        iris.close();
        pupil.close();
        glint.close();
        mouth.close();
    }
}
