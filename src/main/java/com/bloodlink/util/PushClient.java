package com.bloodlink.util;

import javafx.application.Platform;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * The app's single WebSocket connection to the standalone {@link com.bloodlink.push.PushServer}
 * (see that class for the protocol and why it's a separate process). One
 * dashboard controller's {@code initialize()} connects and registers its own
 * {@code refreshAll()} as a listener; {@code logout()} disconnects.
 * <p>
 * Every method here is best-effort and fails silently: if the push server
 * isn't running, isn't reachable, or the connection drops, the app must
 * keep working correctly on its existing periodic poll. This class only
 * makes updates feel faster -- it is never the only path to correct state.
 */
public final class PushClient {
    private static final PushClient INSTANCE = new PushClient();

    private WebSocketClient client;
    private long currentUserId = -1;
    private final Set<Runnable> refreshListeners = new CopyOnWriteArraySet<>();

    private PushClient() { }

    public static PushClient getInstance() { return INSTANCE; }

    /** Call once after login. Safe to call again for the same already-connected user. */
    public synchronized void connect(long userId) {
        if (client != null && client.isOpen() && currentUserId == userId) return;
        disconnect();
        currentUserId = userId;
        try {
            String host = AppConfig.get("push.host");
            int port = AppConfig.getInt("push.port");
            client = new WebSocketClient(new URI("ws://" + host + ":" + port)) {
                @Override public void onOpen(ServerHandshake handshake) { send("HELLO " + userId); }

                @Override public void onMessage(String message) {
                    if ("REFRESH".equals(message)) {
                        Platform.runLater(() -> refreshListeners.forEach(Runnable::run));
                    }
                }

                @Override public void onClose(int code, String reason, boolean remote) { }

                @Override public void onError(Exception ex) { }
            };
            client.setConnectionLostTimeout(30);
            client.connect(); // async -- does not block the caller if the server is slow or absent
        } catch (URISyntaxException | IllegalArgumentException | NumberFormatException e) {
            // Missing/misconfigured push.host or push.port -- run on polling only.
            client = null;
        }
    }

    /** Fire-and-forget: asks the server to nudge another user to refresh, if they're connected. */
    public void ping(long targetUserId) {
        WebSocketClient current = client;
        if (current != null && current.isOpen()) {
            try { current.send("PING " + targetUserId); } catch (Exception e) { /* best-effort only */ }
        }
    }

    /** Registers a callback that runs on the JavaFX Application Thread whenever the server pushes a refresh nudge. */
    public void onRefresh(Runnable listener) { refreshListeners.add(listener); }

    public void removeListener(Runnable listener) { refreshListeners.remove(listener); }

    public synchronized void disconnect() {
        refreshListeners.clear();
        currentUserId = -1;
        if (client == null) return;
        WebSocketClient toClose = client;
        client = null;
        try {
            toClose.closeBlocking();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
