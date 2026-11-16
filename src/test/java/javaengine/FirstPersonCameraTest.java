package javaengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class FirstPersonCameraTest {
    @Test void crouchLowersTheViewAndTakesPriorityOverSprint() {
        var camera = new FirstPersonCamera();
        var forest = new Forest();
        camera.spawnAt(4, 3);
        float standingHeight = camera.position().y;
        float walked = camera.updateMovement(0, 1, false, false, .05f, forest);
        camera.spawnAt(4, 3);
        float crouchWalk = camera.updateMovement(0, 1, true, true, .05f, forest);

        assertTrue(camera.crouching());
        assertFalse(camera.sprinting());
        assertTrue(crouchWalk > 0 && crouchWalk < walked);
        assertTrue(camera.position().y < standingHeight);
        assertTrue(camera.position().y > standingHeight - FirstPersonCamera.CROUCH_DROP);

        for (int i = 0; i < 30; i++) camera.updateMovement(0, 0, false, true, .05f, forest);
        float ground = Terrain.height(camera.position().x, camera.position().z);
        assertEquals(ground + FirstPersonCamera.EYE_HEIGHT - FirstPersonCamera.CROUCH_DROP, camera.position().y, .001f);

        camera.updateMovement(0, 1, true, false, .05f, forest);
        assertFalse(camera.crouching());
        assertTrue(camera.sprinting());
        assertTrue(camera.position().y > ground + FirstPersonCamera.EYE_HEIGHT - FirstPersonCamera.CROUCH_DROP);
    }
}
