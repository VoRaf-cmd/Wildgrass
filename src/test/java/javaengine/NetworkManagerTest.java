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

    @Test void aimEquipmentAndOrderedActionsReachTheOtherPlayerOnlyOnce() throws Exception {
        int port;
        try (var socket = new DatagramSocket(0)) { port = socket.getLocalPort(); }
        var eye = new Vector3f(1, 1.53f, 2);
        var direction = new Vector3f(0, 0, -1);
        var blastPoint = new Vector3f(2, 0.1f, 5);
        try (var host = new NetworkManager(); var client = new NetworkManager()) {
            host.startHost(port);
            client.connectToHost("127.0.0.1", port);
            for (int i = 0; i < 8; i++) {
                client.update(.05f, eye, 1.2f, -0.4f, Inventory.Item.DYNAMITE,
                        NetworkPacket.AIMING | NetworkPacket.CROUCHING);
                host.update(.05f, eye);
            }
            var pose = host.remotePose();
            assertEquals(1.2f, pose.yaw(), .001f);
            assertEquals(-0.4f, pose.pitch(), .001f);
            assertEquals(Inventory.Item.DYNAMITE.ordinal(), pose.item());
            assertEquals(NetworkPacket.AIMING | NetworkPacket.CROUCHING, pose.flags());

            client.action(NetworkPacket.Kind.DYNAMITE, eye, direction, 0);
            client.action(NetworkPacket.Kind.BLAST, blastPoint, null, 0);
            // Let the sender retry several times before the receiver can acknowledge the first event.
            for (int i = 0; i < 12; i++) client.update(.05f, eye);
            for (int i = 0; i < 12; i++) {
                host.update(.05f, eye);
                client.update(.05f, eye);
            }
            assertEquals(NetworkPacket.Kind.DYNAMITE, host.pollAction().kind());
            var blast = host.pollAction();
            assertEquals(NetworkPacket.Kind.BLAST, blast.kind());
            assertEquals(blastPoint.x, blast.x());
            assertEquals(blastPoint.y, blast.y());
            assertEquals(blastPoint.z, blast.z());
            assertNull(host.pollAction());
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
