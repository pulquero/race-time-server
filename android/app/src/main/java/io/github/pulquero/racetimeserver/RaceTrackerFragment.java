package io.github.pulquero.racetimeserver;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.polidea.rxandroidble2.RxBleConnection;

import androidx.fragment.app.Fragment;
import butterknife.BindColor;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

public class RaceTrackerFragment extends Fragment implements RaceTimeServiceSubscriber {

    private static final String LOG_TAG = "RaceTrackerFragment";

    @BindColor(R.color.connected)
    int connectedColor;
    @BindColor(R.color.disconnected)
    int disconnectedColor;
    @BindColor(R.color.connecting)
    int connectingColor;
    @BindColor(R.color.disconnecting)
    int disconnectingColor;
    @BindColor(R.color.responseText)
    int responseTextColor;
    @BindColor(R.color.error)
    int errorColor;
    @BindView(R.id.bluetoothAddress)
    TextView bluetoothAddressView;
    @BindView(R.id.command)
    EditText commandText;
    @BindView(R.id.send)
    Button sendButton;
    @BindView(R.id.response)
    TextView responseView;

    private RaceTimeService raceTimeService;
    private Disposable raceTimeServiceDisposable;
    private Disposable btConnStateDisposable;
    private Disposable commandDisposable;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_race_tracker, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    public void onStart() {
        super.onStart();
        if(raceTimeService != null) {
            initUI();
        }
    }

    private void initUI() {
        updateBluetoothAddressView(raceTimeService.getRaceTracker().getConnectionState(), null);
        startMonitoringBluetoothConnectionState();
    }

    public void onStop() {
        super.onStop();
        if(commandDisposable != null) {
            commandDisposable.dispose();
            commandDisposable = null;
        }

        stopMonitoringBluetoothConnectionState();
    }

    public void onDetach() {
        super.onDetach();
        raceTimeServiceDisposable.dispose();
        raceTimeServiceDisposable = null;
        raceTimeService = null;
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
                                responseView.setTextColor(responseTextColor);
                                responseView.setText(result);
                            },
                            ex -> {
                                responseView.setTextColor(errorColor);
                                responseView.setText(ex.getMessage());
                            });
        }
    }

    private void startMonitoringBluetoothConnectionState() {
        btConnStateDisposable = raceTimeService.getRaceTracker().observeConnectionState()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> updateBluetoothAddressView(state, null),
                        ex -> {
                            Log.e(LOG_TAG, "Bluetooth connection status", ex);
                            updateBluetoothAddressView(null, ex);
                        });
    }

    private void updateBluetoothAddressView(RxBleConnection.RxBleConnectionState state, Throwable err) {
        bluetoothAddressView.setText(raceTimeService.getRaceTracker().getAddress());
        if(err != null) {
            bluetoothAddressView.setTextColor(errorColor);
        } else {
            int color;
            switch (state) {
                case CONNECTING:
                    color = connectingColor;
                    break;
                case CONNECTED:
                    color = connectedColor;
                    break;
                case DISCONNECTING:
                    color = disconnectingColor;
                    break;
                default:
                    color = disconnectedColor;
            }
            bluetoothAddressView.setTextColor(color);
        }
    }

    private void stopMonitoringBluetoothConnectionState() {
        btConnStateDisposable.dispose();
        btConnStateDisposable = null;
    }

    @Override
    public void subscribeToRaceTimeService(Observable<RaceTimeService> serviceObservable) {
        raceTimeServiceDisposable = serviceObservable.subscribe(service -> {
            this.raceTimeService = service;
            sendButton.setEnabled(true);
            initUI();
        });
    }

    @Override
    public void setRaceTimeServiceManager(RaceTimeServiceManager manager) {
    }
}
