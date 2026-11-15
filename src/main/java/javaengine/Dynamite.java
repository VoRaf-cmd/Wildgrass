package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.FloatBuffer;
import java.util.function.Consumer;

final class Dynamite {
    private static final float SPEED = 10.5f;
    private static final float WINDUP = 0.15f;
    private static final float FUSE = 2.05f;
    private static final float RECOVERY = 0.45f;
    private final Charge[] charges = new Charge[6];
    private final Matrix4f pose = new Matrix4f();
    private final Matrix4f sparkPose = new Matrix4f();
    private final Vector3f direction = new Vector3f();
    private final Vector3f fuseTip = new Vector3f();
    private float preparation = -1.0f;
    private float recovery;

    Dynamite() {
        for (int i = 0; i < charges.length; i++) charges[i] = new Charge();
    }

    void start() {
        if (preparation < 0.0f && recovery == 0.0f) preparation = 0.0f;
    }

    void cancel() {
        preparation = -1.0f;
    }

    private Vector3f throwDirection(Vector3f aim) {
        return direction.set(aim).add(0.0f, 0.17f, 0.0f).normalize();
    }

    boolean updateThrow(float delta, Inventory inventory, Vector3f eye, Vector3f aim,
                     SoundEffect toss, SoundEffect fuse) {
        recovery = Math.max(0.0f, recovery - delta);
        if (preparation < 0.0f) return false;
        preparation += delta;
        if (preparation < WINDUP) return false;
        preparation = -1.0f;
        for (Charge charge : charges) {
            if (!charge.active && inventory.takeSelected(Inventory.Item.DYNAMITE)) {
                launch(charge, eye, aim, false);
                recovery = RECOVERY;
                toss.play();
                fuse.play();
                return true;
            }
        }
        return false;
    }

    void remoteThrow(Vector3f eye, Vector3f aim) {
        for (Charge charge : charges) {
            if (!charge.active) {
                launch(charge, eye, aim, true);
                return;
            }
        }
    }

    private void launch(Charge charge, Vector3f eye, Vector3f aim, boolean remote) {
        Vector3f heading = throwDirection(aim);
        Projectiles.origin(eye, heading, charge.point);
        charge.velocity.set(heading).mul(SPEED);
        charge.active = true;
        charge.landed = false;
        charge.age = 0.0f;
        charge.remote = remote;
    }

    void update(float delta, Projectiles ballistics, Forest forest, Vector3f player, Meadow meadow,
                BlastEffects effects, ImpactMotion motion, SoundEffect boom, Consumer<Vector3f> detonation) {
        for (Charge charge : charges) {
            if (!charge.active) continue;
            float step = Math.min(delta, FUSE - charge.age);
            if (!charge.landed && ballistics.advance(charge.point, charge.velocity, step, forest)) {
                charge.landed = true;
            }
            charge.age += step;
            if (charge.age >= FUSE) {
                charge.active = false;
                if (!charge.remote) {
                    effects.explode(charge.point, player, forest, meadow, motion, boom);
                    detonation.accept(charge.point);
                }
            }
        }
    }

    void preview(SceneShader shader, ObjModel marker, Forest forest, Projectiles ballistics, Vector3f eye,
                 Vector3f aim, FloatBuffer buffer) {
        ballistics.preview(shader, marker, forest, eye, throwDirection(aim), SPEED, buffer);
    }

    void drawHeld(SceneShader shader, ObjModel mesh, ObjModel spark, float aspect, FloatBuffer buffer) {
        float back = preparation < 0.0f ? 0.0f : (float) Math.sin(preparation / WINDUP * Math.PI * 0.86f);
        float returnFromThrow = recovery / RECOVERY;
        pose.translation((0.155f + back * 0.06f) * aspect,
                        -0.39f + back * 0.145f - returnFromThrow * 0.31f, -0.61f + 0.13f * back)
                .rotateY(-0.27f - 0.32f * back).rotateZ(0.20f - 0.85f * back);
        shader.draw(mesh, pose, false, buffer);
        if (preparation >= 0.045f) spark(shader, spark, preparation * 2.1f, buffer);
    }

    void drawThrown(SceneShader shader, ObjModel mesh, ObjModel spark, FloatBuffer buffer) {
        for (Charge charge : charges) {
            if (!charge.active) continue;
            pose.translation(charge.point.x, charge.point.y + (charge.landed ? 0.07f : 0.03f), charge.point.z)
                    .rotateY(charge.landed ? 0.73f : charge.age * 5.0f)
                    .rotateZ(charge.landed ? 1.47f : charge.age * 8.8f)
                    .translate(0.0f, -0.145f, 0.0f).scale(0.78f);
            shader.draw(mesh, pose, false, buffer);
            spark(shader, spark, charge.age, buffer);
        }
    }

    private void spark(SceneShader shader, ObjModel mesh, float time, FloatBuffer buffer) {
        pose.transformPosition(0.045f, 0.40f, 0.014f, fuseTip);
        float size = 0.021f + 0.007f * (float) Math.cos(time * 47.0f);
        sparkPose.translation(fuseTip).scale(size);
        shader.drawEmissive(mesh, sparkPose, buffer);
        sparkPose.translation(fuseTip.x + (float) Math.sin(time * 39.0f) * 0.032f,
                fuseTip.y + 0.035f, fuseTip.z + (float) Math.cos(time * 37.0f) * 0.029f)
                .scale(0.009f);
        shader.drawEmissive(mesh, sparkPose, buffer);
    }

    private static final class Charge {
        final Vector3f point = new Vector3f();
        final Vector3f velocity = new Vector3f();
        float age;
        boolean active, landed, remote;
    }
}
