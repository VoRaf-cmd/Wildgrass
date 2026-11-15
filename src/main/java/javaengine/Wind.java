package javaengine;

final class Wind {
    static final float X = 0.866f;
    static final float Z = 0.50f;

    private float time;
    private float strength;

    void update(float delta) {
        time += delta;
        strength = 0.67f + 0.25f * (float) Math.sin(time * 0.63f)
                + 0.12f * (float) Math.sin(time * 1.53f + 1.2f);
    }

    float time() {
        return time;
    }

    float strength() {
        return strength;
    }
}
