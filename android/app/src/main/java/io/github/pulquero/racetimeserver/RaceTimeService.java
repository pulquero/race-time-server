package io.github.pulquero.racetimeserver;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

public class RaceTimeService extends Service {
    public RaceTimeService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private IBinder binder = new RaceTimeInterface.Stub() {
        @Override
        public void start() throws RemoteException {

        }

        @Override
        public void stop() throws RemoteException {

        }
    };
}
