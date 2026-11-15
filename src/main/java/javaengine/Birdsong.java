package javaengine;

import org.joml.Vector3f;

import java.util.Random;

final class Birdsong implements AutoCloseable {
    private final SoundEffect[] calls = {
            new SoundEffect("bird-call-1.wav"),
            new SoundEffect("bird-call-2.wav"),
            new SoundEffect("bird-call-3.wav")
    };
    private final Random random = new Random();
    private int lastCall = -1;
    private float remaining = 0.75f;

    void masterVolume(int percent) {
        for (SoundEffect call : calls) {
            call.masterVolume(percent);
        }
    }

    void update(float delta, BirdFlocks birds, Vector3f eye, Vector3f aim, boolean menu) {
        remaining -= delta;
        if (remaining > 0.0f) {
            return;
        }
        int next = (lastCall + 1 + random.nextInt(calls.length - 1)) % calls.length;
        lastCall = next;
        Vector3f source = birds.position(random.nextInt(birds.flockCount()));
        float distance = eye.distance(source);
        float horizontal = (float) Math.hypot(source.x - eye.x, source.z - eye.z);
        float pan = horizontal > 0.001f
                ? ((source.z - eye.z) * aim.x - (source.x - eye.x) * aim.z) / horizontal
                : 0.0f;
        calls[next].volume(-6.0f - Math.min(distance * 0.34f, 13.0f) - (menu ? 5.0f : 0.0f));
        calls[next].pan(Math.clamp(pan * 0.70f, -0.70f, 0.70f));
        calls[next].play();
        remaining = random.nextFloat(1.85f, 4.2f);
    }

    @Override
    public void close() {
        for (SoundEffect call : calls) {
            call.close();
        }
    }
}
