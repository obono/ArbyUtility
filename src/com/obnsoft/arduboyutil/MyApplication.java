package com.obnsoft.arduboyutil;

import com.physicaloid.lib.Physicaloid;

import android.app.Application;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

public class MyApplication extends Application {

    public static final IntentFilter USB_RECEIVER_FILTER =
            new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);

    private static final String TAG = "ArduboyUtility";

    private Physicaloid mPhysicaloid;
    private WakeLock    mWakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        Utils.generateFolders();
        mPhysicaloid = new Physicaloid(getApplicationContext());
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
    }

    @Override
    public void onTerminate() {
        mPhysicaloid = null;
        super.onTerminate();
    }

    public Physicaloid getPhysicaloidInstance() {
        return mPhysicaloid;
    }

    public void acquireWakeLock() {
        synchronized (mWakeLock) {
            if (!mWakeLock.isHeld()) {
                mWakeLock.acquire();
            }
        }
    }

    public void releaseWakeLock() {
        synchronized (mWakeLock) {
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
    }

}
