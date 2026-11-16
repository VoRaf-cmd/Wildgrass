package javaengine;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.IOException;
import java.nio.FloatBuffer;

final class ClayAvatar implements AutoCloseable {
    static final int BONE_COUNT = 13;
    static final int SPINE = 12;
    private static final float HIP = .83f, KNEE = .47f, ANKLE = .107f;
    private static final float HIP_BACK = .12f;
    private final ObjModel explorer;
    private final ObjModel camper;
    private final ObjModel whites, iris, pupil, glint, mouth;
    private final Matrix4f[] bones = new Matrix4f[BONE_COUNT];
    private final float[] palette = new float[BONE_COUNT * 16];
    private final Matrix4f face = new Matrix4f();
    private final Matrix4f feature = new Matrix4f();
    private final Vector3f bowHand = new Vector3f(), drawHand = new Vector3f();
    private final Vector3f shoulder = new Vector3f(), elbow = new Vector3f(), hand = new Vector3f();
    private final Vector3f axis = new Vector3f(), pole = new Vector3f(), segment = new Vector3f();
    private final Quaternionf rotation = new Quaternionf(), bowRotation = new Quaternionf();

    ClayAvatar() throws IOException {
        explorer = AvatarMesh.load("survivor-explorer.wavatar");
        try {
            camper = AvatarMesh.load("survivor-camper.wavatar");
        } catch (IOException error) {
            explorer.close();
            throw error;
        }
        whites = sphere(0xEDE5CE);
        iris = sphere(0x8F643D);
        pupil = sphere(0x22191D);
        glint = sphere(0xFFFAEE);
        mouth = sphere(0x563039);
        for (int i = 0; i < bones.length; i++) bones[i] = new Matrix4f();
    }

    void draw(SceneShader shader, Matrix4f body, float pitch, float step, float sway, float swing,
              float rightArm, float leftArm, float slash, float alarm, float time,
              float crouch, boolean aiming, boolean guest, AvatarMotion motion,
              Matrix4f bow, float bowReady, float pull, float follow, float drawHandWeight, FloatBuffer buffer) {
        float squash = 0.004f * sway - 0.012f * swing + 0.018f * alarm;
        squash *= 1 - crouch * .8f;
        bones[0].translation(0, -FirstPersonCamera.CROUCH_DROP * crouch, HIP_BACK * crouch)
                .scale(1 - squash * .5f, 1 + squash, 1 - squash * .5f);
        float forward = Math.clamp(motion.forward / 2.6f, -1, 1);
        float lateral = Math.clamp(motion.sideways / 2.6f, -1, 1);
        float twist = step * .065f * (1 - motion.aim) + slash * .16f;
        float raise = AvatarMotion.smooth(0, 1, bowReady);
        if (bow != null) twist -= .27f * raise;
        joint(SPINE, 0, 0, HIP, 0, -crouch * .24f - motion.run * .10f,
                twist, -lateral * .045f - motion.turn * .018f);
        joint(1, SPINE, 0, 1.47f, 0, pitch * 0.76f - alarm * .18f + crouch * .20f, bow == null ? -twist * .7f : -twist, sway * .012f);
        joint(2, SPINE, -.267f, 1.34f, 0, leftArm + crouch * .20f, -.08f * swing, .09f * alarm);
        joint(3, 2, -.355f, 1.09f, 0, .13f + .08f * step + motion.run * .48f + motion.aim * .07f + motion.draw * .35f, 0, 0);
        joint(4, SPINE, .267f, 1.34f, 0, rightArm + crouch * .20f, slash, -.10f * alarm);
        joint(5, 4, .355f, 1.09f, 0, .16f + swing * .32f + motion.run * .24f * (1 - motion.aim) - motion.aim * .07f, 0, 0);
        if (bow != null) poseBow(body, bow, pitch, raise, pull, follow, drawHandWeight);
        leg(6, 7, 10, -1, -step, crouch, forward, lateral, motion.run);
        leg(8, 9, 11, 1, step, crouch, forward, lateral, motion.run);
        for (int i = 0; i < bones.length; i++) bones[i].get(palette, i * 16);
        shader.drawSkinned(guest ? camper : explorer, body, palette, buffer);
        face.set(body).mul(bones[1]);
        drawFace(shader, pitch, aiming, swing, alarm, time + (guest ? 1.7f : 0), buffer);
    }

