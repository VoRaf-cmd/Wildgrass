package javaengine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

final class GameMenuNetworkTest {
    @Test void titleConnectsAndPauseHosts(@TempDir Path folder) {
        var menu = new GameMenu(new GameSettings(folder.resolve("settings.properties")));
        menu.pointer(90, 310, 960, 540, true);
        assertEquals(GameMenu.Screen.CONNECT, menu.screen());
        menu.pasteIp("127.0.0.1");
        menu.pointer(90, 330, 960, 540, true);
        assertEquals(GameMenu.NetworkAction.CONNECT, menu.consumeNetworkAction());
        assertEquals("127.0.0.1", menu.ipAddress());
        menu.networkConnected();
        assertEquals(GameMenu.Screen.PLAYING, menu.screen());
        menu.key(GLFW_KEY_ESCAPE, GLFW_PRESS);
        menu.pointer(90, 310, 960, 540, true);
        assertEquals(GameMenu.NetworkAction.DISCONNECT, menu.consumeNetworkAction());

        var solo = new GameMenu(new GameSettings(folder.resolve("solo.properties")));
        solo.key(GLFW_KEY_ENTER, GLFW_PRESS);
        solo.key(GLFW_KEY_ESCAPE, GLFW_PRESS);
        solo.pointer(90, 310, 960, 540, true);
        assertEquals(GameMenu.NetworkAction.HOST, solo.consumeNetworkAction());
        solo.networkHostStarted();
        assertEquals(GameMenu.Screen.PLAYING, solo.screen());
    }
}
