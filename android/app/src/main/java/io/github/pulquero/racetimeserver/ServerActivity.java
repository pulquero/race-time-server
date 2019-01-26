package io.github.pulquero.racetimeserver;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.polidea.rxandroidble2.RxBleConnection;

import androidx.appcompat.app.AppCompatActivity;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

public class ServerActivity extends AppCompatActivity {
    static final String MAC_ADDRESS_EXTRA = "macAddress";
    private static final String LOG_TAG = "ServerActivity";

    @BindView(R.id.bluetoothAddress)
    TextView bluetoothAddressView;
    @BindView(R.id.networkAddress)
    TextView networkAddressView;
    @BindView(R.id.server)
    Button serverToggleButton;
    @BindView(R.id.command)
    EditText commandText;
    @BindView(R.id.send)
    Button sendButton;
    @BindView(R.id.response)
    TextView responseView;

    private Intent raceTimeServiceIntent;
    private RaceTimeService raceTimeService;
    private Disposable btConnStateDisposable;
    private Disposable netStateDisposable;
    private Disposable commandDisposable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);
        ButterKnife.bind(this);
        raceTimeServiceIntent = new Intent(getApplicationContext(), RaceTimeService.class);
        networkAddressView.setText(TimingServer.getNetworkAddress());
    }

    protected void onStart() {
        super.onStart();
        bindService(raceTimeServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE | Context.BIND_ABOVE_CLIENT | Context.BIND_IMPORTANT);
    }

    protected void onStop() {
        super.onStop();
        if(commandDisposable != null) {
            commandDisposable.dispose();
            commandDisposable = null;
        }

        stopMonitoringConnectionState();
        if(!raceTimeService.inUse()) {
            raceTimeService.disconnect();
        }
        raceTimeService = null;

        unbindService(serviceConnection);
    }

    @OnClick(R.id.server)
    public void onServerToggleClick() {
        serverToggleButton.setEnabled(false);
        if(raceTimeService.inUse()) {
            raceTimeService.getTimingServer().stop();
            stopMonitoringConnectionState();
            stopService(raceTimeServiceIntent);
            raceTimeService.restartTimingService();
            updateNetworkAddressView(raceTimeService.getTimingServer().getState(), null);
            updateServerToggleButton();
            startMonitoringConnectionState();
        } else {
            startService(raceTimeServiceIntent);
            raceTimeService.getTimingServer().start();
        }
    }

    @OnClick(R.id.send)
    public void onSendClick() {
        String cmd = commandText.getText().toString();
        if(!cmd.isEmpty()) {
            commandText.setEnabled(false);
            sendButton.setEnabled(false);
            responseView.setText("");
            commandDisposable = raceTimeService.getRaceTracker().sendAndObserve(commandText.getText().toString())
                    .observeOn(AndroidSchedulers.mainThread())
                    .firstOrError()
                    .doFinally(() -> {
                        commandDisposable.dispose();
                        commandDisposable = null;
                        commandText.setEnabled(true);
                        sendButton.setEnabled(true);
                    })
                    .subscribe(result -> {
                        responseView.setTextColor(Color.GRAY);
                        responseView.setText(result);
                    },
                    exception -> {
                        responseView.setTextColor(Color.RED);
                        responseView.setText(exception.getMessage());
                    });
        }
    }

    private void startMonitoringConnectionState() {
        btConnStateDisposable = raceTimeService.getRaceTracker().observeConnectionState()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> updateBluetoothAddressView(state, null),
                        ex -> {
                            Log.e(LOG_TAG, "Bluetooth connection status", ex);
                            updateBluetoothAddressView(null, ex);
                        });
        netStateDisposable = raceTimeService.getTimingServer().observeState()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                            updateNetworkAddressView(state,null);
                            updateServerToggleButton();
                        },
                        ex -> {
                            Log.e(LOG_TAG, "Network connection status", ex);
                            updateNetworkAddressView(null, ex);
                        });
    }

    private void updateBluetoothAddressView(RxBleConnection.RxBleConnectionState state, Throwable err) {
        bluetoothAddressView.setText(raceTimeService.getRaceTracker().getAddress());
        if(err != null) {
            bluetoothAddressView.setTextColor(Color.RED);
        } else {
            int color;
            switch (state) {
                case CONNECTING:
                    color = 0xFF008800;
                    break;
                case CONNECTED:
                    color = Color.GREEN;
                    break;
                case DISCONNECTING:
                    color = Color.GRAY;
                    break;
                default:
                    color = Color.BLACK;
            }
            bluetoothAddressView.setTextColor(color);
        }
    }

    private void updateNetworkAddressView(TimingServer.State state, Throwable err) {
        if(err != null) {
            networkAddressView.setTextColor(Color.RED);
        } else {
            int color;
            switch (state) {
                case STARTED:
                    color = 0xFF008800;
                    break;
                case CONNECTED:
                    color = Color.GREEN;
                    break;
                default:
                    color = Color.BLACK;
            }
            networkAddressView.setTextColor(color);
        }
    }

    private void updateServerToggleButton() {
        serverToggleButton.setEnabled(true);
        serverToggleButton.setText(raceTimeService.inUse() ? R.string.stopServer : R.string.startServer);
    }

    private void stopMonitoringConnectionState() {
        btConnStateDisposable.dispose();
        btConnStateDisposable = null;
        netStateDisposable.dispose();
        netStateDisposable = null;
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            raceTimeService = ((RaceTimeService.LocalBinder)service).getService();
            if(!raceTimeService.inUse()) {
                String btAddress = getIntent().getStringExtra(MAC_ADDRESS_EXTRA);
                raceTimeService.connect(getApplicationContext(), btAddress);
            } else {
                Toast.makeText(ServerActivity.this, R.string.isRunning, Toast.LENGTH_SHORT);
            }

            updateBluetoothAddressView(raceTimeService.getRaceTracker().getConnectionState(), null);
            updateNetworkAddressView(raceTimeService.getTimingServer().getState(), null);
            updateServerToggleButton();
            startMonitoringConnectionState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };
}
