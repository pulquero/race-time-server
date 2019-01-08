package io.github.pulquero.racetimeserver;

import android.content.Context;
import android.os.Build;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.mockrxandroidble.RxBleClientMock;

public class RaceTracker {
    private static volatile RxBleClient rxBleClient;

    public static RxBleClient getRxBleClient(Context context) {
        if(rxBleClient == null) {
            synchronized (RaceTracker.class) {
                if(rxBleClient == null) {
                    if(Build.PRODUCT.contains("sdk")) {
                        rxBleClient = new RxBleClientMock.Builder()
                                .addDevice(new RxBleClientMock.DeviceBuilder()
                                        .deviceName("Test")
                                        .deviceMacAddress("00")
                                        .rssi(44)
                                        .scanRecord(new byte[0]).build()).build();
                    } else {
                        rxBleClient = RxBleClient.create(context.getApplicationContext());
                    }
                }
            }
        }
        return rxBleClient;
    }

}
