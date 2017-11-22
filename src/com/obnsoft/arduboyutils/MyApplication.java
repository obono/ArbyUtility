package com.obnsoft.arduboyutils;

import com.physicaloid.lib.Physicaloid;

import android.app.Application;
import android.content.IntentFilter;
import android.hardware.usb.UsbManager;

public class MyApplication extends Application {

    public static final IntentFilter USB_RECEIVER_FILTER =
            new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);

    private Physicaloid mPhysicaloid;

    @Override
    public void onCreate() {
        super.onCreate();
        mPhysicaloid = new Physicaloid(getApplicationContext());
    }

    @Override
    public void onTerminate() {
        mPhysicaloid = null;
        super.onTerminate();
    }

    public Physicaloid getPhysicaloidInstance() {
        return mPhysicaloid;
    }

}
