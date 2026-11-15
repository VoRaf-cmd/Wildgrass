package javaengine;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.net.DatagramSocket;

import static org.junit.jupiter.api.Assertions.*;

final class NetworkManagerTest {
    @Test void twoLocalPlayersExchangePositionsAndDisconnect() throws Exception {
        int port;
        try (var socket = new DatagramSocket(0)) { port = socket.getLocalPort(); }
        var hostPosition = new Vector3f(1.2f, 1.53f, -2.0f);
        var clientPosition = new Vector3f(3.0f, 1.53f, 4.0f);
        try (var host = new NetworkManager(); var client = new NetworkManager(); var extra = new NetworkManager()) {
            host.startHost(port);
            client.connectToHost("127.0.0.1", port);
            for (int i = 0; i < 30; i++) {
                client.update(.05f, clientPosition);
                host.update(.05f, hostPosition);
            }
            assertTrue(host.connected());
            assertTrue(client.connected());
            assertTrue(host.hasRemote());
            assertTrue(client.hasRemote());
            assertEquals(clientPosition.x, host.remotePosition().x, .001f);
            assertEquals(hostPosition.z, client.remotePosition().z, .001f);

            extra.connectToHost("127.0.0.1", port);
            for (int i = 0; i < 5; i++) {
                extra.update(.05f, clientPosition);
                host.update(.05f, hostPosition);
            }
            assertEquals(NetworkManager.Mode.OFFLINE, extra.mode());
            assertTrue(extra.message().contains("dois jogadores"));
            client.close();
            host.update(.05f, hostPosition);
            assertFalse(host.connected());
            assertEquals(NetworkManager.Mode.HOST, host.mode());
        }
    }

    @Test void invalidIpIsRejectedBeforeOpeningSocket() throws Exception {
        try (var client = new NetworkManager()) {
            assertThrows(IllegalArgumentException.class, () -> client.connectToHost("999.5.1.1", 7777));
            assertEquals(NetworkManager.Mode.OFFLINE, client.mode());
        }
    }

    @Test void missingHostTimesOutWithUsefulMessage() throws Exception {
        int unusedPort;
        try (var socket = new DatagramSocket(0)) { unusedPort = socket.getLocalPort(); }
        try (var client = new NetworkManager()) {
            client.connectToHost("127.0.0.1", unusedPort);
            for (int i = 0; i < 210; i++) client.update(.05f, new Vector3f());
            assertEquals(NetworkManager.Mode.OFFLINE, client.mode());
            assertTrue(client.message().contains("Sem resposta"));
        }
    }
}
