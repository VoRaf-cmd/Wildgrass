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
    private final ClayAvatar avatar = new ClayAvatar();
    private boolean visible;
    private NetworkPacket.Pose pose;
    private float yaw;
    private float pitch;
    private float stride;
    private float movement;
    private float actionTime;
    private float time;
    private float alarm;
    private float crouch;
    private Inventory.Item actionItem;

    void receive(NetworkPacket.Pose pose) {
        this.pose = pose;
        target.set(pose.x(), pose.y(), pose.z());
        if (!visible) {
            displayed.set(target);
            yaw = pose.yaw();
            pitch = pose.pitch();
            crouch = crouchTarget();
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
        alarm = 0.0f;
    }

    void reactToBlast(Vector3f location) {
        if (visible) alarm = Math.max(alarm, Math.clamp((10 - displayed.distance(location)) / 7, 0, 1));
    }

    void update(float delta) {
        actionTime = Math.max(0, actionTime - delta);
        alarm = Math.max(0, alarm - delta * 1.26f);
        if (!visible) return;
        time += delta;
        float distance = (float) Math.hypot(target.x - displayed.x, target.z - displayed.z);
        float blend = 1.0f - (float) Math.exp(-delta * 15.0f);
        crouch += (crouchTarget() - crouch) * blend;
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

    private float crouchTarget() {
        return (pose.flags() & NetworkPacket.CROUCHING) != 0 ? 1.0f : 0.0f;
    }

    void draw(SceneShader shader, ObjModel[] items, ObjModel bowString, ObjModel lighterLid,
              FireVisuals fire, boolean guest, FloatBuffer buffer) {
        if (!visible) return;
        float step = (float) Math.sin(stride) * movement;
        float bob = Math.abs(step) * 0.034f * (1 - crouch * .82f);
        body.translation(displayed.x, Terrain.height(displayed.x, displayed.z) + bob, displayed.z)
                .rotateY(-yaw).scale(.92f, 1, .97f);
        int item = pose.item();
        Inventory.Item held = item >= 0 ? NetworkPacket.ITEMS[item] : null;
        boolean aiming = (pose.flags() & NetworkPacket.AIMING) != 0;
        float swing = actionTime > 0 ? (float) Math.sin(Math.PI * actionTime / 0.44f) : 0;
        float leftArm = aiming ? pitch + 1.02f : step * .35f;
        float rightArm = held == null ? -step * .35f + swing * .75f
                : pitch * .72f + (aiming ? 1.17f : .68f) + swing * .72f;
        float slash = actionItem == Inventory.Item.MACHETE ? swing * .82f : 0;
        avatar.draw(shader, body, pitch, step, (float) Math.sin(time * 3.1f), swing,
                rightArm, leftArm, slash, alarm, time, crouch, aiming, guest, buffer);
        if (item < 0 || items[item] == null) return;
        avatar.grip(body, part);
        part.rotateX(aiming ? -1.27f : -.84f);
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
        avatar.close();
    }
}
