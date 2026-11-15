package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

final class RemotePlayer implements AutoCloseable {
    private final Vector3f displayed = new Vector3f();
    private final Vector3f target = new Vector3f();
    private final Vector3f flamePoint = new Vector3f();
    private final Vector3f stringStart = new Vector3f();
    private final Vector3f stringEnd = new Vector3f();
    private final Vector3f span = new Vector3f();
    private final Matrix4f body = new Matrix4f();
    private final Matrix4f part = new Matrix4f();
    private final Matrix4f accessory = new Matrix4f();
    private final Avatar[] variants = {
            new Avatar(0x8F9438, 0x383029, 0xB58F53, 0x302923),
            new Avatar(0xAB633F, 0x2E4444, 0xC89F70, 0x482D26)
    };
    private boolean visible;
    private NetworkPacket.Pose pose;
    private float yaw;
    private float pitch;
    private float stride;
    private float movement;
    private float actionTime;
    private float time;
    private Inventory.Item actionItem;

    void receive(NetworkPacket.Pose pose) {
        this.pose = pose;
        target.set(pose.x(), pose.y(), pose.z());
        if (!visible) {
            displayed.set(target);
            yaw = pose.yaw();
            pitch = pose.pitch();
        }
        visible = true;
    }

    void use(Inventory.Item item) {
        actionItem = item;
        actionTime = 0.44f;
    }

    void hide() {
        visible = false;
        movement = 0.0f;
        actionTime = 0.0f;
    }

    void update(float delta) {
        actionTime = Math.max(0, actionTime - delta);
        if (!visible) return;
        time += delta;
        float distance = (float) Math.hypot(target.x - displayed.x, target.z - displayed.z);
        float blend = 1.0f - (float) Math.exp(-delta * 15.0f);
        yaw += (float) Math.IEEEremainder(pose.yaw() - yaw, Math.PI * 2) * blend;
        pitch += (pose.pitch() - pitch) * blend;
        if (distance > 3.0f) {
            displayed.set(target);
            movement = 0;
            return;
        }
        displayed.lerp(target, blend);
        movement += (Math.min(1.0f, distance * 6.0f) - movement) * blend;
        stride = (stride + distance * blend * 8.0f) % 6.2831855f;
    }

    void draw(SceneShader shader, ObjModel[] items, ObjModel bowString, ObjModel lighterLid,
              FireVisuals fire, boolean guest, FloatBuffer buffer) {
        if (!visible) return;
        Avatar avatar = variants[guest ? 1 : 0];
        float step = (float) Math.sin(stride) * movement;
        float bob = Math.abs(step) * 0.034f;
        body.translation(displayed.x, displayed.y - 1.53f + bob, displayed.z).rotateY(-yaw);
        shader.draw(avatar.torso, body, false, buffer);
        part.set(body).translate(0, 1.51f, 0).rotateX(pitch);
        shader.draw(avatar.head, part, false, buffer);
        for (int side = -1; side <= 1; side += 2) {
            part.set(body).translate(side * 0.12f, 0.77f, 0).rotateX(step * side * 0.55f);
            shader.draw(avatar.leg, part, false, buffer);
        }
        int item = pose.item();
        Inventory.Item held = item >= 0 ? NetworkPacket.ITEMS[item] : null;
        boolean aiming = (pose.flags() & NetworkPacket.AIMING) != 0;
        float swing = actionTime > 0 ? (float) Math.sin(Math.PI * actionTime / 0.44f) : 0;
        part.set(body).translate(-0.28f, 1.32f, 0).rotateZ(-0.12f).rotateX(aiming ? pitch + 1.36f : step * 0.45f);
        shader.draw(avatar.arm, part, false, buffer);
        part.set(body).translate(0.28f, 1.32f, 0).rotateZ(0.13f)
                .rotateY(actionItem == Inventory.Item.MACHETE ? swing * 1.25f : 0)
                .rotateX(held == null ? -step * 0.45f : pitch + (aiming ? 1.47f : 0.88f) + swing * 0.95f);
        shader.draw(avatar.arm, part, false, buffer);
        if (item < 0 || items[item] == null) return;
        part.translate(0, -0.52f, -0.06f)
                .rotateX(aiming ? -1.47f : -0.98f);
        switch (held) {
            case AXE -> part.rotateY(-1.0f).scale(0.43f).translate(-0.10f, -0.57f, 0);
            case MACHETE -> part.scale(0.49f).translate(0, -0.16f, 0);
            case STONE -> part.scale(0.09f);
            case CAMPFIRE -> part.scale(0.36f);
            case FIBER -> part.scale(0.19f);
            default -> part.scale(0.76f);
        }
        boolean lit = (pose.flags() & NetworkPacket.LIT) != 0;
        if (held == Inventory.Item.BOW) {
            drawBow(shader, items[item], bowString, items[Inventory.Item.ARROW.ordinal()], buffer);
        } else {
            shader.draw(items[item], part, false, buffer);
        }
        if (held == Inventory.Item.LIGHTER) {
            accessory.set(part).translate(-0.145f, 0.397f, 0).rotateZ(lit ? 2.75f : 0)
                    .translate(0.145f, -0.397f, 0);
            shader.draw(lighterLid, accessory, false, buffer);
            part.transformPosition(0.049f, 0.48f, 0.01f, flamePoint);
        } else if (held == Inventory.Item.TORCH) {
            part.transformPosition(0, 0.44f, 0, flamePoint);
        }
        if (lit && (held == Inventory.Item.LIGHTER || held == Inventory.Item.TORCH)) {
            fire.flames(shader, flamePoint, time, held == Inventory.Item.TORCH ? 0.15f : 0.08f, buffer);
        }
    }

