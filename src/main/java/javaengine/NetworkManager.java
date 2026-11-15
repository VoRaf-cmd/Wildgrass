package javaengine;

import org.joml.Vector3f;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.ThreadLocalRandom;

/** One host and one client. Called from the game loop; receive never waits for a packet. */
final class NetworkManager implements AutoCloseable {
    static final int DEFAULT_PORT = 7777;
    private static final float SEND_INTERVAL = 0.05f;
    private static final float HELLO_INTERVAL = 0.5f;
    private static final float PEER_TIMEOUT = 6.0f;
    private static final float CONNECT_TIMEOUT = 10.0f;
    private final ByteBuffer receiveBuffer = ByteBuffer.allocate(NetworkPacket.MAX_BYTES);
    private final Vector3f remotePosition = new Vector3f();
    private DatagramChannel channel;
    private InetSocketAddress peer;
    private Mode mode = Mode.OFFLINE;
    private String message = "";
    private long localNonce;
    private long peerNonce;
    private int sendSequence;
    private int receiveSequence;
    private boolean hasSequence;
    private long remoteRevision;
    private float sendTimer;
    private float helloTimer;
    private float silence;
    private float connecting;
    private boolean connected;
    private int hostPort;

    enum Mode { OFFLINE, HOST, CLIENT }

    void startHost(int port) throws IOException {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Porta inválida");
        close();
        channel = DatagramChannel.open(StandardProtocolFamily.INET);
        try {
            channel.configureBlocking(false);
            channel.bind(new InetSocketAddress(port));
        } catch (IOException error) {
            close();
            throw error;
        }
        mode = Mode.HOST;
        hostPort = port;
        localNonce = ThreadLocalRandom.current().nextLong();
        message = "LAN aberta na porta " + port + " · aguardando amigo";
    }

    void connectToHost(String ip, int port) throws IOException {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Porta inválida");
        InetAddress address = ipv4(ip);
        close();
        channel = DatagramChannel.open(StandardProtocolFamily.INET);
        try {
            channel.configureBlocking(false);
            channel.bind(new InetSocketAddress(0));
        } catch (IOException error) {
            close();
            throw error;
        }
        mode = Mode.CLIENT;
        peer = new InetSocketAddress(address, port);
        localNonce = ThreadLocalRandom.current().nextLong();
        helloTimer = 0.0f;
        message = "Conectando a " + ip + ":" + port + "...";
    }

