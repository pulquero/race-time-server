package io.github.pulquero.racetimeserver;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.util.Collections;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

public class LiveTime extends WebSocketServer {
    private static final int PORT = 5001;
    private static final String LOG_TAG = "LT";
    private static final int MAJOR_VERSION = 0;
    private static final int MINOR_VERSION = 1;

    private final Timer timer = new Timer();
    private TimerTask heartbeat;
    private volatile int connectionCount;

    public LiveTime() {
        super(new InetSocketAddress(PORT));
    }

    @Override
    protected boolean onConnect(SelectionKey key) {
        if(connectionCount == 0) {
            connectionCount++;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        heartbeat = new TimerTask() {
            @Override
            public void run() {
                try {
                    sendHeartbeat(conn);
                } catch (JSONException e) {
                }
            }
        };
        timer.schedule(heartbeat, 100, 500);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        heartbeat.cancel();
        heartbeat = null;
        connectionCount--;
    }

    @Override
    public synchronized void onMessage(WebSocket conn, String message) {
        try {
            if(message.charAt(0) == '{') {
                set(new JSONObject(message));
            } else {
                JSONObject result = get(message);
                if (result != null) {
                    conn.send(result.toString());
                }
            }
        } catch(JSONException ex) {
            Log.e(LOG_TAG, ex.getMessage());
        }
    }

    private JSONObject get(String action) throws JSONException {
        switch (action) {
            case "get_version": return getVersion();
            case "get_settings": return getSettings();
            case "get_timestamp": return getTimestamp();
        }
        return null;
    }

    private JSONObject getVersion() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("major", MAJOR_VERSION);
        json.put("minor", MINOR_VERSION);
        return json;
    }

    private JSONObject getSettings() throws JSONException {
        JSONObject nodeJson = new JSONObject();
        nodeJson.put("frequency", 5800);
        nodeJson.put("trigger_rssi", 32);
        JSONObject json = new JSONObject();
        json.put("nodes", Collections.singletonList(nodeJson));
        json.put("calibration_threshold", 2);
        json.put("calibration_offset", 3);
        json.put("trigger_threshold", 4);
        return json;
    }

    private JSONObject getTimestamp() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("timestamp", System.currentTimeMillis());
        return json;
    }

    private void set(JSONObject json) {
        for(Iterator<String> iter = json.keys(); iter.hasNext(); ) {
            String key = iter.next();
        }
    }

    private synchronized void sendHeartbeat(WebSocket conn) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("current_rssi", Collections.singletonList(34));
        String notif = createNotification("heartbeat", json);
        conn.send(notif);
    }

    public synchronized void sendPass(WebSocket conn) throws JSONException {
        JSONObject json = getTimestamp();
        json.put("node", 0);
        json.put("frequency", 5801);
        String notif = createNotification("pass_record", json);
        conn.send(notif);
    }

    private String createNotification(String type, JSONObject data) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("notification", type);
        json.put("data", data);
        return json.toString();
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.e(LOG_TAG, ex.getMessage());
    }
}
