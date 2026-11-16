package javaengine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.glfw.GLFW.*;

final class FirstPersonCamera {
    private static final float WALK_SPEED = 2.6f;
    private static final float SPRINT_SPEED = 4.7f;
    private static final float CROUCH_SPEED = 1.38f;
    static final float EYE_HEIGHT = 1.53f;
    static final float CROUCH_DROP = 0.43f;
    private final Vector3f position = new Vector3f(0.18f, EYE_HEIGHT, 4.65f);
    private final Vector3f direction = new Vector3f();
    private final Vector3f renderEye = new Vector3f();
    private final Vector3f renderAim = new Vector3f();
    private int perspective;
    private float yaw;
    private float pitch = -0.085f;
    private boolean captured;
    private boolean sprinting;
    private boolean crouching;
    private float groundHeight;
    private float crouchAmount;
    private boolean hasMousePosition;
    private double lastX;
    private double lastY;
    private float sensitivityScale = 1.0f;

    void attach(long window, Inventory inventory, GameMenu menu, Runnable useItem) {
        attach(window, inventory, menu, useItem, () -> { });
    }

    void attach(long window, Inventory inventory, GameMenu menu, Runnable useItem, Runnable releaseItem) {
        glfwSetCursorPosCallback(window, (handle, x, y) -> {
            if (menu.visible()) {
                menuPointer(handle, menu, x, y, false);
            } else if (captured && hasMousePosition) {
                double sensitivity = 0.0028 * menu.settings().fraction(GameSettings.Option.SENSITIVITY) * sensitivityScale;
                yaw = (float) ((yaw + (x - lastX) * sensitivity) % (2 * Math.PI));
                pitch = (float) Math.clamp(pitch - (y - lastY) * sensitivity, -1.55, 1.55);
            }
            lastX = x;
            lastY = y;
            hasMousePosition = captured;
        });
        glfwSetKeyCallback(window, (handle, key, scancode, action, mods) -> {
            if (menu.screen() == GameMenu.Screen.CONNECT && key == GLFW_KEY_V && action == GLFW_PRESS
                    && (mods & GLFW_MOD_CONTROL) != 0) {
                menu.pasteIp(glfwGetClipboardString(handle));
                return;
            }
            if (menu.visible() || key == GLFW_KEY_ESCAPE || key == GLFW_KEY_E) {
                menu.key(key, action);
                syncCursor(handle, menu);
            } else if (action == GLFW_PRESS && key == GLFW_KEY_F5) {
                cyclePerspective();
            } else if (action == GLFW_PRESS && key >= GLFW_KEY_1 && key <= GLFW_KEY_7) {
                inventory.select(key - GLFW_KEY_1);
            }
        });
        glfwSetCharCallback(window, (handle, codepoint) -> menu.charInput(codepoint));
        glfwSetScrollCallback(window, (handle, x, y) -> {
            if (captured && y != 0.0) {
                inventory.select(inventory.selected() + (y < 0.0 ? 1 : -1));
            }
        });
        glfwSetMouseButtonCallback(window, (handle, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT && menu.visible()) {
                if (action == GLFW_PRESS) {
                    try (var stack = MemoryStack.stackPush()) {
                        var x = stack.mallocDouble(1);
                        var y = stack.mallocDouble(1);
                        glfwGetCursorPos(handle, x, y);
                        menuPointer(handle, menu, x.get(0), y.get(0), true);
                    }
                    syncCursor(handle, menu);
                } else if (action == GLFW_RELEASE) {
                    menu.release();
                }
            } else if (captured && button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS) {
                useItem.run();
            } else if (captured && button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_RELEASE) {
                releaseItem.run();
            }
        });
        glfwSetWindowFocusCallback(window, (handle, focused) -> {
            if (!focused) {
                menu.pause();
                syncCursor(handle, menu);
            }
        });
        syncCursor(window, menu);
    }

    private static void menuPointer(long window, GameMenu menu, double x, double y, boolean press) {
        try (var stack = MemoryStack.stackPush()) {
            var width = stack.mallocInt(1);
            var height = stack.mallocInt(1);
            glfwGetWindowSize(window, width, height);
            menu.pointer(x, y, width.get(0), height.get(0), press);
        }
    }

    private void syncCursor(long window, GameMenu menu) {
        boolean enabled = !menu.visible();
        if (captured != enabled) {
            capture(window, enabled);
        }
    }

    void refreshCursor(long window, GameMenu menu) {
        syncCursor(window, menu);
    }

    private void capture(long window, boolean enabled) {
        captured = enabled;
        glfwSetInputMode(window, GLFW_CURSOR, enabled ? GLFW_CURSOR_DISABLED : GLFW_CURSOR_NORMAL);
        hasMousePosition = false;
    }

    float update(long window, float seconds, Forest forest) {
        if (!captured) {
            return updateMovement(0, 0, false, false, seconds, forest);
        }
        int forward = pressed(window, GLFW_KEY_W) - pressed(window, GLFW_KEY_S);
        int right = pressed(window, GLFW_KEY_D) - pressed(window, GLFW_KEY_A);
        boolean sprint = pressed(window, GLFW_KEY_LEFT_SHIFT) != 0 || pressed(window, GLFW_KEY_RIGHT_SHIFT) != 0;
        boolean crouch = pressed(window, GLFW_KEY_LEFT_CONTROL) != 0 || pressed(window, GLFW_KEY_RIGHT_CONTROL) != 0;
        return updateMovement(forward, right, sprint, crouch, seconds, forest);
    }

    float updateMovement(int forward, int right, boolean sprint, boolean crouch, float seconds, Forest forest) {
        crouching = crouch;
        float traveled = move(forward, right, sprint, seconds, forest);
        updateHeight(seconds);
        return traveled;
    }

    private void updateHeight(float seconds) {
        float blend = 1.0f - (float) Math.exp(-seconds * 14.0f);
        crouchAmount += ((crouching ? 1.0f : 0.0f) - crouchAmount) * blend;
        groundHeight += (Terrain.height(position.x, position.z) - groundHeight) * blend;
        position.y = groundHeight + EYE_HEIGHT - CROUCH_DROP * crouchAmount;
    }

    void spawnAt(float x, float z) {
        groundHeight = Terrain.height(x, z);
        crouchAmount = 0;
        crouching = false;
        position.set(x, groundHeight + EYE_HEIGHT, z);
    }

    float move(int forward, int right, boolean sprint, float seconds, Forest forest) {
        sprinting = false;
        if (forward == 0 && right == 0) {
            return 0.0f;
        }
        sprint &= !crouching;
        float speed = crouching ? CROUCH_SPEED : sprint ? SPRINT_SPEED : WALK_SPEED;
        float distance = speed * seconds / (float) Math.hypot(forward, right);
        float sin = (float) Math.sin(yaw);
        float cos = (float) Math.cos(yaw);
        float x = Math.clamp(position.x + (sin * forward + cos * right) * distance, -Terrain.WALK_LIMIT, Terrain.WALK_LIMIT);
        float z = Math.clamp(position.z + (-cos * forward + sin * right) * distance, -Terrain.WALK_LIMIT, Terrain.WALK_LIMIT);
        float beforeX = position.x;
        float beforeZ = position.z;
        // Se a árvore cair sobre a posição do jogador, deixe-o sair da colisão.
        boolean blocked = forest.blocksPlayer(position.x, position.z);
        if (blocked || !forest.blocksPlayer(x, position.z)) {
            position.x = x;
        }
        if (blocked || !forest.blocksPlayer(position.x, z)) {
            position.z = z;
        }
        float traveled = (float) Math.hypot(position.x - beforeX, position.z - beforeZ);
        sprinting = sprint && traveled > 0.0f;
        return traveled;
    }

    boolean sprinting() {
        return sprinting;
    }

    boolean crouching() { return crouching; }

    void sensitivityScale(float scale) {
        sensitivityScale = scale;
    }

    private int pressed(long window, int key) {
        return glfwGetKey(window, key) == GLFW_PRESS ? 1 : 0;
    }

    void view(Matrix4f matrix) {
        float horizontal = (float) Math.cos(pitch);
        direction.set((float) Math.sin(yaw) * horizontal, (float) Math.sin(pitch),
                -(float) Math.cos(yaw) * horizontal);
        matrix.identity().lookAlong(direction.x, direction.y, direction.z, 0.0f, 1.0f, 0.0f)
                .translate(-position.x, -position.y, -position.z);
    }

    void cyclePerspective() { perspective = (perspective + 1) % 3; }

    boolean thirdPerson() { return perspective != 0; }
    boolean frontView() { return perspective == 2; }
    Vector3f renderEye() { return renderEye; }
    Vector3f renderAim() { return renderAim; }

    void perspectiveView(Matrix4f matrix, Forest forest) {
        view(matrix);
        renderEye.set(position);
        renderAim.set(direction);
        if (!thirdPerson()) return;
        float sign = frontView() ? 1 : -1;
        // Sweep the camera boom, stopping before terrain or a solid trunk.
        float distance = 0;
        for (float d = .1f; d <= 3.2f; d += .1f) {
            float x = position.x + direction.x * sign * d;
            float y = position.y + direction.y * sign * d;
            float z = position.z + direction.z * sign * d;
            if (y < Terrain.height(x, z) + .18f || forest.blocksPlayer(x, z)) break;
            distance = d;
        }
        renderEye.fma(sign * distance, direction);
        if (frontView()) renderAim.negate();
        matrix.identity().lookAlong(renderAim, new Vector3f(0, 1, 0))
                .translate(-renderEye.x, -renderEye.y, -renderEye.z);
    }

    Vector3f position() {
        return position;
    }

    Vector3f aim() {
        return direction;
    }

    float yaw() { return yaw; }
    float pitch() { return pitch; }

    boolean ignite(IgnitionAim ignition, Forest forest, Meadow meadow, float seconds) {
        return ignite(ignition, forest, meadow, null, seconds);
    }

    boolean ignite(IgnitionAim ignition, Forest forest, Meadow meadow, Campfires campfires, float seconds) {
        if (!captured) {
            ignition.reset();
            return false;
        }
        ignition.begin();
        forest.aimFire(ignition, position, direction);
        meadow.aimFire(ignition, position, direction);
        if (campfires != null) campfires.aimFire(ignition, position, direction);
        return ignition.heat(seconds);
    }

}
