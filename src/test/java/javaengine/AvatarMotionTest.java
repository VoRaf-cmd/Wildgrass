package javaengine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AvatarMotionTest {
    @Test void cadenceAndBlendsAreStableAcrossFrameRates() {
        AvatarMotion a = walk(30), b = walk(144);
        assertEquals(a.phase, b.phase, .01f);
        assertEquals(a.speed, b.speed, .001f);
        assertEquals(2.6f, a.forward, .001f);
        assertEquals(0, a.sideways, .001f);
    }

    @Test void stopsWithoutAdvancingFeetAndResetsOnTeleport() {
        AvatarMotion a = walk(60);
        float phase = a.phase;
        for (int i = 0; i < 120; i++) a.update(1f / 60, 0, 0, 0, 0, false, false);
        assertEquals(phase, a.phase);
        assertTrue(a.speed < .001f);
        a.reset();
        assertEquals(0, a.forward);
        assertEquals(0, a.phase);
    }

    @Test void attackAnticipatesThenStrikesAndRecoversContinuously() {
        assertEquals(0, AvatarMotion.attack(0));
        assertTrue(AvatarMotion.attack(.2f) < 0);
        assertEquals(1, AvatarMotion.attack(.43f), .00001f);
        assertEquals(0, AvatarMotion.attack(1));
        for (float boundary : new float[]{.27f, .43f, 1})
            assertEquals(AvatarMotion.attack(boundary - .00001f), AvatarMotion.attack(boundary), .001f);
    }

    private AvatarMotion walk(int fps) {
        AvatarMotion a = new AvatarMotion();
        for (int i = 0; i < fps * 2; i++) a.update(1f / fps, 0, -2.6f / fps, 0, 0, false, false);
        return a;
    }
}