    private void drawBow(SceneShader shader, ObjModel bow, ObjModel string, ObjModel arrow, FloatBuffer buffer) {
        boolean drawing = (pose.flags() & NetworkPacket.DRAWING) != 0;
        float draw = drawing ? 0.75f : 0;
        shader.drawBow(bow, part, draw, buffer);
        float nock = 0.157f + draw * 0.235f;
        for (int side = -1; side <= 1; side += 2) {
            part.transformPosition(0, side * 0.415f * (1 - draw * 0.046f), 0.157f + draw * 0.065f, stringStart);
            part.transformPosition(0, 0, nock, stringEnd);
            span.set(stringEnd).sub(stringStart);
            float length = span.length();
            accessory.translation(stringStart).rotateTowards(span.div(length), flamePoint.set(1, 0, 0))
                    .scale(0.006f, 0.006f, length);
            shader.drawEmissive(string, accessory, buffer);
        }
        if (drawing) shader.draw(arrow, accessory.set(part).translate(0, 0, nock), false, buffer);
    }

    @Override public void close() {
        for (Avatar variant : variants) {
            variant.head.close();
            variant.torso.close();
            variant.arm.close();
            variant.leg.close();
        }
    }

    private static final class Avatar {
        final ObjModel head, torso, arm, leg;

        Avatar(int shirt, int pants, int skin, int hair) {
            var mesh = new PlantMesh(288);
            shape(mesh, 0, 0, 0, 0.39f, 0.35f, 0.32f, skin);
            shape(mesh, 0, -0.115f, -0.006f, 0.38f, 0.17f, 0.32f, hair);
            shape(mesh, 0, 0.15f, 0.012f, 0.40f, 0.14f, 0.33f, hair);
            for (int side : new int[] {-1, 1}) {
                shape(mesh, side * 0.10f, 0.03f, -0.151f, 0.128f, 0.123f, 0.092f, 0xFFF2CD);
                shape(mesh, side * 0.10f, 0.025f, -0.194f, 0.049f, 0.057f, 0.024f, 0x28251D);
            }
            head = mesh.upload();
            mesh = new PlantMesh(144);
            shape(mesh, 0, 1.10f, 0, 0.48f, 0.65f, 0.30f, shirt);
            shape(mesh, 0, 0.84f, 0, 0.43f, 0.12f, 0.30f, pants);
            torso = mesh.upload();
            mesh = new PlantMesh(144);
            shape(mesh, 0, -0.21f, 0, 0.159f, 0.51f, 0.17f, shirt);
            shape(mesh, 0, -0.49f, -0.009f, 0.145f, 0.19f, 0.16f, skin);
            arm = mesh.upload();
            mesh = new PlantMesh(144);
            shape(mesh, 0, -0.31f, 0, 0.185f, 0.66f, 0.19f, pants);
            shape(mesh, 0, -0.685f, -0.044f, 0.205f, 0.17f, 0.29f, 0x342B23);
            leg = mesh.upload();
        }

        private static void shape(PlantMesh mesh, float x, float y, float z, float width, float height,
                                  float depth, int color) {
            Vector3f[][] rings = new Vector3f[4][8];
            for (int row = 0; row < 4; row++) {
                float level = switch (row) { case 0 -> -0.5f; case 1 -> -0.32f; case 2 -> 0.32f; default -> 0.5f; };
                float radius = row == 0 || row == 3 ? 0.74f : 1;
                for (int side = 0; side < 8; side++) {
                    double angle = (side + 0.5) * Math.PI / 4;
                    rings[row][side] = new Vector3f(x + (float) Math.cos(angle) * width * 0.54f * radius,
                            y + height * level, z + (float) Math.sin(angle) * depth * 0.54f * radius);
                }
            }
            for (int side = 0; side < 8; side++) {
                int next = (side + 1) % 8;
                for (int row = 0; row < 3; row++) {
                    triangle(mesh, rings[row][side], rings[row + 1][side], rings[row][next], color);
                    triangle(mesh, rings[row][next], rings[row + 1][side], rings[row + 1][next], color);
                }
                triangle(mesh, new Vector3f(x, y + height * 0.5f, z), rings[3][next], rings[3][side], color);
                triangle(mesh, new Vector3f(x, y - height * 0.5f, z), rings[0][side], rings[0][next], color);
            }
        }

        private static void triangle(PlantMesh mesh, Vector3f a, Vector3f b, Vector3f c, int color) {
            Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a)).normalize();
            vertex(mesh, a, normal, color);
            vertex(mesh, b, normal, color);
            vertex(mesh, c, normal, color);
        }

        private static void vertex(PlantMesh mesh, Vector3f vertex, Vector3f normal, int color) {
            mesh.vertex(vertex.x, vertex.y, vertex.z, normal.x, normal.y, normal.z, color, 0);
        }
    }
}
