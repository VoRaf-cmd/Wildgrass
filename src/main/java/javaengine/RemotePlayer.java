package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
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
    private final ClayAvatar avatar;
    private boolean visible;
    private NetworkPacket.Pose pose;
    private float yaw;
    private float pitch;
    private final AvatarMotion motion = new AvatarMotion();
    private float movement;
    private float actionTime;
    private float time;
    private float alarm;
    private float crouch;
    private Inventory.Item actionItem;
    private float bowCharge;
    private float localBowCharge;
    private float bowRecovery;
    private float releasedDraw;
    private float bowReady;
    private float drawHandWeight;
    private boolean bowDrawing;

    RemotePlayer() throws IOException {
        avatar = new ClayAvatar();
    }

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

    void updateLocal(NetworkPacket.Pose localPose, float delta) {
        receive(localPose);
        update(delta, true);
        // Local movement needs no network interpolation or visual trailing.
        displayed.set(target);
        yaw = localPose.yaw();
        pitch = localPose.pitch();
    }

    void localBowCharge(float charge) { localBowCharge = charge; }

    void releaseBow(float speed) {
        releasedDraw = Bow.drawAmount((speed - 8) / 22);
        bowRecovery = Bow.RECOVERY_TIME;
        bowDrawing = false;
        bowCharge = 0;
    }

    void use(Inventory.Item item) {
        actionItem = item;
        actionTime = 0.60f;
    }

    void hide() {
        visible = false;
        motion.reset();
        movement = 0.0f;
        actionTime = 0.0f;
        alarm = 0.0f;
        bowCharge = bowReady = bowRecovery = drawHandWeight = 0;
        bowDrawing = false;
    }

    void reactToBlast(Vector3f location) {
        if (visible) alarm = Math.max(alarm, Math.clamp((10 - displayed.distance(location)) / 7, 0, 1));
    }

    void update(float delta) { update(delta, false); }

    private void update(float delta, boolean local) {
        if (delta <= 0) return;
        actionTime = Math.max(0, actionTime - delta);
        alarm = Math.max(0, alarm - delta * 1.26f);
        if (!visible) return;
        time += delta;
        bowRecovery = Math.max(0, bowRecovery - delta);
        boolean hasBow = pose.item() == Inventory.Item.BOW.ordinal();
        bowDrawing = hasBow && bowRecovery == 0 && (pose.flags() & NetworkPacket.DRAWING) != 0;
        if (local) bowCharge = bowDrawing ? localBowCharge : 0;
        else if (bowDrawing) bowCharge = Math.min(1, bowCharge + delta / Bow.DRAW_TIME);
        else bowCharge = Math.max(0, bowCharge - delta * 7);
        if (!hasBow) bowRecovery = 0;
        boolean reaching = bowDrawing || bowRecovery > .15f;
        drawHandWeight = Math.clamp(drawHandWeight + delta * (reaching ? 8 : -7), 0, 1);
        boolean raised = hasBow && (bowDrawing || bowRecovery > .11f || (pose.flags() & NetworkPacket.AIMING) != 0);
        bowReady = Math.clamp(bowReady + delta * (raised ? 5 : -4), 0, 1);
        float distance = (float) Math.hypot(target.x - displayed.x, target.z - displayed.z);
        float blend = 1.0f - (float) Math.exp(-delta * 15.0f);
        crouch += (crouchTarget() - crouch) * blend;
        float yawDelta = (float) Math.IEEEremainder(pose.yaw() - yaw, Math.PI * 2) * blend;
        yaw += yawDelta;
        pitch += (pose.pitch() - pitch) * blend;
        if (distance > 3.0f) {
            displayed.set(target);
            movement = 0;
            motion.reset();
            return;
        }
        float positionBlend = local ? 1 : blend;
        float dx = (target.x - displayed.x) * positionBlend;
        float dz = (target.z - displayed.z) * positionBlend;
        displayed.lerp(target, positionBlend);
        motion.update(delta, dx, dz, yaw, yawDelta,
                (pose.flags() & NetworkPacket.AIMING) != 0, (pose.flags() & NetworkPacket.DRAWING) != 0);
        movement = AvatarMotion.smooth(.04f, 1.5f, motion.speed);
    }

    private float crouchTarget() {
        return (pose.flags() & NetworkPacket.CROUCHING) != 0 ? 1.0f : 0.0f;
    }

    void draw(SceneShader shader, ObjModel[] items, ObjModel bowString, ObjModel lighterLid,
              FireVisuals fire, boolean guest, FloatBuffer buffer) {
        if (!visible) return;
        float step = (float) Math.sin(motion.phase) * movement;
        float bob = (1 - (float) Math.cos(motion.phase * 2)) * .012f * movement
                * (1 + motion.run) * (1 - crouch * .82f);
        body.translation(displayed.x, Terrain.height(displayed.x, displayed.z) + bob, displayed.z)
                .rotateY(-yaw).scale(.92f, 1, .97f);
        int item = pose.item();
        Inventory.Item held = item >= 0 ? NetworkPacket.ITEMS[item] : null;
        boolean aiming = (pose.flags() & NetworkPacket.AIMING) != 0;
        float swing = actionTime > 0 ? AvatarMotion.attack(1 - actionTime / .60f) : 0;
        float leftArm = step * (.35f + motion.run * .24f) * (1 - motion.aim)
                + (pitch + 1.02f) * motion.aim;
        float rightArm = held == null ? -step * .35f + swing * .75f
                : pitch * .72f + (.68f + motion.aim * .49f) + swing * .72f;
        float slash = actionItem == Inventory.Item.MACHETE ? swing * .82f : 0;
        float bowElapsed = Bow.RECOVERY_TIME - bowRecovery;
        float bowFlex = bowRecovery > 0 ? Bow.stringFlex(releasedDraw, bowElapsed) : Bow.drawAmount(bowCharge);
        float pull = bowRecovery > 0 ? releasedDraw : Bow.drawAmount(bowCharge);
        float follow = bowRecovery > 0 ? AvatarMotion.smooth(0, .08f, bowElapsed)
                * AvatarMotion.smooth(0, .19f, bowRecovery) : 0;
        avatar.draw(shader, body, pitch, step, (float) Math.sin(time * 3.1f), swing,
                rightArm, leftArm, slash, alarm, time, crouch, aiming, guest, motion,
                held == Inventory.Item.BOW ? part : null, bowReady, pull, follow, AvatarMotion.smooth(0, 1, drawHandWeight), buffer);
        if (item < 0 || items[item] == null) return;
        if (held == Inventory.Item.BOW) {
            drawBow(shader, items[item], bowString, items[Inventory.Item.ARROW.ordinal()], bowFlex, buffer);
            return;
        }
        avatar.grip(body, part);
        part.rotateX(-.84f - motion.aim * .43f);
        switch (held) {
            case AXE -> part.rotateY(-1.0f).scale(0.43f).translate(-0.10f, -0.57f, 0);
            case MACHETE -> part.scale(0.49f).translate(0, -0.16f, 0);
            case STONE -> part.scale(0.09f);
            case CAMPFIRE -> part.scale(0.36f);
            case FIBER -> part.scale(0.19f);
            default -> part.scale(0.76f);
        }
        boolean lit = (pose.flags() & NetworkPacket.LIT) != 0;
        shader.draw(items[item], part, false, buffer);
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

    private void drawBow(SceneShader shader, ObjModel bow, ObjModel string, ObjModel arrow, float draw, FloatBuffer buffer) {
        shader.drawBow(bow, part, draw, buffer);
        float nock = Bow.nockDepth(draw);
        for (int side = -1; side <= 1; side += 2) {
            part.transformPosition(0, side * 0.415f * (1 - draw * 0.046f), 0.157f + draw * 0.065f, stringStart);
            part.transformPosition(0, 0, nock, stringEnd);
            span.set(stringEnd).sub(stringStart);
            float length = span.length();
            accessory.translation(stringStart).rotateTowards(span.div(length), flamePoint.set(1, 0, 0))
                    .scale(0.006f, 0.006f, length);
            shader.drawEmissive(string, accessory, buffer);
        }
        if (bowDrawing) {
            part.transformPosition(.023f, .008f, -.015f, stringStart);
            span.set(stringEnd).sub(stringStart).normalize();
            accessory.translation(stringEnd).rotateTowards(span, flamePoint.set(0, 1, 0)).scale(.95f);
            shader.draw(arrow, accessory, false, buffer);
        }
    }

    @Override public void close() {
        avatar.close();
    }
}
