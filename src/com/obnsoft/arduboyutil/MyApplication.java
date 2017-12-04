/*
 * Copyright (C) 2017 OBONO
 * http://d.hatena.ne.jp/OBONO/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
