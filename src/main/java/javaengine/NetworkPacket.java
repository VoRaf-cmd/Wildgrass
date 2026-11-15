package javaengine;

import java.nio.ByteBuffer;

/** Small, versioned UDP messages. All fields use network byte order. */
final class NetworkPacket {
    static final int MAGIC = 0x57475253; // WGRS
    static final byte VERSION = 3;
    static final byte HELLO = 1;
    static final byte WELCOME = 2;
    static final byte POSITION = 3;
    static final byte GOODBYE = 4;
    static final byte BUSY = 5;
    static final byte ACTION = 6;
    static final byte ACK = 7;
    static final int MAX_BYTES = 64;

    private NetworkPacket() { }

    static ByteBuffer hello(long nonce) {
        return header(HELLO, 14).putLong(nonce).flip();
    }

    static ByteBuffer welcome(long clientNonce, long hostNonce) {
        return header(WELCOME, 22).putLong(clientNonce).putLong(hostNonce).flip();
    }

    static ByteBuffer position(long nonce, int sequence, float x, float y, float z) {
        return position(nonce, sequence, x, y, z, 0, 0, -1, 0);
    }

    static ByteBuffer position(long nonce, int sequence, float x, float y, float z,
                               float yaw, float pitch, int item, int flags) {
        return header(POSITION, 40).putLong(nonce).putInt(sequence).putFloat(x).putFloat(y).putFloat(z)
                .putFloat(yaw).putFloat(pitch).put((byte) item).put((byte) flags).flip();
    }

    static ByteBuffer action(long nonce, int sequence, Action action) {
        return header(ACTION, 47).putLong(nonce).putInt(sequence).put((byte) action.kind().ordinal())
                .putFloat(action.x()).putFloat(action.y()).putFloat(action.z())
                .putFloat(action.dx()).putFloat(action.dy()).putFloat(action.dz()).putFloat(action.value()).flip();
    }

    static ByteBuffer ack(long nonce, int sequence) {
        return header(ACK, 18).putLong(nonce).putInt(sequence).flip();
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
                    ? new Message(type, bytes.getLong(), 0, 0, null, null) : null;
            case WELCOME -> bytes.remaining() == 16
                    ? new Message(type, bytes.getLong(), bytes.getLong(), 0, null, null) : null;
            case POSITION -> positionMessage(bytes);
            case ACTION -> actionMessage(bytes);
            case ACK -> bytes.remaining() == 12
                    ? new Message(type, bytes.getLong(), 0, bytes.getInt(), null, null) : null;
            case BUSY -> bytes.remaining() == 0 ? new Message(type, 0, 0, 0, null, null) : null;
            default -> null;
        };
    }

    private static Message positionMessage(ByteBuffer bytes) {
        if (bytes.remaining() != 34) return null;
        long nonce = bytes.getLong();
        int sequence = bytes.getInt();
        float x = bytes.getFloat();
        float y = bytes.getFloat();
        float z = bytes.getFloat();
        float yaw = bytes.getFloat();
        float pitch = bytes.getFloat();
        int item = bytes.get();
        int flags = Byte.toUnsignedInt(bytes.get());
        if (!coordinate(x) || !coordinate(y) || !coordinate(z) || !(Math.abs(yaw) <= 6.3f)
                || !(Math.abs(pitch) <= 1.56f) || item < -1 || item >= ITEMS.length || (flags & ~POSE_FLAGS) != 0) return null;
        return new Message(POSITION, nonce, 0, sequence, new Pose(x, y, z, yaw, pitch, item, flags), null);
    }

    private static Message actionMessage(ByteBuffer bytes) {
        if (bytes.remaining() != 41) return null;
        long nonce = bytes.getLong();
        int sequence = bytes.getInt();
        int kind = Byte.toUnsignedInt(bytes.get());
        float x = bytes.getFloat(), y = bytes.getFloat(), z = bytes.getFloat();
        float dx = bytes.getFloat(), dy = bytes.getFloat(), dz = bytes.getFloat();
        float value = bytes.getFloat();
        if (kind >= KINDS.length || !coordinate(x) || !coordinate(y) || !coordinate(z)
                || !(dx * dx + dy * dy + dz * dz <= 1.01f) || !coordinate(value)) return null;
        return new Message(ACTION, nonce, 0, sequence, null, new Action(KINDS[kind], x, y, z, dx, dy, dz, value));
    }

    private static boolean coordinate(float value) { return Math.abs(value) <= 100.0f; }

    static final int LIT = 1, AIMING = 2, DRAWING = 4, CROUCHING = 8;
    private static final int POSE_FLAGS = LIT | AIMING | DRAWING | CROUCHING;
    static final Inventory.Item[] ITEMS = Inventory.Item.values();
    enum Kind { USE, BLAST, DYNAMITE, ARROW, STONE, CHOP, CUT, CAMPFIRE, IGNITE }
    private static final Kind[] KINDS = Kind.values();
    record Pose(float x, float y, float z, float yaw, float pitch, int item, int flags) { }
    record Action(Kind kind, float x, float y, float z, float dx, float dy, float dz, float value) { }
    record Message(byte type, long nonce, long otherNonce, int sequence, Pose pose, Action action) {
        float z() { return pose.z(); }
    }
}
