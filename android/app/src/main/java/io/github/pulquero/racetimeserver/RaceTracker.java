package io.github.pulquero.racetimeserver;

import android.content.Context;
import android.os.ParcelUuid;

import com.polidea.rxandroidble2.NotificationSetupMode;
import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;
import com.polidea.rxandroidble2.exceptions.BleException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.functions.Predicate;
import io.reactivex.internal.functions.Functions;
import io.reactivex.schedulers.Schedulers;

public class RaceTracker {
    public static final ParcelUuid SERVICE_UUID = ParcelUuid.fromString("0000FFF0-0000-1000-8000-00805F9B34FB");
    private static final UUID WRITE_UUID = createUUID16("FFF1");
    private static final UUID READ_UUID = createUUID16("FFF2");
    private static final int MAX_DATA_SIZE = 20;
    private static final long WAIT_DELAY = 300L;
    private static final int RETRIES = 3;
    private static final int MAX_PILOTS = 8;

    private static final String BATTERY = "B";
    /**
     * F ${timeoutSecs}
     */
    private static final String TIMEOUT = "F";
    /**
     * G
     * Cal in-progress -> Calibrated
     */
    private static final String CALIBRATION = "G";
    private static final String NAME = "I";
    private static final String GATE_DRIVERS = "L";
    private static final String RACE_MODE = "M";
    /**
     * Racers: ${count}
     * N ${pilot} ${bandChannel}
     */
    private static final String PILOTS = "N";
    private static final String PILOTS_RESPONSE = "Racers";
    /**
     * ${bandChannel},${rssi},${x}
     */
    private static final String RSSI = "Q";
    /**
     * R
     * Total Rounds:${lapCount}
     * R ${pilot}
     * Total Rounds:${pilotLaps} P${pilot}
     */
    private static final String ROUNDS = "R";
    private static final String TIME_LOG = "T";
    /**
     * Don't use - irreversible.
     */
    private static final String FACTORY_RESET = "Y";
    /**
     * Z ${index}
     * ${index} = 1..12, 17-24
     * 25 = Pilot 1 band-channel
     * 32 = Pilot 8 band-channel
     */
    private static final String CONFIG = "Z";
    private static final int Z_MIN_LAP_TIME_INDEX = 13;
    private static final int Z_PILOT_FREQ_INDEX = 25;

    private static final String STOP_RACE = "0";
    // single pilot
    // R${lap},T${lapTime},${time}
    // multi-pilot
    // P${pilot}R${lap}T${lapTime},${time}
    public static final int SHOTGUN_RACE = 1;
    public static final int FLYOVER_RACE = 2;
    private static final String START_RACE3 = "3";
    private static final String READY = "READY";

    private static final String BAND_A = "A";
    private static final String BAND_B = "B";
    /**
     * Raceband.
     */
    private static final String BAND_C = "C";
    private static final String BAND_E = "E";
    private static final String BAND_F = "F";

    private static final String UNASSIGNED_PILOT = "FF";

    private static final short[] BAND_A_FREQS = {5865, 5845, 5825, 5805, 5785, 5765, 5745, 5725};
    private static final short[] BAND_B_FREQS = {5733, 5752, 5771, 5790, 5809, 5828, 5847, 5866};
    private static final short[] BAND_C_FREQS = {5658, 5695, 5732, 5769, 5806, 5843, 5880, 5917};
    private static final short[] BAND_E_FREQS = {5705, 5685, 5665, 5645, 5885, 5905, 5925, 5945};
    private static final short[] BAND_F_FREQS = {5740, 5760, 5780, 5800, 5820, 5840, 5860, 5880};

    private static volatile RxBleClient rxBleClient;

    public static RxBleClient getRxBleClient(Context appContext) {
        if(rxBleClient == null) {
            synchronized (RaceTracker.class) {
                if(rxBleClient == null) {
                    rxBleClient = RxBleClient.create(appContext);
                }
            }
        }
        return rxBleClient;
    }



    private final short[] pilotFreqs = new short[MAX_PILOTS];
    private final RxBleDevice device;
    private int pilotCount;

    public RaceTracker(Context appContext, String btAddress) {
        this.device = getRxBleClient(appContext).getBleDevice(btAddress);
    }

    public String getAddress() {
        return device.getMacAddress();
    }

    public Observable<RxBleConnection.RxBleConnectionState> observeConnectionState() {
        return device.observeConnectionStateChanges();
    }

    public RxBleConnection.RxBleConnectionState getConnectionState() {
        return device.getConnectionState();
    }

    public Observable<String> sendAndObserve(String cmd) {
        if(cmd.length() + 1 > MAX_DATA_SIZE) { // including null terminator
            throw new IllegalArgumentException("Invalid command - too long");
        }

        if(cmd.startsWith(PILOTS) && cmd.length() > 1) {
            pilotCount = 0;
            Arrays.fill(pilotFreqs, (short) 0);
        }

        return device.establishConnection(false)
            .subscribeOn(Schedulers.io())
            .doOnNext(RaceTracker::waitUntilDeviceReady)
            .flatMapSingle(
                    conn -> conn.writeCharacteristic(WRITE_UUID, toBytes(cmd))
                                .doOnSuccess(RaceTracker::waitUntilDeviceReady)
                                .flatMap(writtenSZ -> conn.readCharacteristic(READ_UUID))
                    )
            .map(RaceTracker::toString)
            .observeOn(Schedulers.io());
    }

    public String send(String cmd) {
        return sendAndObserve(cmd).blockingFirst();
    }