    void grip(Matrix4f body, Matrix4f destination) {
        destination.set(body).mul(bones[5]).translate(.405f, .822f, -.062f);
    }

    private void poseBow(Matrix4f body, Matrix4f bow, float pitch, float raise,
                         float pull, float follow, float drawHandWeight) {
        float bowPitch = pitch * raise;
        bowRotation.rotationX(bowPitch).rotateZ(.30f * (1 - raise) - .08f * follow);
        bones[3].transformPosition(-.405f, .822f, -.062f, bowHand);
        // The grip moves along the sight line, keeping the left elbow just shy of lockout.
        bones[0].transformPosition(-.08f - .17f * pull, 1.36f + .43f * (float) Math.sin(bowPitch),
                -.43f * (float) Math.cos(bowPitch) - .02f * follow, drawHand);
        bowHand.lerp(drawHand, raise);
        solveArm(2, 3, -1, bowHand, false);
        bow.translation(bowHand).rotate(bowRotation).scale(.95f);

        if (drawHandWeight > .001f) {
            bow.transformPosition(0, 0, Bow.nockDepth(pull), drawHand);
            // On release, the draw hand continues past the anchor as the string springs forward.
            drawHand.add(.17f * follow, .015f * follow, .16f * follow);
            bones[5].transformPosition(.405f, .822f, -.062f, hand);
            drawHand.lerp(hand, 1 - raise * drawHandWeight);
            solveArm(4, 5, 1, drawHand, true);
        }
        bow.mulLocal(body);
    }

    private void solveArm(int upper, int lower, int side, Vector3f goal, boolean drawing) {
        bones[upper].transformPosition(side * .267f, 1.34f, 0, shoulder);
        float a = (float) Math.hypot(.088f, .25f);
        float b = (float) Math.sqrt(.05f * .05f + .268f * .268f + .062f * .062f);
        axis.set(goal).sub(shoulder);
        float reach = axis.length();
        if (reach < .0001f) axis.set(0, -1, 0);
        else axis.div(reach);
        reach = Math.clamp(reach, .04f, a + b - .004f);
        goal.set(shoulder).fma(reach, axis);
        float along = (a * a + reach * reach - b * b) / (2 * reach);
        pole.set(side, drawing ? .42f : -1, drawing ? .8f : 0);
        pole.fma(-pole.dot(axis), axis).normalize();
        elbow.set(shoulder).fma(along, axis).fma((float) Math.sqrt(Math.max(0, a * a - along * along)), pole);
        rotation.rotationTo(segment.set(side * .088f, -.25f, 0), axis.set(elbow).sub(shoulder));
        bones[upper].translation(shoulder).rotate(rotation).translate(-side * .267f, -1.34f, 0);
        rotation.rotationTo(segment.set(side * .05f, -.268f, -.062f), axis.set(goal).sub(elbow));
        bones[lower].translation(elbow).rotate(rotation).translate(-side * .355f, -1.09f, 0);
    }

