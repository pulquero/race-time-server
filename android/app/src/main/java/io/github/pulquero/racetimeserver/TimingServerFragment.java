package io.github.pulquero.racetimeserver;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import butterknife.BindColor;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

public class TimingServerFragment extends Fragment implements RaceTimeServiceSubscriber {

    private static final String LOG_TAG = "TimingServerFragment";

    @BindColor(R.color.connected)
    int connectedColor;
    @BindColor(R.color.disconnected)
    int disconnectedColor;
    @BindColor(R.color.started)
    int startedColor;
    @BindColor(R.color.error)
    int errorColor;
    @BindView(R.id.networkAddress)
    TextView networkAddressView;
    @BindView(R.id.server)
    Button serverToggleButton;

    private RaceTimeService raceTimeService;
    private RaceTimeServiceManager raceTimeServiceManager;
    private Disposable raceTimeServiceDisposable;
    private Disposable netStateDisposable;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_timing_server, container, false);
        ButterKnife.bind(this, view);
        networkAddressView.setText(TimingServer.getNetworkAddress());
        return view;
    }

    public void onStart() {
        super.onStart();
        if(raceTimeService != null) {
            initUI();
        }
    }

    private void initUI() {
        updateNetworkAddressView(raceTimeService.getTimingServer().getState(), null);
        updateServerToggleButton();
        startMonitoringNetworkConnectionState();
    }

    public void onStop() {
        super.onStop();
        stopMonitoringNetworkConnectionState();
    }

    public void onDetach() {
        super.onDetach();
        raceTimeServiceDisposable.dispose();
        raceTimeServiceDisposable = null;
        raceTimeService = null;
    }

    @OnClick(R.id.server)
    public void onServerToggleClick() {
        if(raceTimeService.inUse()) {
            raceTimeService.getTimingServer().stop();
            stopMonitoringNetworkConnectionState();
            raceTimeServiceManager.stopFromRunningInBackground();
            raceTimeService.restartTimingService();
            updateNetworkAddressView(raceTimeService.getTimingServer().getState(), null);
            updateServerToggleButton();
            startMonitoringNetworkConnectionState();
        } else {
            serverToggleButton.setEnabled(false);
            raceTimeServiceManager.keepRunningInBackground();
            raceTimeService.getTimingServer().start();
        }
    }

    private void startMonitoringNetworkConnectionState() {
        netStateDisposable = raceTimeService.getTimingServer().observeState()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                            updateNetworkAddressView(state,null);
                            updateServerToggleButton();
                            serverToggleButton.setEnabled(true);
                        },
                        ex -> {
                            Log.e(LOG_TAG, "Network connection status", ex);
                            updateNetworkAddressView(null, ex);
                        });
    }


    private void updateNetworkAddressView(TimingServer.State state, Throwable err) {
        if(err != null) {
            networkAddressView.setTextColor(errorColor);
        } else {
            int color;
            switch (state) {
                case STARTED:
                    color = startedColor;
                    break;
                case CONNECTED:
                    color = connectedColor;
                    break;
                default:
                    color = disconnectedColor;
            }
            networkAddressView.setTextColor(color);
        }
    }

    private void updateServerToggleButton() {
        serverToggleButton.setText(raceTimeService.inUse() ? R.string.stopServer : R.string.startServer);
    }

    private void stopMonitoringNetworkConnectionState() {
        netStateDisposable.dispose();
        netStateDisposable = null;
    }

    @Override
    public void subscribeToRaceTimeService(Observable<RaceTimeService> serviceObservable) {
        raceTimeServiceDisposable = serviceObservable.subscribe(service -> {
            this.raceTimeService = service;
            serverToggleButton.setEnabled(true);
            initUI();
        });
    }

    @Override
    public void setRaceTimeServiceManager(RaceTimeServiceManager manager) {
        raceTimeServiceManager = manager;
    }
}
