package io.github.pulquero.racetimeserver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import com.jakewharton.rxrelay2.BehaviorRelay;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

public class ServerActivity extends AppCompatActivity implements RaceTimeServiceManager {
    static final String MAC_ADDRESS_EXTRA = "macAddress";

    private final BehaviorRelay<RaceTimeService> serviceSubject = BehaviorRelay.create();
    private Intent raceTimeServiceIntent;
    private RaceTimeService raceTimeService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        raceTimeServiceIntent = new Intent(getApplicationContext(), RaceTimeService.class);
        setContentView(R.layout.activity_server);
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        if(fragment instanceof RaceTimeServiceSubscriber) {
            ((RaceTimeServiceSubscriber)fragment).subscribeToRaceTimeService(serviceSubject);
            ((RaceTimeServiceSubscriber)fragment).setRaceTimeServiceManager(this);
        }
    }

    public void onStart() {
        super.onStart();
        bindService(raceTimeServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE | Context.BIND_ABOVE_CLIENT | Context.BIND_IMPORTANT);
    }

    public void onStop() {
        super.onStop();

        if(!raceTimeService.inUse()) {
            raceTimeService.disconnect();
        }
        raceTimeService = null;

        unbindService(serviceConnection);
    }

    @Override
    public void keepRunningInBackground() {
        startService(raceTimeServiceIntent);
    }

    @Override
    public void stopFromRunningInBackground() {
        stopService(raceTimeServiceIntent);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            raceTimeService = ((RaceTimeService.LocalBinder)service).getService();
            if(!raceTimeService.inUse()) {
                String btAddress = getIntent().getStringExtra(ServerActivity.MAC_ADDRESS_EXTRA);
                raceTimeService.connect(getApplicationContext(), btAddress);
            } else {
                Toast.makeText(ServerActivity.this, R.string.isRunning, Toast.LENGTH_SHORT);
            }
            serviceSubject.accept(raceTimeService);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };
}
