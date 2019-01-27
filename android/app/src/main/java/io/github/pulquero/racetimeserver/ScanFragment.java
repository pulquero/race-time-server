package io.github.pulquero.racetimeserver;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.polidea.rxandroidble2.scan.ScanFilter;
import com.polidea.rxandroidble2.scan.ScanResult;
import com.polidea.rxandroidble2.scan.ScanSettings;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ScanFragment extends Fragment {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final String LOG_TAG = "ScanFragment";

    @BindView(R.id.scan)
    Button scanToggleButton;
    @BindView(R.id.results)
    RecyclerView recyclerView;
    private Disposable scanDisposable;
    private ScanResultsAdapter resultsAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_scan, container, false);
        ButterKnife.bind(this, view);

        configureResultList();
        return view;
    }

    private void configureResultList() {
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(recyclerLayoutManager);
        resultsAdapter = new ScanResultsAdapter();
        recyclerView.setAdapter(resultsAdapter);
        resultsAdapter.setOnAdapterItemClickListener(view -> {
            final int childAdapterPosition = recyclerView.getChildAdapterPosition(view);
            final ScanResult itemAtPosition = resultsAdapter.getItemAtPosition(childAdapterPosition);
            doConnect(itemAtPosition.getBleDevice().getMacAddress());
        });
    }

    @OnClick(R.id.scan)
    public void onScanToggleClick() {
        if (isScanning()) {
            stopScanning();
        } else {
            if(!ensureBluetoothOn()) {
                return;
            }
            scanBleDevices();
        }

        updateButtonUIState();
    }

    private void scanBleDevices() {
        resultsAdapter.clearScanResults();
        scanDisposable = RaceTracker.getRxBleClient(getContext()).scanBleDevices(
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .build(),
                new ScanFilter.Builder()
                        .setServiceUuid(RaceTracker.SERVICE_UUID)
                        .build()
        )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(this::dispose)
                .subscribe(resultsAdapter::addScanResult, this::onScanFailure);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isScanning()) {
            stopScanning();
        }
    }

    private boolean isScanning() {
        return scanDisposable != null;
    }

    private void stopScanning() {
        scanDisposable.dispose();
        scanDisposable = null;
    }

    private void doConnect(String macAddress) {
        final Intent intent = new Intent(getContext(), ServerActivity.class);
        intent.putExtra(ServerActivity.MAC_ADDRESS_EXTRA, macAddress);
        startActivity(intent);
    }

    private void onScanFailure(Throwable throwable) {
        Log.e(LOG_TAG, "Scan", throwable);
        Toast.makeText(getContext(), throwable.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void dispose() {
        stopScanning();
        updateButtonUIState();
    }

    private void updateButtonUIState() {
        scanToggleButton.setText(isScanning() ? R.string.stopScan : R.string.startScan);
    }

    boolean ensureBluetoothOn() {
        switch(RaceTracker.getRxBleClient(getContext()).getState()) {
            case BLUETOOTH_NOT_AVAILABLE:
                Toast.makeText(getContext(), R.string.errNoBluetooth, Toast.LENGTH_LONG).show();
                return false;
            case LOCATION_PERMISSION_NOT_GRANTED:
                requestLocationPermission(getActivity());
                return false;
            case BLUETOOTH_NOT_ENABLED:
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                return false;
            case LOCATION_SERVICES_NOT_ENABLED:
                Toast.makeText(getContext(), R.string.errLocationOff, Toast.LENGTH_LONG).show();
                return false;
            case READY:
                return true;
        }
        throw new AssertionError();
    }


    private static final int REQUEST_PERMISSION_COARSE_LOCATION = 9358;

    static void requestLocationPermission(Activity activity) {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_PERMISSION_COARSE_LOCATION
        );
    }
}
