package javaengine;

import java.nio.ByteBuffer;

/** Small, versioned UDP messages. All fields use network byte order. */
final class NetworkPacket {
    static final int MAGIC = 0x57475253; // WGRS
    static final byte VERSION = 1;
    static final byte HELLO = 1;
    static final byte WELCOME = 2;
    static final byte POSITION = 3;
    static final byte GOODBYE = 4;
    static final byte BUSY = 5;
    static final int MAX_BYTES = 64;

    private NetworkPacket() { }

    static ByteBuffer hello(long nonce) {
        return header(HELLO, 14).putLong(nonce).flip();
    }

    static ByteBuffer welcome(long clientNonce, long hostNonce) {
        return header(WELCOME, 22).putLong(clientNonce).putLong(hostNonce).flip();
    }

    static ByteBuffer position(long nonce, int sequence, float x, float y, float z) {
        return header(POSITION, 30).putLong(nonce).putInt(sequence).putFloat(x).putFloat(y).putFloat(z).flip();
    }

    static ByteBuffer goodbye(long nonce) {
        return header(GOODBYE, 14).putLong(nonce).flip();
    }

    static ByteBuffer busy() {
        return header(BUSY, 6).flip();
    }

    private static ByteBuffer header(byte type, int size) {
        return ByteBuffer.allocate(size).putInt(MAGIC).put(VERSION).put(type);
    }

    static Message read(ByteBuffer bytes) {
        if (bytes.remaining() < 6 || bytes.getInt() != MAGIC || bytes.get() != VERSION) return null;
        byte type = bytes.get();
        return switch (type) {
            case HELLO, GOODBYE -> bytes.remaining() == 8
                    ? new Message(type, bytes.getLong(), 0, 0, 0, 0, 0) : null;
            case WELCOME -> bytes.remaining() == 16
                    ? new Message(type, bytes.getLong(), bytes.getLong(), 0, 0, 0, 0) : null;
            case POSITION -> positionMessage(bytes);
            case BUSY -> bytes.remaining() == 0 ? new Message(type, 0, 0, 0, 0, 0, 0) : null;
            default -> null;
        };
    }

    private static Message positionMessage(ByteBuffer bytes) {
        if (bytes.remaining() != 24) return null;
        long nonce = bytes.getLong();
        int sequence = bytes.getInt();
        float x = bytes.getFloat();
        float y = bytes.getFloat();
        float z = bytes.getFloat();
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
                || Math.abs(x) > 100 || Math.abs(y) > 100 || Math.abs(z) > 100) return null;
        return new Message(POSITION, nonce, 0, sequence, x, y, z);
    }

    record Message(byte type, long nonce, long otherNonce, int sequence, float x, float y, float z) { }
}
