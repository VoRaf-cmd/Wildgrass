package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;

final class Bow {
    static final float DRAW_TIME = 0.81f;
    private static final float AIM_FOV = (float) Math.toRadians(40.0);
    static final float RECOVERY_TIME = 0.29f;
    private final Matrix4f body = new Matrix4f();
    private final Matrix4f piece = new Matrix4f();
    private final Vector3f lower = new Vector3f();
    private final Vector3f upper = new Vector3f();
    private final Vector3f nock = new Vector3f();
    private final Vector3f span = new Vector3f();
    private boolean drawing;
    private boolean released;
    private float charge;
    private float recovery;
    private float aimProgress;
    private float shotPower;
    private float releasedDraw;

    boolean press(Inventory inventory) {
        if (!drawing && recovery == 0.0f && inventory.total(Inventory.Item.ARROW) > 0) {
            drawing = true;
            return true;
        }
        return false;
    }

    void release() {
        if (drawing) released = true;
    }

    void cancel() {
        drawing = false;
        released = false;
        charge = 0.0f;
    }

    float update(float delta, Inventory inventory, Vector3f eye, Vector3f aim, Projectiles shots,
                SoundEffect ready, SoundEffect fire) {
        float launchedSpeed = 0.0f;
        recovery = Math.max(0.0f, recovery - delta);
        if (released) {
            if (inventory.take(Inventory.Item.ARROW)) {
                releasedDraw = drawAmount(charge);
                launchedSpeed = speed();
                shots.launch(Inventory.Item.ARROW, eye, aim, launchedSpeed);
                shotPower = 0.36f + 0.64f * charge;
                recovery = RECOVERY_TIME;
                fire.volume(-6.0f * (1.0f - charge));
                fire.play();
            }
            cancel();
        } else if (drawing && charge < 1.0f) {
            charge = Math.min(1.0f, charge + delta / DRAW_TIME);
            if (charge == 1.0f) ready.play();
        }
        return launchedSpeed;
    }

    boolean drawing() { return drawing; }
    float charge() { return charge; }
    float speed() { return 8.0f + 22.0f * charge; }

    void updateAim(boolean held, float delta) {
        aimProgress = Math.clamp(aimProgress + delta * (held ? 5.7f : -6.7f), 0.0f, 1.0f);
    }

    void resetAim() {
        aimProgress = 0.0f;
    }

    float aimAmount() {
        return smooth(aimProgress);
    }

    float fieldOfView(float shake) {
        return org.joml.Math.lerp(Sky.FOV, AIM_FOV, aimAmount()) + kick() * shake * 0.026f;
    }

    void reactView(Matrix4f view, float shake) {
        float kick = kick() * shake;
        view.rotateLocalX(kick * 0.030f).rotateLocalZ(kick * -0.012f)
                .translateLocal(0.006f * kick, -0.013f * kick, 0.0f);
    }

    private float kick() {
        if (recovery <= 0.0f) return 0.0f;
        float progress = 1.0f - recovery / RECOVERY_TIME;
        return shotPower * (float) Math.sin(progress * 8.0f + 0.9f) * (1.0f - progress) * (1.0f - progress);
    }

    static float drawAmount(float charge) {
        return smooth(Math.clamp(charge, 0, 1));
    }

    static float stringFlex(float releasedDraw, float elapsed) {
        if (elapsed < .035f) return releasedDraw * (1 - smooth(elapsed / .035f));
        float ring = elapsed - .035f;
        return -.07f * releasedDraw * (float) (Math.sin(ring * 90) * Math.exp(-ring * 29));
    }

    static float nockDepth(float flex) { return .157f + flex * .235f; }

    private static float smooth(float t) { return t * t * (3.0f - 2.0f * t); }

    void draw(SceneShader shader, ObjModel bow, ObjModel string, ObjModel arrow, float aspect, FloatBuffer buffer) {
        float draw = recovery > 0 ? stringFlex(releasedDraw, RECOVERY_TIME - recovery) : drawAmount(charge);
        float aim = aimAmount();
        float kick = kick();
        float tension = drawing ? (float) Math.sin(charge * 23.0f) * draw * 0.003f : 0.0f;
        float raise = drawing ? AvatarMotion.smooth(0, .28f, charge) : 0;
        float follow = AvatarMotion.smooth(0, RECOVERY_TIME, recovery);
        raise = Math.max(raise, follow);
        float breath = (float) Math.sin(org.lwjgl.glfw.GLFW.glfwGetTime() * 2.3) * .003f * (1 - .85f * Math.max(aim, raise));
        body.translation((0.177f - 0.059f * aim - 0.021f * draw) * aspect,
                        -0.30f + aim * 0.16f + raise * .064f + breath + kick * .029f,
                        -0.76f + aim * 0.042f - kick * 0.055f)
                .rotateY(0.19f - aim * 0.07f + tension)
                .rotateX(-kick * .045f)
                .rotateZ(.07f * (1 - Math.max(aim, raise)) - kick * .036f)
                .scale(0.74f * Math.min(1.0f, aspect))
                .scale(1.0f, -1.0f, 1.0f);
        shader.drawBow(bow, body, draw, buffer);
        float limb = 0.415f * (1.0f - draw * 0.046f);
        float tipDepth = 0.157f + draw * 0.065f;
        body.transformPosition(0.0f, -limb, tipDepth, lower);
        body.transformPosition(0.0f, limb, tipDepth, upper);
        body.transformPosition(0.0f, 0.0f, nockDepth(draw), nock);
        string(shader, string, lower, nock, buffer);
        string(shader, string, nock, upper, buffer);
        if (drawing) {
            body.transformPosition(0.023f, 0.008f, -0.015f, upper);
            span.set(nock).sub(upper).normalize();
            piece.translation(nock).rotateTowards(span.x, span.y, span.z, 0.0f, 1.0f, 0.0f).scale(0.74f);
            shader.draw(arrow, piece, false, buffer);
        }
    }

    private void string(SceneShader shader, ObjModel mesh, Vector3f start, Vector3f end, FloatBuffer buffer) {
        span.set(end).sub(start);
        float length = span.length();
        span.div(length);
        piece.translation(start).rotateTowards(span.x, span.y, span.z, 1.0f, 0.0f, 0.0f)
                .scale(0.006f, 0.006f, length);
        shader.drawEmissive(mesh, piece, buffer);
    }
}
