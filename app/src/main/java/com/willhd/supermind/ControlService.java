package com.willhd.supermind;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import com.choosemuse.libmuse.Accelerometer;
import com.choosemuse.libmuse.ConnectionState;
import com.choosemuse.libmuse.Eeg;
import com.choosemuse.libmuse.Muse;
import com.choosemuse.libmuse.MuseArtifactPacket;
import com.choosemuse.libmuse.MuseConnectionListener;
import com.choosemuse.libmuse.MuseConnectionPacket;
import com.choosemuse.libmuse.MuseDataListener;
import com.choosemuse.libmuse.MuseDataPacket;
import com.choosemuse.libmuse.MuseDataPacketType;
import com.choosemuse.libmuse.MuseListener;
import com.choosemuse.libmuse.MuseManagerAndroid;
import com.choosemuse.libmuse.MuseVersion;

import java.util.List;

public class ControlService extends AccessibilityService {
    private final String TAG = "Control Service";

    private AccessibilityNodeInfo rootNodeInfo;

    private MuseManagerAndroid manager;

    private Muse muse;

    private MuseConnectionListener connectionListener;

    private MuseDataListener dataListener;

    private final Handler handler = new Handler();

    private final double[] eegBuffer = new double[6];
    private boolean eegStale;
    private final double[] alphaBuffer = new double[6];
    private boolean alphaStale;
    private final double[] accelBuffer = new double[3];
    private boolean accelStale;

    private boolean dataTransmission = true;

    @Override
    public void onCreate() {
        manager = MuseManagerAndroid.getInstance();
        manager.setContext(this);
        dataListener = new MuseDataListener() {
            @Override
            public void receiveMuseDataPacket(MuseDataPacket p, final Muse muse) {
                // valuesSize returns the number of data values contained in the packet.
                final long n = p.valuesSize();
                switch (p.packetType()) {
                    case EEG:
                        getEegChannelValues(eegBuffer,p);
                        eegStale = true;
                        break;
                    case ACCELEROMETER:
                        getAccelValues(p);
                        accelStale = true;
                        break;
                    case ALPHA_RELATIVE:
                        getEegChannelValues(alphaBuffer,p);
                        alphaStale = true;
                        break;
                    case BATTERY:
                    case DRL_REF:
                    case QUANTIZATION:
                    default:
                        break;
                }
                Log.d(TAG, "EEG" + eegBuffer.toString());
                Log.d(TAG, "ACC" + accelBuffer.toString());
            }

            @Override
            public void receiveMuseArtifactPacket(MuseArtifactPacket museArtifactPacket, Muse muse) {

            }
        };
        connectionListener = new MuseConnectionListener() {
            @Override
            public void receiveMuseConnectionPacket(MuseConnectionPacket p, final Muse muse) {

                final ConnectionState current = p.getCurrentConnectionState();

                // Format a message to show the change of connection state in the UI.
                final String status = p.getPreviousConnectionState() + " -> " + current;
                Log.i(TAG, status);

                // Update the UI with the change in connection state.
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        final MuseVersion museVersion = muse.getMuseVersion();
                        // If we haven't yet connected to the headband, the version information
                        // will be null.  You have to connect to the headband before either the
                        // MuseVersion or MuseConfiguration information is known.
                        if (museVersion != null) {
                            final String version = museVersion.getFirmwareType() + " - "
                                    + museVersion.getFirmwareVersion() + " - "
                                    + museVersion.getProtocolVersion();
                        } else {
                            Log.e(TAG, "Undefined Version");
                        }
                    }
                });

