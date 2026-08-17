package eu.xnull.airkeys;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButtonToggleGroup;

import java.net.URI;
import java.util.Locale;

import eu.xnull.airkeys.network.RemoteKeyClient;
import eu.xnull.airkeys.network.ServerDiscovery;

public class MainActivity extends AppCompatActivity
        implements ServerDiscovery.DiscoveryListener, RemoteKeyClient.WebSocketListener {

    private static final long PING_INTERVAL_MS = 15_000L;
    private static final long RECONNECT_BASE_DELAY_MS = 2_000L;
    private static final long RECONNECT_MAX_DELAY_MS = 30_000L;

    private TextView statusText;
    private LinearLayout deviceList;
    private GridLayout keypadLayout;
    private Button scanButton;
    private Button disconnectButton;

    private ServerDiscovery serverDiscovery;
    private RemoteKeyClient webSocketClient;
    private NsdManager nsdManager;
    private MaterialButtonToggleGroup keypadToggle;

    private boolean isNumpadMode = true;
    private boolean isScanning = false;

    private URI serverUri;
    private String serverName;
    private boolean intentionalDisconnect = false;
    private int reconnectAttempts = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable pingRunnable = new Runnable() {
        @Override
        public void run() {
            RemoteKeyClient client = webSocketClient;
            if (client != null && client.isOpen()) {
                try {
                    client.sendPing();
                } catch (Exception ignored) {
                }
                handler.postDelayed(this, PING_INTERVAL_MS);
            }
        }
    };

    private final Runnable reconnectRunnable = new Runnable() {
        @Override
        public void run() {
            if (!intentionalDisconnect && serverUri != null) {
                openConnection(serverUri);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        deviceList = findViewById(R.id.deviceList);
        keypadLayout = findViewById(R.id.keypadLayout);
        scanButton = findViewById(R.id.scanButton);
        disconnectButton = findViewById(R.id.disconnectButton);

        nsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);
        serverDiscovery = new ServerDiscovery(this, this);

        keypadToggle = findViewById(R.id.keypadToggle);
        keypadToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                isNumpadMode = checkedId == R.id.numpadToggle;
                setupKeypad();
            }
        });
        keypadToggle.setSingleSelection(true);

        setupKeypad();
        setupScanButton();
        setupDisconnectButton();
        applyEdgeToEdgeInsets();

        if (savedInstanceState != null) {
            isNumpadMode = savedInstanceState.getBoolean("isNumpadMode", true);
            isScanning = savedInstanceState.getBoolean("isScanning", false);
            serverName = savedInstanceState.getString("serverName");
            String savedUri = savedInstanceState.getString("serverUri");
            if (savedUri != null) {
                serverUri = URI.create(savedUri);
            }
            keypadToggle.check(isNumpadMode ? R.id.numpadToggle : R.id.functionToggle);
            setupKeypad();
            if (isScanning) {
                serverDiscovery.startDiscovery();
            } else if (serverUri != null) {
                statusText.setText(R.string.status_connecting);
                openConnection(serverUri);
            }
        } else {
            keypadToggle.check(R.id.numpadToggle);
        }
    }

    private void applyEdgeToEdgeInsets() {
        final int basePadding = getResources().getDimensionPixelSize(R.dimen.screen_padding);
        final View root = findViewById(R.id.rootLayout);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(basePadding + bars.left, basePadding + bars.top,
                    basePadding + bars.right, basePadding + bars.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void setupKeypad() {
        keypadLayout.removeAllViews();
        keypadLayout.setColumnCount(4);
        if (isNumpadMode) {
            setupNumpad();
        } else {
            setupFunctionKeys();
        }
    }

    private void setupNumpad() {
        String[][] numpadLayout = {
                {"7", "NUM_7"}, {"8", "NUM_8"}, {"9", "NUM_9"}, {"/", "NUM_DIV"},
                {"4", "NUM_4"}, {"5", "NUM_5"}, {"6", "NUM_6"}, {"*", "NUM_MUL"},
                {"1", "NUM_1"}, {"2", "NUM_2"}, {"3", "NUM_3"}, {"-", "NUM_SUB"},
                {"0", "NUM_0"}, {".", "NUM_DOT"}, {"ENTER", "NUM_ENTER"}, {"+", "NUM_ADD"}
        };
        for (int i = 0; i < numpadLayout.length; i++) {
            addKeyButton(numpadLayout[i][0], numpadLayout[i][1], i % 4, i / 4);
        }
    }

    private void setupFunctionKeys() {
        for (int i = 0; i < 12; i++) {
            addKeyButton("F" + (i + 1), "F" + (i + 1), i % 4, i / 4);
        }
    }

    private void addKeyButton(String label, String command, int column, int row) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(16);
        button.setMinHeight(getResources().getDimensionPixelSize(R.dimen.key_button_height));
        button.setBackgroundResource(R.drawable.key_button_background);
        button.setOnClickListener(v -> sendKey(command));

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = 0;
        params.columnSpec = GridLayout.spec(column, 1, 1f);
        params.rowSpec = GridLayout.spec(row, 1, 1f);
        params.setGravity(Gravity.FILL);
        params.setMargins(4, 4, 4, 4);

        button.setLayoutParams(params);
        keypadLayout.addView(button);
    }

    private void setupScanButton() {
        scanButton.setOnClickListener(v -> {
            if (isScanning) {
                serverDiscovery.stopDiscovery();
                isScanning = false;
                scanButton.setText(R.string.scan_for_devices);
            } else {
                deviceList.removeAllViews();
                serverDiscovery.startDiscovery();
                isScanning = true;
                scanButton.setText(R.string.stop_scanning);
            }
        });
    }

    private void setupDisconnectButton() {
        disconnectButton.setOnClickListener(v -> disconnect());
    }

    private void sendKey(String key) {
        RemoteKeyClient client = webSocketClient;
        if (client != null && client.isOpen()) {
            client.send(key);
        } else {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnect() {
        intentionalDisconnect = true;
        reconnectAttempts = 0;
        serverUri = null;
        serverName = null;
        stopHeartbeat();
        handler.removeCallbacks(reconnectRunnable);
        if (webSocketClient != null) {
            try {
                webSocketClient.close();
            } catch (Exception ignored) {
            }
            webSocketClient = null;
        }
        disconnectButton.setVisibility(View.GONE);
        statusText.setText(R.string.status_disconnected);
    }

    private void openConnection(URI uri) {
        if (webSocketClient != null) {
            try {
                webSocketClient.close();
            } catch (Exception ignored) {
            }
        }
        statusText.setText(R.string.status_connecting);
        RemoteKeyClient client = new RemoteKeyClient(uri, this);
        webSocketClient = client;
        try {
            client.connect();
        } catch (Exception e) {
            onError(client, e);
        }
    }

    private void startHeartbeat() {
        handler.removeCallbacks(pingRunnable);
        handler.postDelayed(pingRunnable, PING_INTERVAL_MS);
    }

    private void stopHeartbeat() {
        handler.removeCallbacks(pingRunnable);
    }

    private void scheduleReconnect() {
        handler.removeCallbacks(reconnectRunnable);
        int shift = Math.min(reconnectAttempts, 4);
        long delay = Math.min(RECONNECT_BASE_DELAY_MS * (1L << shift), RECONNECT_MAX_DELAY_MS);
        reconnectAttempts++;
        handler.postDelayed(reconnectRunnable, delay);
    }

    @Override
    public void onServerFound(NsdServiceInfo serviceInfo) {
        runOnUiThread(() -> {
            Button deviceButton = new Button(this);
            deviceButton.setText(serviceInfo.getServiceName());
            deviceButton.setOnClickListener(v -> resolveAndConnect(serviceInfo));
            deviceList.addView(deviceButton);
        });
    }

    @Override
    public void onServerLost(NsdServiceInfo serviceInfo) {
        runOnUiThread(() -> {
            for (int i = 0; i < deviceList.getChildCount(); i++) {
                Button button = (Button) deviceList.getChildAt(i);
                if (button.getText().equals(serviceInfo.getServiceName())) {
                    deviceList.removeViewAt(i);
                    break;
                }
            }
        });
    }

    @Override
    public void onDiscoveryStarted() {
        runOnUiThread(() -> {
            statusText.setText(R.string.scanning_devices);
            isScanning = true;
            scanButton.setText(R.string.stop_scanning);
        });
    }

    @Override
    public void onDiscoveryStopped() {
        runOnUiThread(() -> {
            isScanning = false;
            scanButton.setText(R.string.scan_for_devices);
            if (webSocketClient == null || !webSocketClient.isOpen()) {
                statusText.setText(R.string.status_disconnected);
            }
        });
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> Toast.makeText(this, error, Toast.LENGTH_SHORT).show());
    }

    private void resolveAndConnect(NsdServiceInfo serviceInfo) {
        statusText.setText(R.string.resolving_service);
        nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this,
                            getString(R.string.resolve_error, errorCode),
                            Toast.LENGTH_SHORT).show();
                    statusText.setText(R.string.status_disconnected);
                });
            }

            @Override
            public void onServiceResolved(NsdServiceInfo resolvedService) {
                runOnUiThread(() -> connectToServer(resolvedService));
            }
        });
    }

    private void connectToServer(NsdServiceInfo serviceInfo) {
        try {
            if (serviceInfo.getHost() == null) {
                Toast.makeText(this, R.string.invalid_host, Toast.LENGTH_SHORT).show();
                return;
            }
            serverName = serviceInfo.getServiceName();
            serverUri = new URI(String.format(Locale.US, "ws://%s:%d",
                    serviceInfo.getHost().getHostAddress(),
                    serviceInfo.getPort()));
            intentionalDisconnect = false;
            reconnectAttempts = 0;
            openConnection(serverUri);
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.connection_error, e.getMessage()),
                    Toast.LENGTH_SHORT).show();
            statusText.setText(R.string.status_disconnected);
        }
    }

    @Override
    public void onConnected(RemoteKeyClient client) {
        runOnUiThread(() -> {
            if (client != webSocketClient) {
                return;
            }
            reconnectAttempts = 0;
            statusText.setText(serverName != null
                    ? getString(R.string.status_connected_to, serverName)
                    : getString(R.string.status_connected));
            disconnectButton.setVisibility(View.VISIBLE);
            startHeartbeat();
        });
    }

    @Override
    public void onDisconnected(RemoteKeyClient client) {
        runOnUiThread(() -> {
            if (client != webSocketClient) {
                return;
            }
            stopHeartbeat();
            if (!intentionalDisconnect && serverUri != null) {
                statusText.setText(R.string.status_reconnecting);
                disconnectButton.setVisibility(View.GONE);
                scheduleReconnect();
            } else if (!intentionalDisconnect) {
                statusText.setText(R.string.status_disconnected);
                disconnectButton.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onError(RemoteKeyClient client, Exception ex) {
        runOnUiThread(() -> {
            if (client != webSocketClient) {
                return;
            }
            stopHeartbeat();
            if (!intentionalDisconnect && serverUri != null) {
                if (reconnectAttempts == 0) {
                    Toast.makeText(this, R.string.connection_lost_retrying, Toast.LENGTH_SHORT).show();
                }
                statusText.setText(R.string.status_reconnecting);
                disconnectButton.setVisibility(View.GONE);
                scheduleReconnect();
            } else if (!intentionalDisconnect) {
                Toast.makeText(this, "Error: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                statusText.setText(R.string.status_disconnected);
            }
        });
    }

    @Override
    public void onMessage(RemoteKeyClient client, String message) {
        // No incoming messages are expected from the server yet.
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isNumpadMode", isNumpadMode);
        outState.putBoolean("isScanning", isScanning);
        outState.putString("serverName", serverName);
        if (serverUri != null) {
            outState.putString("serverUri", serverUri.toString());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        intentionalDisconnect = true;
        handler.removeCallbacks(reconnectRunnable);
        handler.removeCallbacks(pingRunnable);
        if (webSocketClient != null) {
            try {
                webSocketClient.close();
            } catch (Exception ignored) {
            }
            webSocketClient = null;
        }
        serverDiscovery.stopDiscovery();
    }
}
