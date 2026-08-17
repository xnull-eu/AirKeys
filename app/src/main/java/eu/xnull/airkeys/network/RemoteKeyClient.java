package eu.xnull.airkeys.network;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

public class RemoteKeyClient extends WebSocketClient {
    private final WebSocketListener listener;

    public interface WebSocketListener {
        void onConnected(RemoteKeyClient client);
        void onDisconnected(RemoteKeyClient client);
        void onError(RemoteKeyClient client, Exception ex);
        void onMessage(RemoteKeyClient client, String message);
    }

    public RemoteKeyClient(URI serverUri, WebSocketListener listener) {
        super(serverUri);
        this.listener = listener;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        if (listener != null) {
            listener.onConnected(this);
        }
    }

    @Override
    public void onMessage(String message) {
        if (listener != null) {
            listener.onMessage(this, message);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (listener != null) {
            listener.onDisconnected(this);
        }
    }

    @Override
    public void onError(Exception ex) {
        if (listener != null) {
            listener.onError(this, ex);
        }
    }
}
