package eu.xnull.airkeys.network;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;

public class ServerDiscovery {
    private static final String SERVICE_TYPE = "_remotekeys._tcp.";

    private final NsdManager nsdManager;
    private final DiscoveryListener listener;
    private final WifiManager.MulticastLock multicastLock;
    private NsdManager.DiscoveryListener discoveryListener;

    public interface DiscoveryListener {
        void onServerFound(NsdServiceInfo serviceInfo);
        void onServerLost(NsdServiceInfo serviceInfo);
        void onDiscoveryStarted();
        void onDiscoveryStopped();
        void onError(String error);
    }

    public ServerDiscovery(Context context, DiscoveryListener listener) {
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.listener = listener;

        WifiManager wifiManager = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            multicastLock = wifiManager.createMulticastLock("airkeys:nsd");
            multicastLock.setReferenceCounted(false);
        } else {
            multicastLock = null;
        }

        initializeDiscoveryListener();
    }

    private void initializeDiscoveryListener() {
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                releaseMulticastLock();
                listener.onError("Discovery start failed with code: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                listener.onError("Discovery stop failed with code: " + errorCode);
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
                listener.onDiscoveryStarted();
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                listener.onDiscoveryStopped();
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (SERVICE_TYPE.equals(serviceInfo.getServiceType())) {
                    listener.onServerFound(serviceInfo);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
                listener.onServerLost(serviceInfo);
            }
        };
    }

    public void startDiscovery() {
        acquireMulticastLock();
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (Exception e) {
            releaseMulticastLock();
            listener.onError("Failed to start discovery: " + e.getMessage());
        }
    }

    public void stopDiscovery() {
        try {
            nsdManager.stopServiceDiscovery(discoveryListener);
        } catch (Exception e) {
            listener.onError("Failed to stop discovery: " + e.getMessage());
        } finally {
            releaseMulticastLock();
        }
    }

    private void acquireMulticastLock() {
        if (multicastLock != null && !multicastLock.isHeld()) {
            try {
                multicastLock.acquire();
            } catch (Exception ignored) {
            }
        }
    }

    private void releaseMulticastLock() {
        if (multicastLock != null && multicastLock.isHeld()) {
            try {
                multicastLock.release();
            } catch (Exception ignored) {
            }
        }
    }
}
