package io.github.pulquero.racetimeserver;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

import androidx.appcompat.app.AppCompatActivity;
import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class ServerActivity extends AppCompatActivity {
    static final String MAC_ADDRESS_EXTRA = "macAddress";
    private static final String SERVER_TAG = "Server";

    @BindView(R.id.networkAddress)
    TextView networkAddressView;
    @BindView(R.id.server)
    Button serverToggleButton;
    private LiveTime liveTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);
        ButterKnife.bind(this);
        String addr = getIntent().getStringExtra(MAC_ADDRESS_EXTRA);
        networkAddressView.setText(getNetworkAddress());
    }

    private String getNetworkAddress() {
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
            Log.e(SERVER_TAG, e.getMessage());
        }
        return "No IP address";
    }

    @OnClick(R.id.server)
    public void onServerToggleClick() {
        if(isRunning()) {
            try {
                liveTime.stop();
                liveTime = null;
            } catch (IOException | InterruptedException e) {
                Log.w(SERVER_TAG, e.getMessage());
            }
        } else {
            liveTime = new LiveTime();
            liveTime.start();
        }

        updateButtonUIState();
    }

    private boolean isRunning() {
        return (liveTime != null);
    }

    private void updateButtonUIState() {
        serverToggleButton.setText(isRunning() ? R.string.stopServer : R.string.startServer);
    }
}