    private void leg(int thigh, int shin, int foot, int side, float step, float crouch, float forward, float lateral, float run) {
        float upper = step * (.48f + run * .22f) * forward;
        float lower = -(.48f + run * .55f) * Math.max(0, step);
        float footAngle = -(upper + lower) * .65f;
        if (crouch > .001f) {
            // Solve the thigh and shin toward an ankle planted at ground height.
            float rise = HIP - ANKLE - FirstPersonCamera.CROUCH_DROP * crouch
                    - Math.max(0, step) * .055f * crouch;
            float ankleForward = (HIP_BACK + step * .12f * forward) * crouch;
            float reach = (float) Math.hypot(rise, ankleForward);
            float a = HIP - KNEE, b = KNEE - ANKLE;
            float hipBend = (float) Math.acos(Math.clamp((a * a + reach * reach - b * b) / (2 * a * reach), -1, 1));
            float kneeBend = (float) Math.acos(Math.clamp((reach * reach - a * a - b * b) / (2 * a * b), -1, 1));
            upper = upper * (1 - crouch) + (float) Math.atan2(ankleForward, rise) + hipBend;
            lower = lower * (1 - crouch) - kneeBend;
            footAngle = -(upper + lower) * Math.clamp(crouch * 3, 0, 1);
        }
        joint(thigh, 0, side * .14f, HIP, 0, upper, 0, step * lateral * .36f);
        joint(shin, thigh, side * .153f, KNEE, 0, lower, 0, 0);
        joint(foot, shin, side * .153f, ANKLE, 0, footAngle, 0, 0);
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
        float mouthHeight = .012f + .032f * alarm + .010f * swing;
        feature.set(face).translate(.010f, 1.550f, -.205f).rotateZ(-.065f).scale(.038f, mouthHeight, .014f);
        shader.draw(mouth, feature, false, buffer);
        for (int side = -1; side <= 1; side += 2) {
            float cycle = (time + side * .041f) % 3.1f;
            float blink = 1 - smooth(.015f, .14f, Math.abs(cycle - 2.35f));
            float doubleBlink = (float) Math.sin(Math.floor((time + side * .041f) / 3.1f) * 2.3);
            if (doubleBlink > .45f) blink = Math.max(blink, 1 - smooth(.01f, .095f, Math.abs(cycle - 2.64f)));
            float squint = aiming ? (side < 0 ? .32f : .11f) : swing * (side < 0 ? .41f : .15f);
            float opening = Math.clamp(1 + .30f * alarm - blink * .94f - squint, .06f, 1.30f);
            float centerX = side < 0 ? -.101f : .099f;
            float centerY = side < 0 ? 1.655f : 1.647f;
            float centerZ = -.216f - alarm * .012f;
            float size = side < 0 ? .059f : .055f;
            float jitter = (float) Math.sin(time * (side < 0 ? 41 : 49)) * alarm * .006f;
            feature.set(face).translate(centerX, centerY + .055f + alarm * .015f, -.193f)
                    .rotateZ(side * (aiming ? .15f : -.07f)).scale(.044f, .010f, .020f);
            shader.draw(pupil, feature, false, buffer);
            if (opening < .18f) {
                feature.set(face).translate(centerX, centerY, -.223f).rotateZ(side * .11f)
                        .scale(size * .67f, .006f, .016f);
                shader.draw(mouth, feature, false, buffer);
                continue;
            }
            centerZ += blink * .020f;
            feature.set(face).translate(centerX, centerY, centerZ).scale(size, size * opening * .82f, .033f);
            shader.draw(whites, feature, false, buffer);

            float pupilX = centerX + (aiming ? -side * .012f : dart * (side < 0 ? .014f : -.011f))
                    + jitter + side * .009f * alarm;
            float pupilY = centerY + Math.clamp(-pitch * .013f, -.013f, .013f) + jitter;
            float pupilZ = centerZ - .029f;
            feature.set(face).translate(pupilX, pupilY, pupilZ).scale(.028f, .029f * opening, .011f);
            shader.draw(iris, feature, false, buffer);
            float pupilSize = 1 - alarm * .37f;
            feature.set(face).translate(pupilX, pupilY, pupilZ - .009f)
                    .scale(.019f * pupilSize, .021f * opening * pupilSize, .006f);
            shader.draw(pupil, feature, false, buffer);
            feature.set(face).translate(pupilX - .007f, pupilY + .009f * opening, pupilZ - .015f)
                    .scale(.0055f, .0055f * opening, .004f);
            shader.drawEmissive(glint, feature, buffer);
        }
    }

    private static float smooth(float from, float to, float value) {
        float t = Math.clamp((value - from) / (to - from), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static ObjModel sphere(int color) {
        final int rows = 6, sides = 10;
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
        explorer.close();
        camper.close();
        whites.close();
        iris.close();
        pupil.close();
        glint.close();
        mouth.close();
    }
}
