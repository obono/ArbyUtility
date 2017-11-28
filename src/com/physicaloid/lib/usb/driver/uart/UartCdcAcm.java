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

package com.physicaloid.lib.usb.driver.uart;

import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbConstants;
import android.util.Log;

import com.obnsoft.arduboyutils.BuildConfig;
import com.physicaloid.lib.framework.SerialCommunicator;
import com.physicaloid.lib.usb.UsbCdcConnection;
import com.physicaloid.misc.RingBuffer;

import java.util.ArrayList;
import java.util.List;

public class UartCdcAcm extends SerialCommunicator{
    private static final String TAG = UartCdcAcm.class.getSimpleName();

    private static final boolean DEBUG_SHOW = BuildConfig.DEBUG && false;

    private UsbCdcConnection mUsbConnetionManager;

    private UartConfig mUartConfig;
    private static final int RING_BUFFER_SIZE       = 1024;
    private static final int USB_READ_BUFFER_SIZE   = 256;
    private static final int USB_WRITE_BUFFER_SIZE  = 256;
    private static final int USB_VID_ARDUINO        = 0x2341;
    private static final int USB_REQUEST_TYPE =
            UsbConstants.USB_DIR_OUT | UsbConstants.USB_TYPE_CLASS | 0x01;
    private static final int USB_REQUESTID_SETLINECODING        = 0x20;
    private static final int USB_REQUESTID_SETCONTROLLINESTATE  = 0x22;
    private static final int USB_CONTROL_VALUE_DTR  = 0x0001;
    private static final int USB_CONTROL_VALUE_RTS  = 0x0002;
    private static final int USB_READ_TIMEOUT       = 5000;
    private static final int USB_WRITE_TIMEOUT      = 1000;
    private static final int USB_REQUEST_TIMEOUT    = 100;

    private RingBuffer mBuffer;

    private boolean mReadThreadStop = true;

    private UsbDeviceConnection mConnection;
    private UsbEndpoint mEndpointIn;
    private UsbEndpoint mEndpointOut;
    private int mInterfaceNum;

    private boolean isOpened;

    public UartCdcAcm(Context context) {
        super(context);
        mUsbConnetionManager = new UsbCdcConnection(context);
        mUartConfig = new UartConfig();
        mBuffer = new RingBuffer(RING_BUFFER_SIZE);
        isOpened = false;
    }

