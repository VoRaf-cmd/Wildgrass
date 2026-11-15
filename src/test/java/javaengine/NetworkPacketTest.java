package javaengine;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

final class NetworkPacketTest {
    @Test void binaryProtocolValidatesHeaderAndCoordinates() {
        var hello = NetworkPacket.read(NetworkPacket.hello(42));
        assertEquals(NetworkPacket.HELLO, hello.type());
        assertEquals(42, hello.nonce());
        var position = NetworkPacket.read(NetworkPacket.position(51, 7, 1.25f, 2.5f, -4.0f));
        assertEquals(NetworkPacket.POSITION, position.type());
        assertEquals(7, position.sequence());
        assertEquals(-4.0f, position.z());
        assertNull(NetworkPacket.read(NetworkPacket.position(51, 8, Float.NaN, 0, 0)));
        assertNull(NetworkPacket.read(ByteBuffer.wrap(new byte[] {1, 2, 3})));
        var wrongVersion = NetworkPacket.hello(42);
        wrongVersion.put(4, (byte) 99);
        assertNull(NetworkPacket.read(wrongVersion.rewind()));
    }
}
