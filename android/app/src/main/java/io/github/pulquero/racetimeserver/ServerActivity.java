package io.github.pulquero.racetimeserver;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.polidea.rxandroidble2.RxBleDevice;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import androidx.appcompat.app.AppCompatActivity;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;

public class ServerActivity extends AppCompatActivity {
    static final String MAC_ADDRESS_EXTRA = "macAddress";
    private static final String SERVER_TAG = "Server";

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

    private RaceTracker raceTracker;
    private TimingServer timingServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);
        ButterKnife.bind(this);
        String btAddr = getIntent().getStringExtra(MAC_ADDRESS_EXTRA);
        bluetoothAddressView.setText(btAddr);
        RxBleDevice device = RaceTracker.getRxBleClient(this).getBleDevice(btAddr);
        device.observeConnectionStateChanges()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                    switch(state) {
                        case CONNECTING:
                            bluetoothAddressView.setTextColor(0xFF008800);
                            break;
                        case CONNECTED:
                            bluetoothAddressView.setTextColor(Color.GREEN);
                            break;
                        case DISCONNECTING:
                            bluetoothAddressView.setTextColor(Color.GRAY);
                            break;
                        case DISCONNECTED:
                            bluetoothAddressView.setTextColor(Color.BLACK);
                            break;
                    }
                },
                ex -> {
                    Log.e(SERVER_TAG, "Bluetooth connection status", ex);
                    bluetoothAddressView.setTextColor(Color.RED);
                }

        );
        raceTracker = new RaceTracker(device);
        networkAddressView.setText(getNetworkAddress());
    }

    @OnClick(R.id.server)
    public void onServerToggleClick() {
        if(isRunning()) {
            try {
                timingServer.stop();
                timingServer = null;
            } catch (IOException | InterruptedException e) {
                Log.w(SERVER_TAG, "Stop server", e);
            }
        } else {
            timingServer = new TimingServer(raceTracker, new TimingServer.Listener() {
                @Override
                public void onConnect() {
                    networkAddressView.setTextColor(Color.GREEN);
                }

                @Override
                public void onDisconnect() {
                    networkAddressView.setTextColor(Color.BLACK);
                }
            });
            timingServer.start();
        }

        updateButtonUIState();
    }

    private boolean isRunning() {
        return (timingServer != null);
    }

    private void updateButtonUIState() {
        serverToggleButton.setText(isRunning() ? R.string.stopServer : R.string.startServer);
    }

    @OnClick(R.id.send)
    public void onSendClick() {
        String cmd = commandText.getText().toString();
        if(cmd != null && !cmd.isEmpty()) {
            commandText.setEnabled(false);
            sendButton.setEnabled(false);
            responseView.setText("");
            raceTracker.sendAndObserve(commandText.getText().toString())
                    .observeOn(AndroidSchedulers.mainThread())
                    .firstOrError()
                    .doFinally(() -> {
                        commandText.setEnabled(true); sendButton.setEnabled(true);})
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

    private static String getNetworkAddress() {
        try {
            Enumeration<NetworkInterface> intfIter = NetworkInterface.getNetworkInterfaces();
            if (intfIter == null) {
                return "No network interface - permissions?";
            }
            while (intfIter.hasMoreElements()) {
                NetworkInterface intf = intfIter.nextElement();
                if (intf.isUp() && !intf.isLoopback()) {
                    for (Enumeration<InetAddress> addrIter = intf.getInetAddresses(); addrIter.hasMoreElements(); ) {
                        InetAddress addr = addrIter.nextElement();
                        if (!addr.isLoopbackAddress() && (addr instanceof Inet4Address)) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        } catch (IOException e) {
            Log.e(SERVER_TAG, "Get network address", e);
        }
        return "No IP address";
    }
}