    private static InetAddress ipv4(String ip) {
        String[] fields = ip.trim().split("\\.", -1);
        if (fields.length != 4) throw new IllegalArgumentException("Digite um IPv4, por exemplo 26.12.34.56");
        byte[] octets = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (!fields[i].matches("[0-9]{1,3}")) throw new IllegalArgumentException("Endereço IP inválido");
            int value = Integer.parseInt(fields[i]);
            if (value > 255) throw new IllegalArgumentException("Endereço IP inválido");
            octets[i] = (byte) value;
        }
        try {
            return InetAddress.getByAddress(octets);
        } catch (java.net.UnknownHostException impossible) {
            throw new IllegalArgumentException("Endereço IP inválido", impossible);
        }
    }

    void update(float delta, Vector3f localPosition) {
        if (channel == null) return;
        delta = Math.clamp(delta, 0.0f, 0.1f);
        try {
            for (int i = 0; i < 32; i++) {
                receiveBuffer.clear();
                var from = channel.receive(receiveBuffer);
                if (from == null) break;
                receiveBuffer.flip();
                var packet = NetworkPacket.read(receiveBuffer);
                if (packet != null && from instanceof InetSocketAddress address) receive(address, packet);
                if (channel == null) return;
            }
            if (mode == Mode.CLIENT && !connected) {
                connecting += delta;
                helloTimer -= delta;
                if (helloTimer <= 0.0f) {
                    send(NetworkPacket.hello(localNonce), peer);
                    helloTimer = HELLO_INTERVAL;
                }
                if (connecting >= CONNECT_TIMEOUT) {
                    close();
                    message = "Sem resposta do host. Confira IP, Radmin e UDP 7777.";
                    return;
                }
            }
            if (connected) {
                silence += delta;
                if (silence >= PEER_TIMEOUT) {
                    if (mode == Mode.HOST) dropPeer();
                    else {
                        close();
                        message = "Conexão perdida com o host.";
                    }
                    return;
                }
                sendTimer -= delta;
                if (sendTimer <= 0.0f) {
                    send(NetworkPacket.position(localNonce, sendSequence++, localPosition.x, localPosition.y,
                            localPosition.z), peer);
                    sendTimer = SEND_INTERVAL;
                }
            }
        } catch (IOException error) {
            close();
            message = "Falha na rede: " + error.getMessage();
        }
    }

    private void receive(InetSocketAddress from, NetworkPacket.Message packet) throws IOException {
        if (mode == Mode.HOST && packet.type() == NetworkPacket.HELLO) {
            if (peer == null || samePeer(from, peer) && packet.nonce() == peerNonce) {
                peer = from;
                peerNonce = packet.nonce();
                connected = true;
                silence = 0.0f;
                sendTimer = 0.0f;
                if (remoteRevision == 0) hasSequence = false;
                message = "Amigo conectado · LAN na porta " + hostPort;
                send(NetworkPacket.welcome(peerNonce, localNonce), peer);
            } else {
                send(NetworkPacket.busy(), from);
            }
            return;
        }
        if (peer == null || !samePeer(from, peer)) return;
        if (mode == Mode.CLIENT && packet.type() == NetworkPacket.WELCOME && packet.nonce() == localNonce) {
            peerNonce = packet.otherNonce();
            connected = true;
            silence = 0.0f;
            sendTimer = 0.0f;
            hasSequence = false;
            message = "Conectado ao host";
            return;
        }
        if (mode == Mode.CLIENT && packet.type() == NetworkPacket.BUSY && !connected) {
            close();
            message = "Este mundo já tem dois jogadores.";
            return;
        }
        if (!connected || packet.nonce() != peerNonce) return;
        if (packet.type() == NetworkPacket.GOODBYE) {
            if (mode == Mode.HOST) dropPeer();
            else {
                close();
                message = "O host encerrou a sessão.";
            }
        } else if (packet.type() == NetworkPacket.POSITION
                && (!hasSequence || Integer.compareUnsigned(packet.sequence(), receiveSequence) > 0)) {
            receiveSequence = packet.sequence();
            hasSequence = true;
            remotePosition.set(packet.x(), packet.y(), packet.z());
            remoteRevision++;
            silence = 0.0f;
        }
    }

    private static boolean samePeer(InetSocketAddress a, InetSocketAddress b) {
        return a.getPort() == b.getPort() && a.getAddress().equals(b.getAddress());
    }

    private void send(ByteBuffer bytes, InetSocketAddress address) throws IOException {
        if (channel != null && address != null) channel.send(bytes, address);
    }

    private void dropPeer() {
        peer = null;
        peerNonce = 0L;
        receiveSequence = 0;
        hasSequence = false;
        remoteRevision = 0;
        connected = false;
        message = "LAN aberta na porta " + hostPort + " · aguardando amigo";
    }

    Mode mode() { return mode; }
    boolean connected() { return connected; }
    boolean hasRemote() { return connected && remoteRevision > 0; }
    Vector3f remotePosition() { return remotePosition; }
    long remoteRevision() { return remoteRevision; }
    String message() { return message; }

    @Override public void close() {
        if (channel != null) {
            if (connected && peer != null) {
                try { send(NetworkPacket.goodbye(localNonce), peer); }
                catch (IOException ignored) { }
            }
            try { channel.close(); }
            catch (IOException ignored) { }
        }
        channel = null;
        peer = null;
        connected = false;
        mode = Mode.OFFLINE;
        remoteRevision = 0;
        receiveSequence = 0;
        hasSequence = false;
        sendSequence = 0;
        silence = 0.0f;
        connecting = 0.0f;
        message = "";
    }
}
