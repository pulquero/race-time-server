package io.github.pulquero.racetimeserver;

import android.util.Log;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

public class TimingServer extends WebSocketServer {
    private static final int PORT = 5001;
    private static final String LOG_TAG = "Timing";
    private static final int MAJOR_VERSION = 0;
    private static final int MINOR_VERSION = 1;

    private final RaceTracker raceTracker;
    private final Listener listener;
    private Timer timer;
    private TimerTask heartbeat;

    interface Listener {
        void onConnect();
        void onDisconnect();
    }

    public TimingServer(RaceTracker raceTracker, Listener listener) {
        super(new InetSocketAddress(PORT));
        this.raceTracker = raceTracker;
        this.listener = listener;
    }

    @Override
    public void start() {
        timer = new Timer();
        super.start();
    }

    public void stop() throws IOException, InterruptedException {
        super.stop();
        timer.cancel();
        timer = null;
    }

    @Override
    public void onStart() {
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        listener.onConnect();
        heartbeat = new TimerTask() {
            @Override
            public void run() {
                try {
                    sendHeartbeat(conn);
                } catch (WebsocketNotConnectedException e) {
                    cancel();
                } catch (Exception e) {
                    Log.w(LOG_TAG, "heartbeat", e);
                }
            }
        };
        timer.schedule(heartbeat, 100, 500);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        heartbeat.cancel();
        heartbeat = null;
        listener.onDisconnect();
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
            // never expected to happen
            throw new AssertionError(ex);
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
        JSONArray nodesJson = new JSONArray();
        try {
            int nodeCount = raceTracker.getPilotCount();
            for (int i = 0; i < nodeCount; i++) {
                JSONObject nodeJson = new JSONObject();
                nodeJson.put("frequency", 5800);
                nodeJson.put("trigger_rssi", 32);
                nodesJson.put(nodeJson);
            }
        } catch(Exception e) {
            Log.w(LOG_TAG,"settings", e);
        }

        JSONObject json = new JSONObject();
        json.put("nodes", nodesJson);
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
        JSONArray rssiJson = new JSONArray();
        int nodeCount = raceTracker.getPilotCount();
        for (int i = 0; i < nodeCount; i++) {
            rssiJson.put(34);
        }
        JSONObject json = new JSONObject();
        json.put("current_rssi", rssiJson);
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
        Log.e(LOG_TAG, "WebSocket error", ex);
    }
}
