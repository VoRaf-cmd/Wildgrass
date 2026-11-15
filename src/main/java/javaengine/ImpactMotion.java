package javaengine;

import org.joml.Matrix4f;

final class ImpactMotion {
    private static final float HIT_DURATION = 0.22f;
    private static final float LAND_DURATION = 0.42f;
    private static final float BLAST_DURATION = 0.68f;
    private float hitTime = HIT_DURATION;
    private float landTime = LAND_DURATION;
    private float hitStrength;
    private float stridePhase;
    private float strideAmount;
    private float heldStrideAmount;
    private float blastTime = BLAST_DURATION;
    private float blastStrength;

    void hit() {
        hitTime = 0.0f;
        hitStrength = 1.0f;
    }

    void cut() {
        hitTime = 0.0f;
        hitStrength = 0.58f;
    }

    void land() {
        landTime = 0.0f;
    }

    void blast(float strength) {
        if (strength <= 0.0f) return;
        float previous = 1.0f - blastTime / BLAST_DURATION;
        blastTime = 0.0f;
        blastStrength = Math.max(Math.min(strength, 1.0f), previous * previous * blastStrength);
    }

    void update(float delta) {
        hitTime = Math.min(HIT_DURATION, hitTime + delta);
        landTime = Math.min(LAND_DURATION, landTime + delta);
        blastTime = Math.min(BLAST_DURATION, blastTime + delta);
        if (blastTime == BLAST_DURATION) blastStrength = 0.0f;
    }

    void stride(float distance, boolean sprinting, float delta) {
        boolean moving = distance > 0.0001f;
        if (moving) {
            float radiansPerMeter = sprinting ? 2.55f : 3.15f;
            stridePhase = (stridePhase + distance * radiansPerMeter) % (float) (2.0 * Math.PI);
        }
        float blend = 1.0f - (float) Math.exp(-delta * 13.0f);
        float cameraTarget = moving && sprinting ? 1.0f : 0.0f;
        float heldTarget = moving ? (sprinting ? 1.0f : 0.58f) : 0.0f;
        strideAmount += (cameraTarget - strideAmount) * blend;
        heldStrideAmount += (heldTarget - heldStrideAmount) * blend;
        if (!moving && heldStrideAmount < 0.001f) {
            strideAmount = 0.0f;
            heldStrideAmount = 0.0f;
            stridePhase = 0.0f;
        }
    }

    void shakeView(Matrix4f view, float intensity) {
        float hit = (float) Math.cos(hitTime * 62.0f) * (1.0f - hitTime / HIT_DURATION) * hitStrength;
        float land = (float) Math.cos(landTime * 43.0f) * (1.0f - landTime / LAND_DURATION);
        float sway = (float) Math.sin(stridePhase) * strideAmount;
        float bob = (float) Math.cos(stridePhase * 2.0f) * strideAmount;
        float decay = 1.0f - blastTime / BLAST_DURATION;
        float blast = decay * decay * blastStrength;
        float side = (float) Math.cos(blastTime * 52.0f) * blast;
        float vertical = (float) Math.cos(blastTime * 41.0f + 0.42f) * blast;
        view.rotateLocalX(vertical * 0.047f * intensity)
                .rotateLocalZ((hit * 0.011f + land * 0.015f + sway * 0.0045f + side * 0.044f) * intensity)
                .translateLocal((hit * 0.029f + land * 0.041f + sway * 0.014f + side * 0.12f) * intensity,
                        (hit * -0.018f + land * 0.053f + bob * 0.017f + vertical * 0.10f) * intensity, 0.0f);
    }

    void recoilAxe(Matrix4f model) {
        float progress = hitTime / HIT_DURATION;
        float recoil = (float) Math.sin(Math.PI * progress) * (1.0f - progress) * hitStrength;
        model.translateLocal(0.04f * recoil, 0.08f * recoil, 0.15f * recoil);
    }

    void heldPose(Matrix4f pose, float intensity) {
        heldPose(pose, intensity, 1.0f);
    }

    void heldPose(Matrix4f pose, float intensity, float shake) {
        float amplitude = heldStrideAmount * intensity;
        float across = (float) Math.sin(stridePhase) * amplitude;
        float step = (float) Math.cos(stridePhase * 2.0f) * amplitude;
        float decay = 1.0f - blastTime / BLAST_DURATION;
        float blast = decay * decay * blastStrength * shake;
        float jolt = (float) Math.cos(blastTime * 46.0f) * blast;
        pose.translation(across * 0.052f + jolt * 0.105f,
                        step * 0.036f - amplitude * 0.012f - blast * 0.073f,
                        step * 0.014f + blast * 0.085f)
                .rotateZ(across * 0.046f + jolt * 0.075f);
    }
}
