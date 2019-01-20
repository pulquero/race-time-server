package io.github.pulquero.racetimeserver;

import android.util.Log;

import com.jakewharton.rxrelay2.BehaviorRelay;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import io.reactivex.Observable;

public class TimingServer extends WebSocketServer {
    private static final int PORT = 5001;
    private static final String LOG_TAG = "TimingServer";
    private static final int MAJOR_VERSION = 0;
    private static final int MINOR_VERSION = 1;

    enum State {
        STARTED, CONNECTED, STOPPED
    }

    private final BehaviorRelay<State> stateSubject = BehaviorRelay.create();
    private final RaceTracker raceTracker;
    private Timer timer;
    private TimerTask heartbeat;

    public TimingServer(RaceTracker raceTracker) {
        super(new InetSocketAddress(PORT), 1);
        this.raceTracker = raceTracker;
        stateSubject.accept(State.STOPPED);
    }

    public Observable<State> observeState() {
        return stateSubject.skip(1);
    }

    public State getState() {
        return stateSubject.getValue();
    }

    @Override
    public void start() {
        super.start();
        timer = new Timer("Timing server heartbeat",true);
    }

    public void stop() {
        try {
            super.stop();
        } catch (IOException | InterruptedException e) {
        }
        timer.cancel();
        timer = null;
        stateSubject.accept(State.STOPPED);
    }

    @Override
    public void onStart() {
        stateSubject.accept(State.STARTED);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
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
        timer.schedule(heartbeat, 5000, 2500);
        stateSubject.accept(State.CONNECTED);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        heartbeat.cancel();
        heartbeat = null;
        if(getConnections().isEmpty()) {
            stateSubject.accept(State.STARTED);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
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
                int freq = raceTracker.getPilotFrequency(i);
                JSONObject nodeJson = new JSONObject();
                nodeJson.put("frequency", freq);
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

    private void set(JSONObject json) throws JSONException {
        int node = json.optInt("node", -1);
        if(node != -1) {
            int freq = json.getInt("frequency");
            raceTracker.setPilotFrequency(node, freq);
        } else {
            for (Iterator<String> iter = json.keys(); iter.hasNext(); ) {
                String key = iter.next();
            }
        }
    }

    private void sendHeartbeat(WebSocket conn) throws JSONException {
        JSONArray rssiJson = new JSONArray();
        int nodeCount = raceTracker.getPilotCount();
        for (int i = 0; i < nodeCount; i++) {
            rssiJson.put(34);
        }
        JSONObject json = new JSONObject();
        json.put("current_rssi", rssiJson);
        String notification = createNotification("heartbeat", json);
        conn.send(notification);
    }

    public void sendPass(WebSocket conn) throws JSONException {
        JSONObject json = getTimestamp();
        json.put("node", 0);
        json.put("frequency", 5801);
        String notification = createNotification("pass_record", json);
        conn.send(notification);
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


    public static String getNetworkAddress() {
        try {
            Enumeration<NetworkInterface> intfIter = NetworkInterface.getNetworkInterfaces();
            if (intfIter == null) {
                return "No network interface - permissions?";
            }
            while (intfIter.hasMoreElements()) {
                NetworkInterface intf = intfIter.nextElement();
                if (intf.isUp() && !intf.isLoopback()) {
                    for (Enumeration<InetAddress> addrIter = intf.getInetAddresses(); addrIter.hasMoreElements(); ) {
                        InetAddress addr = addrIter.nextElement();
                        if (!addr.isLoopbackAddress() && (addr instanceof Inet4Address)) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Get network address", e);
        }
        return "No IP address";
    }
}
