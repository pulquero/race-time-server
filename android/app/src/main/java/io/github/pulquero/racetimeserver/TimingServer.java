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
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class TimingServer extends WebSocketServer {
    private static final int PORT = 5001;
    private static final String LOG_TAG = "TimingServer";
    private static final int MAJOR_VERSION = 0;
    private static final int MINOR_VERSION = 1;
    private static final String CALIBRATION_THRESHOLD = "calibration_threshold";
    private static final String CALIBRATION_OFFSET = "calibration_offset";
    private static final String TRIGGER_THRESHOLD = "trigger_threshold";
    /**
     * Extension.
     */
    private static final String MIN_LAP_TIME = "minimum_lap_time";
    private static final String TRIGGER_RSSI = "trigger_rssi";
    private static final String CURRENT_RSSI = "current_rssi";
    private static final String FREQUENCY = "frequency";
    private static final String NODE = "node";
    private static final String TIMESTAMP = "timestamp";
    private static final String FREQUENCY_SET_NOTIF = "frequency_set";
    private static final String TRIGGER_THRESHOLD_SET_NOTIF = "trigger_threshold_set";
    private static final String HEARTBEAT_NOTIF = "heartbeat";
    private static final String PASS_RECORD_NOTIF = "pass_record";

    enum State {
        STARTED, CONNECTED, STOPPED
    }

    private final BehaviorRelay<State> stateSubject = BehaviorRelay.create();
    private final RaceTracker raceTracker;
    private Timer timer;

    public TimingServer(RaceTracker raceTracker) {
        super(new InetSocketAddress(PORT), 1);
        this.raceTracker = raceTracker;
        stateSubject.accept(State.STOPPED);
    }

    public Observable<State> observeState() {
        return stateSubject.subscribeOn(Schedulers.io()).skip(1);
    }

    public State getState() {
        return stateSubject.getValue();
    }

    /**
     * Returns immediately before the server has started.
     */
    @Override
    public void start() {
        super.start();
        timer = new Timer("Timing server heartbeat",true);
    }

    /**
     * Returns when the server has stopped.
     */
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
        conn.setAttachment(new AttachmentData());
        ensureHeartbeat(conn);
        stateSubject.accept(State.CONNECTED);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        AttachmentData attachmentData = conn.getAttachment();
        if(attachmentData != null) {
            conn.setAttachment(null);
            attachmentData.stopHeartbeat();
            attachmentData.stopRace(null);
        }

        if(getConnections().isEmpty()) {
            stateSubject.accept(State.STARTED);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        try {
            synchronized (conn) {
                if (message.charAt(0) == '{') {
                    // JSON object
                    set(conn, new JSONObject(message));
                } else {
                    // 'get' command
                    JSONObject result = get(conn, message);
                    if (result != null) {
                        conn.send(result.toString());
                    }
                }
            }
        } catch(JSONException ex) {
            // never expected to happen
            throw new AssertionError(ex);
        }
    }

    private JSONObject get(WebSocket conn, String action) throws JSONException {
        switch (action) {
            case "get_version":
                ensureHeartbeat(conn);
                return getVersion();
            case "get_settings":
                ensureHeartbeat(conn);
                return getSettings();
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
        int nodeCount;
        try {
            nodeCount = raceTracker.getPilotCount();
        } catch(Exception e) {
            Log.w(LOG_TAG,"settings - pilot count", e);
            nodeCount = 0;
        }
        int triggerRssi;
        try {
            triggerRssi = raceTracker.getTriggerRssi();
        } catch (Exception e) {
            Log.w(LOG_TAG,"settings - trigger RSSI", e);
            triggerRssi = 0;
        }
        int calibrationThreshold = 0;
        int calibrationOffset = 0;

        JSONArray nodesJson = new JSONArray();

        for (int i = 0; i < nodeCount; i++) {
            int freq;
            try {
                freq = raceTracker.getPilotFrequency(i);
            } catch (Exception e) {
                Log.w(LOG_TAG,"settings - pilot frequency", e);
                freq = 0;
            }
            JSONObject nodeJson = new JSONObject();
            nodeJson.put(FREQUENCY, freq);
            nodeJson.put(TRIGGER_RSSI, triggerRssi);
            nodesJson.put(nodeJson);
        }

        JSONObject json = new JSONObject();
        json.put("nodes", nodesJson);
        json.put(CALIBRATION_THRESHOLD, calibrationThreshold);
        json.put(CALIBRATION_OFFSET, calibrationOffset);
        json.put(TRIGGER_THRESHOLD, triggerRssi);
        return json;
    }

    private JSONObject getTimestamp() throws JSONException {
        JSONObject json = new JSONObject();
        // race timer starts from 0
        json.put(TIMESTAMP, 0);
        return json;
    }

    private void set(WebSocket conn, JSONObject json) throws JSONException {
        if(json.has(NODE)) {
            int node = json.getInt(NODE);
            if(node != -1) {
                // set_frequency
                ensureHeartbeat(conn);
                int freq = json.getInt(FREQUENCY);
                raceTracker.setPilotFrequency(node, freq);
                sendNotification(conn, FREQUENCY_SET_NOTIF, json);
            } else {
                // reset_auto_calibration
                // closest thing to a start race message
                // there is nothing equivalent to a stop race message besides any other message
                AttachmentData attachmentData = conn.getAttachment();
                attachmentData.stopHeartbeat();
                attachmentData.stopRace(raceTracker);
                attachmentData.raceDisposable = raceTracker.startRace(RaceTracker.SHOTGUN_RACE).subscribe(
                    pass -> {
                        sendPass(conn, pass.pilot, pass.ts);
                    },
                    ex -> Log.e(LOG_TAG, "Lap notification", ex)
                );
            }
        } else {
            ensureHeartbeat(conn);
            for (Iterator<String> iter = json.keys(); iter.hasNext(); ) {
                String key = iter.next();
                switch(key) {
                    case CALIBRATION_THRESHOLD:
                        break;
                    case CALIBRATION_OFFSET:
                        break;
                    case TRIGGER_THRESHOLD:
                        raceTracker.setTriggerRssi(Integer.parseInt(json.getString(key)));
                        sendNotification(conn, TRIGGER_THRESHOLD_SET_NOTIF, json);
                        break;
                    case MIN_LAP_TIME:
                        raceTracker.setMinimumLapTime(Integer.parseInt(json.getString(key)));
                        break;
                }
            }
        }
    }

    private void ensureHeartbeat(WebSocket conn) {
        // ensure any previous races are stopped
        AttachmentData attachmentData = conn.getAttachment();
        attachmentData.stopRace(raceTracker);

        // start heartbeat if not already running
        if(attachmentData.heartbeat == null) {
            attachmentData.heartbeat = new HeartbeatTask(conn);
            timer.schedule(attachmentData.heartbeat, 8000L, 15000L);
        }
    }

    private void sendHeartbeat(WebSocket conn) throws JSONException {
        synchronized (conn) {
            JSONArray rssiJson = new JSONArray();
            int nodeCount = raceTracker.getPilotCount();
            for (int i = 0; i < nodeCount; i++) {
                // rssi only available for the principal channel
                if (i == 0) {
                    int rssi = raceTracker.getRssi();
                    rssiJson.put(rssi);
                } else {
                    rssiJson.put(0);
                }
            }
            JSONObject json = new JSONObject();
            json.put(CURRENT_RSSI, rssiJson);
            sendNotification(conn, HEARTBEAT_NOTIF, json);
        }
    }

    public void sendPass(WebSocket conn, int pilot, long ts) throws JSONException {
        JSONObject json = new JSONObject();
        json.put(TIMESTAMP, ts);
        json.put(NODE, pilot);
        json.put(FREQUENCY, raceTracker.getPilotFrequency(pilot));
        sendNotification(conn, PASS_RECORD_NOTIF, json);
    }

    private void sendNotification(WebSocket conn, String type, JSONObject data) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("notification", type);
        json.put("data", data);
        String notification = json.toString();
        conn.send(notification);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.e(LOG_TAG, "WebSocket error", ex);
    }

    final class HeartbeatTask extends TimerTask {
        final WebSocket conn;
        boolean isFirst = true;

        HeartbeatTask(WebSocket conn) {
            this.conn = conn;
        }

        @Override
        public void run() {
            if(isFirst) {
                raceTracker.activateVRX();
                isFirst = false;
            }
            try {
                sendHeartbeat(conn);
            } catch (WebsocketNotConnectedException e) {
                cancel();
            } catch (Exception e) {
                Log.w(LOG_TAG, "heartbeat", e);
            }
        }
    }

    static final class AttachmentData {
        HeartbeatTask heartbeat;
        Disposable raceDisposable;

        void stopHeartbeat() {
            if (heartbeat != null) {
                heartbeat.cancel();
                heartbeat = null;
            }
        }

        void stopRace(RaceTracker raceTracker) {
            if(raceDisposable != null) {
                raceDisposable.dispose();
                raceDisposable = null;
            }
            if(raceTracker != null) {
                raceTracker.stopRace();
            }
        }
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
