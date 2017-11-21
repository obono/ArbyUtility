/*
 * Copyright (C) 2013 Keisuke SUZUKI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * Distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * 
 * This source code was modified by OBONO in November 2017.
 */

package com.physicaloid.lib.framework;

import android.content.Context;

import com.physicaloid.lib.usb.UsbAccessor;
import com.physicaloid.lib.usb.driver.uart.UartCdcAcm;

public class AutoCommunicator {
    @SuppressWarnings("unused")
    private static final String TAG = AutoCommunicator.class.getSimpleName();

    public AutoCommunicator() {
    }

    public SerialCommunicator getSerialCommunicator(Context context) {
        UsbAccessor usbAccess = UsbAccessor.INSTANCE;
        usbAccess.init(context);
        return new UartCdcAcm(context);
    }
}
