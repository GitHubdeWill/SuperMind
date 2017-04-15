package com.willhd.supermind;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

public class ControlService extends AccessibilityService {
    private final String TAG = "Control Service";

    private AccessibilityNodeInfo rootNodeInfo;

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
        if (true);
    }

    @Override
    public void onInterrupt() {

    }
}