                if (current == ConnectionState.DISCONNECTED) {
                    Log.i(TAG, "Muse disconnected:" + muse.getName());
                    // We have disconnected from the headband, so set our cached copy to null.
                    setNullMuse();
                }
            }
        };
        manager.setMuseListener(new MuseListener() {
            @Override
            public void museListChanged() {

            }
        });
        ensurePermissions();
    }

    public void setNullMuse () {muse = null;}

    private void getEegChannelValues(double[] buffer, MuseDataPacket p) {
        buffer[0] = p.getEegChannelValue(Eeg.EEG1);
        buffer[1] = p.getEegChannelValue(Eeg.EEG2);
        buffer[2] = p.getEegChannelValue(Eeg.EEG3);
        buffer[3] = p.getEegChannelValue(Eeg.EEG4);
        buffer[4] = p.getEegChannelValue(Eeg.AUX_LEFT);
        buffer[5] = p.getEegChannelValue(Eeg.AUX_RIGHT);
    }

    private void getAccelValues(MuseDataPacket p) {
        accelBuffer[0] = p.getAccelerometerValue(Accelerometer.X);
        accelBuffer[1] = p.getAccelerometerValue(Accelerometer.Y);
        accelBuffer[2] = p.getAccelerometerValue(Accelerometer.Z);
    }

    public boolean isBluetoothEnabled() {
        return BluetoothAdapter.getDefaultAdapter().isEnabled();
    }

    @Override
    public void onAccessibilityEvent (AccessibilityEvent event) {
        Notification notification = new Notification();
        notification.flags = Notification.FLAG_ONGOING_EVENT;
        notification.flags |= Notification.FLAG_NO_CLEAR;
        notification.flags |= Notification.FLAG_FOREGROUND_SERVICE;
        this.startForeground(1, notification);
        Log.e(TAG, "Called");
        this.rootNodeInfo = event.getSource();
        if (this.rootNodeInfo == null) return;

        if (manager.getMuses().size() < 1) {
            manager.stopListening();

            List<Muse> availableMuses = manager.getMuses();

            // Check that we actually have something to connect to.
            if (availableMuses.size() < 1) {
                Log.w(TAG, "There is nothing to connect to");
            } else {
                // Cache the Muse that the user has selected.
                muse = availableMuses.get(0);
                // Unregister all prior listeners and register our data listener to
                // receive the MuseDataPacketTypes we are interested in.  If you do
                // not register a listener for a particular data type, you will not
                // receive data packets of that type.
                muse.unregisterAllListeners();
                muse.registerConnectionListener(connectionListener);
                muse.registerDataListener(dataListener, MuseDataPacketType.EEG);
                muse.registerDataListener(dataListener, MuseDataPacketType.ALPHA_RELATIVE);
                muse.registerDataListener(dataListener, MuseDataPacketType.ACCELEROMETER);
                muse.registerDataListener(dataListener, MuseDataPacketType.BATTERY);
                muse.registerDataListener(dataListener, MuseDataPacketType.DRL_REF);
                muse.registerDataListener(dataListener, MuseDataPacketType.QUANTIZATION);

                // Initiate a connection to the headband and stream the data asynchronously.
                muse.runAsynchronously();
            }
        }


        Log.e(TAG, "Ready to fetch ");
        AccessibilityNodeInfo fetching = getRootInActiveWindow();
        if (fetching != null) this.recycle(getRootInActiveWindow());
    }

    public void recycle(AccessibilityNodeInfo info) {
        Log.d(TAG, "Recycling " + (info == null));
        if (info == null) return;

        if (info.getChildCount() == 0) {
            Log.d(TAG, "child:" + info.getClassName());
            //Log.d(TAG, "Dialog:" + info.canOpenPopup());
            Log.d(TAG, "Text：" + info.getText());
            Log.d(TAG, "windowId:" + info.getWindowId());
            Log.d(TAG, "ViewId:" + info.getViewIdResourceName());
            Log.d(TAG, "ContentDesc:" + info.getContentDescription());
            Log.d(TAG, "ID:" + info.toString().split("@")[1].split(";")[0]);

            onReceive(info);

        } else {
            for (int i = 0; i < info.getChildCount(); i++) {
                if (info.getChild(i) != null) {
                    recycle(info.getChild(i));
                }
            }
        }
    }

    public void onReceive (AccessibilityNodeInfo info) {

        if (info.getClassName() != null && info.getText() != null) {
            if (info.getText().toString().equals("Like") &&
                    info.getContentDescription().toString().equals("Like button. Double tap and hold to react.")
                    ) {
                info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.e(TAG, "clicked");
            }
        }
    }

    @Override
    public void onInterrupt() {

    }

    private void ensurePermissions() {

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            // We don't have the ACCESS_COARSE_LOCATION permission so create the dialogs asking
            // the user to grant us the permission.
            Toast.makeText(this, "Please enable all Permissions", Toast.LENGTH_LONG).show();
        }
    }

}
