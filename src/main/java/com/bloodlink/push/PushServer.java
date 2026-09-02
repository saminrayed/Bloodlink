package com.bloodlink.push;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Standalone process (has its own {@code main()}) that lets BloodLink desktop
 * clients push "you should refresh" nudges to each other through one shared
 * hub, instead of every dashboard blindly polling on a timer. Start this
 * once, alongside MySQL, before running any BloodLink client:
 *
 * <pre>mvn exec:java -Dexec.mainClass=com.bloodlink.push.PushServer</pre>
 *
 * <p>Protocol is deliberately plain text, one line per message -- every
 * message here is either "who am I" or "which user changed", nothing complex
 * enough to justify pulling in a JSON library:
 * <pre>
 * Client -&gt; Server:  HELLO &lt;userId&gt;        (sent right after connecting)
 * Client -&gt; Server:  PING &lt;targetUserId&gt;   (something changed for that user)
 * Server -&gt; Client:  REFRESH                (forwarded to that user's own connection, if open)
 * </pre>
 *
 * <p>This server holds no BloodLink state and never touches the database --
 * it only routes small text messages between already-authenticated desktop
 * clients. If a target user has no open connection, or this process isn't
 * running at all, {@link com.bloodlink.util.PushClient} fails silently and
 * every dashboard's existing periodic poll keeps the app correct regardless.
 * Nothing here is required for correctness, only for immediacy.
 */
public final class PushServer extends WebSocketServer {
    private final Map<Long, WebSocket> connectionByUserId = new ConcurrentHashMap<>();
    private final Map<WebSocket, Long> userIdByConnection = new ConcurrentHashMap<>();

    public PushServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        // Not associated with a user until this connection sends HELLO.
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Long userId = userIdByConnection.remove(conn);
        if (userId != null) connectionByUserId.remove(userId, conn);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if (message == null) return;
        String[] parts = message.trim().split("\\s+", 2);
        if (parts.length < 2) return;
        try {
            long userId = Long.parseLong(parts[1]);
            switch (parts[0]) {
                case "HELLO" -> {
                    connectionByUserId.put(userId, conn);
                    userIdByConnection.put(conn, userId);
                }
                case "PING" -> {
                    WebSocket target = connectionByUserId.get(userId);
                    if (target != null && target.isOpen()) target.send("REFRESH");
                }
                default -> { /* unrecognized command -- ignore rather than drop the connection over it */ }
            }
        } catch (NumberFormatException e) {
            // Malformed message from one client should never take the shared server down.
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[PushServer] connection error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("[PushServer] listening on port " + getPort());
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8887;
        PushServer server = new PushServer(port);
        server.setReuseAddr(true);
        server.run();
    }
}
