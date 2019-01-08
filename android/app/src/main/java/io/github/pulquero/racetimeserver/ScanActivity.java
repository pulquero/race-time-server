package io.github.pulquero.racetimeserver;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.polidea.rxandroidble2.exceptions.BleScanException;
import com.polidea.rxandroidble2.scan.ScanFilter;
import com.polidea.rxandroidble2.scan.ScanResult;
import com.polidea.rxandroidble2.scan.ScanSettings;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

public class ScanActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;

    @BindView(R.id.scan)
    Button scanToggleButton;
    @BindView(R.id.results)
    RecyclerView recyclerView;
    @BindView(R.id.address)
    EditText addressText;
    @BindView(R.id.connect)
    Button connectButton;
    private Disposable scanDisposable;
    private ScanResultsAdapter resultsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);
        ButterKnife.bind(this);

        configureResultList();
    }

    @OnClick(R.id.scan)
    public void onScanToggleClick() {

        if (isScanning()) {
            scanDisposable.dispose();
        } else {
            if(!ensureBluetoothOn()) {
                return;
            }
            scanBleDevices();
        }

        updateButtonUIState();
    }

    @OnTextChanged(R.id.address)
    public void addressChanged() {
        String macAddress = addressText.getText().toString();
        if(!macAddress.isEmpty()) {
            connectButton.setEnabled(true);
        } else {
            connectButton.setEnabled(false);
        }
    }

    @OnClick(R.id.connect)
    public void onConnectClick() {
        String macAddress = addressText.getText().toString();
        doConnect(macAddress);
    }

    private void scanBleDevices() {
        scanDisposable = RaceTracker.getRxBleClient(this).scanBleDevices(
                new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                        .build(),
                new ScanFilter.Builder()
                        .build()
        )
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(this::dispose)
                .subscribe(resultsAdapter::addScanResult, this::onScanFailure);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (isScanning()) {
            scanDisposable.dispose();
        }
    }

    private void configureResultList() {
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemAnimator(null);
        LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(recyclerLayoutManager);
        resultsAdapter = new ScanResultsAdapter();
        recyclerView.setAdapter(resultsAdapter);
        resultsAdapter.setOnAdapterItemClickListener(view -> {
            final int childAdapterPosition = recyclerView.getChildAdapterPosition(view);
            final ScanResult itemAtPosition = resultsAdapter.getItemAtPosition(childAdapterPosition);
            doConnect(itemAtPosition.getBleDevice().getMacAddress());
        });
    }

    private boolean isScanning() {
        return scanDisposable != null;
    }


    private void doConnect(String macAddress) {
        final Intent intent = new Intent(this, ServerActivity.class);
        intent.putExtra(ServerActivity.MAC_ADDRESS_EXTRA, macAddress);
        startActivity(intent);
    }

    private void onScanFailure(Throwable throwable) {
        if (throwable instanceof BleScanException) {
            Toast.makeText(this, throwable.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void dispose() {
        scanDisposable = null;
        resultsAdapter.clearScanResults();
        updateButtonUIState();
    }

    private void updateButtonUIState() {
        scanToggleButton.setText(isScanning() ? R.string.stopScan : R.string.startScan);
    }

    private static final int REQUEST_PERMISSION_COARSE_LOCATION = 9358;

    boolean ensureBluetoothOn() {
        switch(RaceTracker.getRxBleClient(this).getState()) {
            case BLUETOOTH_NOT_AVAILABLE:
                Toast.makeText(this, R.string.errNoBluetooth, Toast.LENGTH_LONG).show();
                return false;
            case LOCATION_PERMISSION_NOT_GRANTED:
                requestLocationPermission(this);
                return false;
            case BLUETOOTH_NOT_ENABLED:
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                return false;
            case LOCATION_SERVICES_NOT_ENABLED:
                Toast.makeText(this, R.string.errLocationOff, Toast.LENGTH_LONG).show();
                return false;
            case READY:
                return true;
        }
        throw new AssertionError();
    }

    static void requestLocationPermission(final Activity activity) {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_PERMISSION_COARSE_LOCATION
        );
    }

}