    @Override
    public boolean open() {
        if (mUsbConnetionManager.open(USB_VID_ARDUINO, true)) {
            mConnection     = mUsbConnetionManager.getConnection();
            mEndpointIn     = mUsbConnetionManager.getEndpointIn();
            mEndpointOut    = mUsbConnetionManager.getEndpointOut();
            //mInterfaceNum   = mUsbConnetionManager.getCdcAcmInterfaceNum();
            mInterfaceNum   = 0; // Trick!!
            if (!init()) { /*return false;*/ } // Trick!!
            if (!setBaudrate(UartConfig.DEFAULT_BAUDRATE)) { return false; }
            mBuffer.clear();
            startRead();
            isOpened = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean close() {
        stopRead();
        isOpened = false;
        return mUsbConnetionManager.close();
    }

    @Override
    public int read(byte[] buf, int size) {
        return mBuffer.get(buf, size);
    }

    @Override
    public int write(byte[] buf, int size) {
        if(buf == null) { return 0; }
        int offset = 0;
        int write_size;
        int written_size;
        byte[] wbuf = new byte[USB_WRITE_BUFFER_SIZE];

        while (offset < size) {
            write_size = USB_WRITE_BUFFER_SIZE;

            if (offset + write_size > size) {
                write_size = size - offset;
            }
            System.arraycopy(buf, offset, wbuf, 0, write_size);

            written_size = mConnection.bulkTransfer(
                    mEndpointOut, wbuf, write_size, USB_WRITE_TIMEOUT);

            if (written_size < 0) {
                return -1;
            }
            offset += written_size;
        }

        return offset;
    }

    private void stopRead() {
        mReadThreadStop = true;
    }

    private void startRead() {
        if(mReadThreadStop) {
            mReadThreadStop = false;
            new Thread(mLoop).start();
        }
    }

    private Runnable mLoop = new Runnable() {
        @Override
        public void run() {
            int len=0;
            byte[] rbuf = new byte[USB_READ_BUFFER_SIZE];
            while (!mReadThreadStop) {// this is the main loop for transferring

                try {
                    len = mConnection.bulkTransfer(
                            mEndpointIn, rbuf, rbuf.length, USB_READ_TIMEOUT);
                } catch(Exception e) {
                    Log.e(TAG, e.toString());
                }

                if (len > 0) {
                    mBuffer.add(rbuf, len);
                    onRead(len);
                }

            }
        } // end of run()
    }; // end of runnable


    /**
     * Sets Uart configurations
     * @param config configurations
     * @return true : successful, false : fail
     */
    public boolean setUartConfig(UartConfig config) {
        boolean res = true;
        boolean ret = true;
        if(mUartConfig.baudrate != config.baudrate) {
            res = setBaudrate(config.baudrate);
            ret = ret && res;
        }

        if(mUartConfig.dataBits != config.dataBits) {
            res = setDataBits(config.dataBits);
            ret = ret && res;
        }

        if(mUartConfig.parity != config.parity) {
            res = setParity(config.parity);
            ret = ret && res;
        }

        if(mUartConfig.stopBits != config.stopBits) {
            res = setStopBits(config.stopBits);
            ret = ret && res;
        }

        if(mUartConfig.dtrOn != config.dtrOn ||
           mUartConfig.rtsOn != config.rtsOn) {
            res = setDtrRts(config.dtrOn, config.rtsOn);
            ret = ret && res;
        }

        return ret;
    }

    /**
     * Initializes CDC communication
     * @return true : successful, false : fail
     */
    private boolean init() {
        if(mConnection == null) return false;
        int ret = mConnection.controlTransfer(USB_REQUEST_TYPE,
                USB_REQUESTID_SETCONTROLLINESTATE, 0x00, mInterfaceNum, null, 0, 0); // init CDC
        if(ret < 0) { return false; }
        return true;
    }

    @Override
    public boolean isOpened() {
        return isOpened;
    }

    /**
     * Sets baudrate
     * @param baudrate baudrate e.g. 9600
     * @return true : successful, false : fail
     */
    public boolean setBaudrate(int baudrate) {
        byte[] buf = new byte[] {
                (byte) (baudrate & 0x000000FF),
                (byte) ((baudrate & 0x0000FF00) >> 8),
                (byte) ((baudrate & 0x00FF0000) >> 16),
                (byte) ((baudrate & 0xFF000000) >> 24),
                0x00, 0x00, 0x08
        };
        int ret = mConnection.controlTransfer(USB_REQUEST_TYPE, USB_REQUESTID_SETLINECODING, 0,
                mInterfaceNum, buf, buf.length, USB_REQUEST_TIMEOUT);
        if(ret < 0) { 
            if(DEBUG_SHOW) { Log.d(TAG, "Fail to setBaudrate"); }
            return false;
        }
        mUartConfig.baudrate = baudrate;
        return true;
    }

    /**
     * Sets Data bits
     * @param dataBits data bits e.g. UartConfig.DATA_BITS8
     * @return true : successful, false : fail
     */
    public boolean setDataBits(int dataBits) {
        // TODO : implement
        if(DEBUG_SHOW) { Log.d(TAG, "Fail to setDataBits"); }
        mUartConfig.dataBits = dataBits;
        return false;
    }

    /**
     * Sets Parity bit
     * @param parity parity bits e.g. UartConfig.PARITY_NONE
     * @return true : successful, false : fail
     */
    public boolean setParity(int parity) {
        // TODO : implement
        if(DEBUG_SHOW) { Log.d(TAG, "Fail to setParity"); }
        mUartConfig.parity = parity;
        return false;
    }

    /**
     * Sets Stop bits
     * @param stopBits stop bits e.g. UartConfig.STOP_BITS1
     * @return true : successful, false : fail
     */
    public boolean setStopBits(int stopBits) {
        // TODO : implement
        if(DEBUG_SHOW) { Log.d(TAG, "Fail to setStopBits"); }
        mUartConfig.stopBits = stopBits;
        return false;
    }

    @Override
    public boolean setDtrRts(boolean dtrOn, boolean rtsOn) {
        int ctrlValue = 0x0000;
        if(dtrOn) {
            ctrlValue |= USB_CONTROL_VALUE_DTR;
        }
        if(rtsOn) {
            ctrlValue |= USB_CONTROL_VALUE_RTS;
        }
        int ret = mConnection.controlTransfer(USB_REQUEST_TYPE, USB_REQUESTID_SETCONTROLLINESTATE,
                ctrlValue, mInterfaceNum, null, 0, USB_REQUEST_TIMEOUT);
        if(ret < 0) { 
            if(DEBUG_SHOW) { Log.d(TAG, "Fail to setDtrRts"); }
            return false;
        }
        mUartConfig.dtrOn = dtrOn;
        mUartConfig.rtsOn = rtsOn;
        return true;
    }

    @Override
    public UartConfig getUartConfig() {
        return mUartConfig;
    }

    @Override
    public int getBaudrate() {
        return mUartConfig.baudrate;
    }

    @Override
    public int getDataBits() {
        return mUartConfig.dataBits;
    }

    @Override
    public int getParity() {
        return mUartConfig.parity;
    }

    @Override
    public int getStopBits() {
        return mUartConfig.stopBits;
    }

    @Override
    public boolean getDtr() {
        return mUartConfig.dtrOn;
    }

    @Override
    public boolean getRts() {
        return mUartConfig.rtsOn;
    }

    @Override
    public void clearBuffer() {
        mBuffer.clear();
    }

    //////////////////////////////////////////////////////////
    // Listener for reading uart
    //////////////////////////////////////////////////////////
    private List<ReadListener> uartReadListenerList
        = new ArrayList<ReadListener>();
    private boolean mStopReadListener = false;

    @Override
    public void addReadListener(ReadListener listener) {
        uartReadListenerList.add(listener);
    }

    @Override
    public void clearReadListener() {
        uartReadListenerList.clear();
    }

    @Override
    public void startReadListener() {
        mStopReadListener = false;
    }

    @Override
    public void stopReadListener() {
        mStopReadListener = true;
    }

    private void onRead(int size) {
        if(mStopReadListener) return;
        for (ReadListener listener: uartReadListenerList) {
            listener.onRead(size);
        }
    }
    //////////////////////////////////////////////////////////

}
