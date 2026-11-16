package javaengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class FirstPersonCameraTest {
    @Test void perspectivesCycleWithoutChangingGameplayAimOrPosition() {
        var camera = new FirstPersonCamera();
        var forest = new Forest();
        camera.spawnAt(4, 3);
        var matrix = new org.joml.Matrix4f();
        camera.perspectiveView(matrix, forest);
        var originalPosition = new org.joml.Vector3f(camera.position());
        var originalAim = new org.joml.Vector3f(camera.aim());
        assertFalse(camera.thirdPerson());
        camera.cyclePerspective();
        camera.perspectiveView(matrix, forest);
        assertTrue(camera.thirdPerson());
        assertFalse(camera.frontView());
        assertTrue(new org.joml.Vector3f(camera.renderEye()).sub(originalPosition).dot(originalAim) < 0);
        camera.cyclePerspective();
        camera.perspectiveView(matrix, forest);
        assertTrue(camera.frontView());
        assertTrue(camera.renderAim().dot(originalAim) < -.99f);
        assertEquals(originalPosition, camera.position());
        assertEquals(originalAim, camera.aim());
        camera.cyclePerspective();
        camera.perspectiveView(matrix, forest);
        assertFalse(camera.thirdPerson());
        assertEquals(originalPosition, camera.renderEye());
    }

    @Test void frontCameraStopsBeforeTreeTrunk() {
        var camera = new FirstPersonCamera();
        camera.spawnAt(0, 2);
        camera.cyclePerspective();
        camera.cyclePerspective();
        camera.perspectiveView(new org.joml.Matrix4f(), new Forest());
        assertTrue(camera.renderEye().distance(camera.position()) < 2);
        assertTrue(camera.renderEye().z > 0);
    }

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
