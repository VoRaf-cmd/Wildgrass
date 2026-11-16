package javaengine;

import org.joml.Matrix4f;

import java.io.IOException;
import java.nio.FloatBuffer;

final class CowModels implements AutoCloseable {
    private final Coat[] coats;
    private final Matrix4f root = new Matrix4f();
    private final Matrix4f pose = new Matrix4f();

    CowModels() throws IOException {
        coats = new Coat[] {new Coat("spotted"), new Coat("brown")};
    }

    void draw(SceneShader shader, Ranch.Cow[] cows, ObjModel meat, FloatBuffer buffer) {
        for (Ranch.Cow animal : cows) {
            Coat coat = coats[animal.coat];
            float walk = (float) Math.sin(animal.phase) * animal.walk;
            float fall = animal.fall * animal.fall * (3 - 2 * animal.fall);
            float bounce = walk * walk * .06f * (1 - fall);
            root.translation(animal.display.x, animal.display.y + bounce, animal.display.z)
                    .rotateY(-animal.displayYaw).translate(0, .84f - .36f * fall, 0)
                    .rotateZ(fall * 1.53f + walk * .029f + animal.flinch * .095f)
                    .translate(0, -.84f, 0);
            shader.draw(coat.body, root, false, buffer);
            float chew = (float) Math.sin(animal.animation * 12 + animal.coat) * .065f;
            pose.set(root).translate(0, 1.16f, -.66f)
                    .rotateX(-animal.headDown * 1.00f + chew * animal.headDown + animal.flinch * .32f)
                    .rotateZ(walk * .065f);
            shader.draw(coat.head, pose, false, buffer);
            for (int side = -1; side <= 1; side += 2) for (int end = -1; end <= 1; end += 2) {
                pose.set(root).translate(side * .282f, .885f, end < 0 ? -.60f : .65f)
                        .rotateX(walk * side * end * .47f * (1 - fall) + .18f * fall);
                shader.draw(coat.leg, pose, false, buffer);
            }
            pose.set(root).translate(0, 1.22f, .981f).rotateX(-.16f - .23f * animal.walk)
                    .rotateZ((float) Math.sin(animal.phase * .73f + animal.animation * 2.3f) * .36f * (1 - fall));
            shader.draw(coat.tail, pose, false, buffer);
            for (int i = 0; i < animal.meat; i++) {
                float angle = i * 2.4f + animal.displayYaw;
                float x = animal.display.x + .75f * (float) Math.cos(angle);
                float z = animal.display.z + .75f * (float) Math.sin(angle);
                pose.translation(x, Terrain.height(x, z) + .075f, z).rotateY(angle);
                shader.draw(meat, pose, false, buffer);
            }
        }
    }

    @Override public void close() {
        for (Coat coat : coats) {
            coat.body.close();
            coat.head.close();
            coat.leg.close();
            coat.tail.close();
        }
    }

    private static final class Coat {
        final ObjModel body, head, leg, tail;

        Coat(String name) throws IOException {
            body = AxeDemo.loadModel("cow-" + name + "-body.obj", null);
            head = AxeDemo.loadModel("cow-" + name + "-head.obj", null);
            leg = AxeDemo.loadModel("cow-" + name + "-leg.obj", null);
            tail = AxeDemo.loadModel("cow-" + name + "-tail.obj", null);
        }
    }
}