    private String send(String cmd, Predicate<String> isExpectedResponse) {
        BleException exception = null;
        for(int i=0; i<RETRIES; i++) {
            try {
                String result = send(cmd);
                if (isExpectedResponse.test(result)) {
                    return result;
                }
            } catch(BleException ex) {
                exception = ex;
            } catch(RuntimeException ex) {
                throw ex;
            } catch(Exception ex) {
                throw new AssertionError(ex);
            }
        }

        if(exception != null) {
            throw exception;
        } else {
            throw new BleException(String.format("Failed to properly read '%s' after %d retries", cmd, RETRIES));
        }
    }

    private String readValue(String cmd, String expectedResponse) {
        String result = send(cmd, read -> {
            int pos = read.indexOf(':');
            String returnedResponse = read.substring(0, pos);
            return expectedResponse.equals(returnedResponse);
        });
        int pos = result.indexOf(':');
        return result.substring(pos + 1).trim();
    }

    public float getRssi() {
        String result = send(RSSI);
        String[] parts = result.split(",", -1);
        return Float.parseFloat(parts[1].substring(0, parts[1].length()-"dbm".length()));
    }

    public int getPilotCount() {
        if(pilotCount == 0) {
            String value = readValue(PILOTS, PILOTS_RESPONSE);
            pilotCount = Integer.parseInt(value);
        }
        return pilotCount;
    }

    public void setPilotFrequency(int pilotIndex, int freq) {
        String bandChannel = null;
        if(freq != 0) {
            short[][] table = {BAND_C_FREQS, BAND_A_FREQS, BAND_B_FREQS, BAND_E_FREQS, BAND_F_FREQS};
            String[] bands = {BAND_C, BAND_A, BAND_B, BAND_E, BAND_F};
            find: for(int i=0; i<table.length; i++) {
                short[] freqs = table[i];
                for(int j=0; j<freqs.length; j++) {
                    if(freqs[j] == freq) {
                        bandChannel = bands[i] + (j+1);
                        break find;
                    }
                }
            }
        } else {
            bandChannel = UNASSIGNED_PILOT;
        }

        if(bandChannel != null) {
            send(PILOTS + " " + (pilotIndex+1) + " " + bandChannel);
        }
    }

    public int getPilotFrequency(int pilotIndex) {
        if(pilotFreqs[pilotIndex] == 0) {
            String bandChannel = getConfig(Z_PILOT_FREQ_INDEX + pilotIndex);
            String band = bandChannel.substring(0, 1);
            int channelIndex = Integer.parseInt(bandChannel.substring(1, 2)) - 1;
            short freq;
            switch (band) {
                case BAND_C:
                    freq = BAND_C_FREQS[channelIndex];
                    break;
                case BAND_A:
                    freq = BAND_A_FREQS[channelIndex];
                    break;
                case BAND_B:
                    freq = BAND_B_FREQS[channelIndex];
                    break;
                case BAND_E:
                    freq = BAND_E_FREQS[channelIndex];
                    break;
                case BAND_F:
                    freq = BAND_F_FREQS[channelIndex];
                    break;
                default:
                    freq = 0;
            }
            pilotFreqs[pilotIndex] = freq;
        }
        return pilotFreqs[pilotIndex];
    }

    private String getConfig(int index) {
        return readValue(CONFIG + " " + index, String.valueOf(index));
    }

    public void stopRace() {
        send(STOP_RACE);
    }

    public Observable<LapNotification> startRace(int mode) {
        send(String.valueOf(mode), read -> READY.equals(read));
        try {
            RaceTracker.waitUntilDeviceReady(null);
        } catch (InterruptedException e) {
        }

        return device.establishConnection(false)
                .subscribeOn(Schedulers.io())
                .doOnNext(RaceTracker::waitUntilDeviceReady)
                .flatMap(conn -> conn.setupNotification(READ_UUID, NotificationSetupMode.COMPAT))
                .flatMap(Functions.identity())
                .map(RaceTracker::toString)
                .map(s -> {
                    String[] parts = s.split(",", -1);
                    int pilotIndex;
                    long ts;
                    if(parts.length == 3) {
                        pilotIndex = 0;
                        ts = Long.parseLong(parts[2]);
                    } else {
                        pilotIndex = Integer.parseInt(parts[0].substring(1, 2)) - 1;
                        ts = Long.parseLong(parts[1]);
                    }
                    return new LapNotification(pilotIndex, ts);
                })
                .observeOn(Schedulers.io());
    }

    private static String toString(byte[] sz) {
        // strip null terminator
        int endPos = 0;
        while(sz[endPos] != 0) {
            endPos++;
        }
        return new String(sz, 0, endPos, StandardCharsets.US_ASCII);
    }

    private static byte[] toBytes(String str) {
        byte[] s = str.getBytes(StandardCharsets.US_ASCII);
        // add null terminator
        byte[] sz = new byte[MAX_DATA_SIZE];
        System.arraycopy(s, 0, sz, 0, s.length);
        return sz;
    }

    private static void waitUntilDeviceReady(Object o) throws InterruptedException {
        Thread.sleep(WAIT_DELAY);
    }

    private static UUID createUUID16(String s) {
        return UUID.fromString("0000"+s+"-0000-1000-8000-00805F9B34FB");
    }

    static final class LapNotification {
        final int pilot;
        final long ts;

        LapNotification(int pilot, long ts) {
            this.pilot = pilot;
            this.ts = ts;
        }
    }
}
