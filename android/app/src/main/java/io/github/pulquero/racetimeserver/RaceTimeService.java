package io.github.pulquero.racetimeserver;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public class RaceTimeService extends Service {
    private RaceTracker raceTracker;
    private TimingServer timingServer;

    public void connect(Context appContext, String btAddress) {
        raceTracker = new RaceTracker(appContext, btAddress);
        raceTracker.connect();
        timingServer = new TimingServer(raceTracker);
    }

    public RaceTracker getRaceTracker() {
        return raceTracker;
    }

    public TimingServer getTimingServer() {
        return timingServer;
    }

    public boolean inUse() {
        return timingServer != null && timingServer.getState() != TimingServer.State.STOPPED;
    }

    public void restartTimingService() {
        timingServer = null;
        timingServer = new TimingServer(raceTracker);
    }

    public void disconnect() {
        raceTracker.disconnect();
        raceTracker = null;
        timingServer = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private final IBinder binder = new LocalBinder();

    public final class LocalBinder extends Binder {
        public RaceTimeService getService() {
            return RaceTimeService.this;
        }
    }
}
