package io.github.pulquero.racetimeserver;

import android.content.Context;
import android.os.ParcelUuid;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleDevice;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;

public class RaceTracker {
    public static final ParcelUuid SERVICE_UUID = ParcelUuid.fromString("0000FFF0-0000-1000-8000-00805F9B34FB");
    private static final UUID WRITE_UUID = createUUID16("FFF1");
    private static final UUID READ_UUID = createUUID16("FFF2");
    private static final int MTU = 16;

    private static final String BATTERY = "B";
    /**
     * F $timeoutSecs
     */
    private static final String TIMEOUT = "F";
    /**
     * Start calibration.
     * G 1
     */
    private static final String CALIBRATION = "G";
    private static final String NAME = "I";
    private static final String GATE_DRIVERS = "L";
    private static final String RACE_MODE = "M";
    /**
     * Racers: $count
     * Z $pilot $bandChannel
     */
    private static final String PILOTS = "N";
    private static final String RSSI = "Q";
    private static final String ROUNDS = "R";
    private static final String TIME_LOG = "T";
    private static final String FACTORY_RESET = "Y";
    /**
     * Z $index
     * $index = 1..10, 15, 20
     * 25 = Pilot 1 band-channel
     * 32 = Pilot 8 band-channel
     */
    private static final String CONFIG = "Z";

    private static final String STOP_RACE = "0";
    private static final String START_RACE1 = "1";
    private static final String START_RACE2 = "2";
    private static final String START_RACE3 = "3";

    private static final String BAND_A = "A";
    private static final String BAND_B = "B";
    /**
     * Raceband.
     */
    private static final String BAND_C = "C";
    private static final String BAND_E = "E";
    private static final String BAND_F = "F";

    private static volatile RxBleClient rxBleClient;

    public static RxBleClient getRxBleClient(Context context) {
        if(rxBleClient == null) {
            synchronized (RaceTracker.class) {
                if(rxBleClient == null) {
                    rxBleClient = RxBleClient.create(context.getApplicationContext());
                }
            }
        }
        return rxBleClient;
    }



    private final RxBleDevice device;

    public RaceTracker(RxBleDevice device) {
        this.device = device;
    }

    public Observable<String> sendAndObserve(String cmd) {
        if(cmd.length() + 1 > MTU) { // including null terminator
            throw new IllegalArgumentException("Invalid command - too long");
        }

        return device.establishConnection(false)
            .subscribeOn(Schedulers.io())
            .flatMapSingle(
                    conn -> {
                        byte[] s = cmd.getBytes(StandardCharsets.US_ASCII);
                        // add null terminator
                        byte[] sz = new byte[MTU];
                        System.arraycopy(s, 0, sz, 0, s.length);
                        return conn.writeCharacteristic(WRITE_UUID, sz)
                                .flatMap(writtenSZ -> conn.readCharacteristic(READ_UUID));
                    })
            .map(readSZ -> {
                // strip null terminator
                int endPos = 0;
                while(readSZ[endPos] != 0) {
                    endPos++;
                }
                return new String(readSZ, 0, endPos, StandardCharsets.US_ASCII);
            })
            .observeOn(Schedulers.io());
    }

    public String send(String cmd) {
        return sendAndObserve(cmd).blockingFirst();
    }

    public int getRssi() {
        return 0;
    }

    public int getPilotCount() {
        String result = send(PILOTS);
        return Integer.parseInt(getValue(result));
    }

    private static String getValue(String result) {
        int pos = result.indexOf(':');
        if(pos != -1) {
            return result.substring(pos+1).trim();
        } else {
            return result;
        }
    }

    private static UUID createUUID16(String s) {
        return UUID.fromString("0000"+s+"-0000-1000-8000-00805F9B34FB");
    }
}
