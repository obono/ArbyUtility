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

package com.physicaloid.lib;

import android.content.Context;
import android.util.Log;

import com.obnsoft.arduboyutils.BuildConfig;
import com.physicaloid.lib.framework.AutoCommunicator;
import com.physicaloid.lib.framework.SerialCommunicator;
import com.physicaloid.lib.programmer.avr.AvrManager;
import com.physicaloid.lib.programmer.avr.AvrTask;
import com.physicaloid.lib.programmer.avr.TransferErrors;
import com.physicaloid.lib.usb.driver.uart.ReadListener;
import com.physicaloid.lib.usb.driver.uart.UartConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class Physicaloid {
    private static final boolean DEBUG_SHOW = true && BuildConfig.DEBUG;
    private static final String TAG = Physicaloid.class.getSimpleName();

    private Context mContext;

    protected SerialCommunicator mSerial;
    private Thread mUploadThread;
    //private UploadCallBack mCallBack;

    private static final Object LOCK = new Object();
    protected static final Object LOCK_WRITE = new Object();
    protected static final Object LOCK_READ = new Object();

    public Physicaloid(Context context) {
        this.mContext = context;
    }

    /**
     * Opens a device and communicate USB UART by default settings
     * @return true : successful , false : fail
     * @throws RuntimeException
     */
    public boolean open() throws RuntimeException {
        return open(new UartConfig());
    }

    /**
     * Opens a device and communicate USB UART
     * @param uart UART configuration
     * @return true : successful , false : fail
     * @throws RuntimeException
     */
    public boolean open(UartConfig uart) throws RuntimeException {
        synchronized (LOCK) {
            if(mSerial == null) {
                mSerial = new AutoCommunicator().getSerialCommunicator(mContext);
                if(mSerial == null) return false;
            }
            if(mSerial.open()) {
                mSerial.setUartConfig(uart);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Closes a device.
     * @return true : successful , false : fail
     * @throws RuntimeException
     */
    public boolean close() throws RuntimeException {
        synchronized (LOCK) {
            if(mSerial == null) return true;
            if(mSerial.close()) {
                mSerial = null;
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Reads from a device
     * @param buf
     * @return read byte size
     * @throws RuntimeException
     */
    public int read(byte[] buf) throws RuntimeException {
        if(mSerial == null) return 0;
        return read(buf, buf.length);
    }

    /**
     * Reads from a device
     * @param buf
     * @param size
     * @return read byte size
     * @throws RuntimeException
     */
    public int read(byte[] buf, int size) throws RuntimeException {
        synchronized (LOCK_READ) {
            if(mSerial == null) return 0;
            return mSerial.read(buf, size);
        }
    }

    /**
     * Adds read listener
     * @param listener ReadListener
     * @return true : successful , false : fail
     * @throws RuntimeException
     */
    public boolean addReadListener(ReadListener listener) throws RuntimeException {
        synchronized (LOCK_READ) {
            if(mSerial == null) return false;
            if(listener == null) return false;
            mSerial.addReadListener(listener);
            return true;
        }
    }

    /**
     * Clears read listener
     * @throws RuntimeException
     */
    public void clearReadListener() throws RuntimeException {
        synchronized (LOCK_READ) {
            if(mSerial == null) return;
            mSerial.clearReadListener();
        }
    }

    /**
     * Writes to a device.
     * @param buf
     * @return written byte size
     * @throws RuntimeException
     */
    public int write(byte[] buf) throws RuntimeException {
        if(mSerial == null) return 0;
        return write(buf, buf.length);
    }

    /**
     * Writes to a device.
     * @param buf
     * @param size
     * @return written byte size
     * @throws RuntimeException
     */
    public int write(byte[] buf, int size) throws RuntimeException {
        synchronized (LOCK_WRITE){
            if(mSerial == null) return 0;
            return mSerial.write(buf, size);
        }
    }

    /**
     * Uploads a binary file to a device on background process. No need to open().
     * @param board board profile e.g. Boards.ARDUINO_UNO
     * @param filePath a binary file path e.g. /sdcard/arduino/Blink.hex
     * @throws RuntimeException
     */
    public void upload(Boards board, String filePath) throws RuntimeException {
        upload(board, filePath, null);
    }

    /**
     * Uploads a binary file to a device on background process. No need to open().
     * @param board board profile e.g. Boards.ARDUINO_UNO
     * @param filePath a binary file path e.g. /sdcard/arduino/Blink.uno.hex
     * @param callback
     * @throws RuntimeException
     */
    public void upload(Boards board, String filePath, UploadCallBack callback)
            throws RuntimeException {
        ArrayList<AvrTask> tasks = new ArrayList<AvrTask>();
        try {
            AvrTask.Op operation = AvrTask.Op.UPLOAD_FLASH;
            if (filePath.toLowerCase(Locale.getDefault()).endsWith(AvrTask.EXT_EEPROM)) {
                operation = AvrTask.Op.UPLOAD_EEPROM;
            }
            tasks.add(new AvrTask(operation, new File(filePath)));
        } catch (FileNotFoundException e) {
            if (callback != null) {
                callback.onError(TransferErrors.FILE_OPEN);
            }
            return;
        }
        processTasks(tasks, board, callback);
    }

    /**
     * Uploads a binary file to a device on background process. No need to open().
     * @param board board profile e.g. Boards.ARDUINO_UNO
     * @param fileStream a binary stream e.g. getResources().getAssets().open("Blink.uno.hex")
     * @throws RuntimeException
     */
    public void upload(Boards board, InputStream fileStream) throws RuntimeException {
        upload(board, fileStream, null);
    }

    /**
     * Uploads a binary file to a device on background process. No need to open().
     * @param board board profile e.g. Boards.ARDUINO_UNO
     * @param fileStream a binary stream e.g. getResources().getAssets().open("Blink.uno.hex")
     * @param callback
     * @throws RuntimeException
     */
    public void upload(Boards board, InputStream fileStream, UploadCallBack callback)
            throws RuntimeException {
        ArrayList<AvrTask> tasks = new ArrayList<AvrTask>();
        tasks.add(new AvrTask(AvrTask.Op.UPLOAD_FLASH, fileStream, true));
        processTasks(tasks, board, callback);
    }

    /**
     * Process tasks on background process. No need to open().
     * @param tasks
     * @param board board profile
     * @param callback
     * @throws RuntimeException
     */
    public void processTasks(final List<AvrTask> tasks, final Boards board,
            final UploadCallBack callback) throws RuntimeException {

        final boolean serialIsNull;
        if (mSerial == null) { // if not open
            if (DEBUG_SHOW) {
                Log.d(TAG, "upload : mSerial is null");
            }
            // need to run on non-thread
            mSerial = new AutoCommunicator().getSerialCommunicator(mContext);
            serialIsNull = true;
        } else {
            serialIsNull = false;
        }

        mUploadThread = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (LOCK) {
                synchronized (LOCK_WRITE) {
                synchronized (LOCK_READ) {
                    UartConfig tmpUartConfig = new UartConfig();


                    if (mSerial == null) { // fail
                        if(DEBUG_SHOW) { Log.d(TAG, "upload : mSerial is null"); }
                        if (callback != null) {
                            callback.onError(TransferErrors.OPEN_DEVICE);
                        }
                        mSerial = null;
                        return;
                    }

                    if(!mSerial.isOpened()){
                        if(!mSerial.open()) {
                            if(DEBUG_SHOW) { Log.d(TAG, "upload : cannot mSerial.open"); }
                            if (callback != null) { callback.onError(TransferErrors.OPEN_DEVICE); }
                            if (serialIsNull) {
                                mSerial.close();
                            }
                            mSerial = null;
                            return;
                        }
                        if(DEBUG_SHOW) { Log.d(TAG, "upload : open successful"); }
                    } else { // if already open
                        UartConfig origUartConfig = mSerial.getUartConfig();
                        tmpUartConfig.baudrate = origUartConfig.baudrate;
                        tmpUartConfig.dataBits = origUartConfig.dataBits;
                        tmpUartConfig.stopBits = origUartConfig.stopBits;
                        tmpUartConfig.parity = origUartConfig.parity;
                        tmpUartConfig.dtrOn = origUartConfig.dtrOn;
                        tmpUartConfig.rtsOn = origUartConfig.rtsOn;
                        if(DEBUG_SHOW) { Log.d(TAG, "upload : already open"); }
                    }

                    //mSerial.stopReadListener();
                    mSerial.clearBuffer();

                    boolean ret = false;
                    if (callback != null) {
                        callback.onPreUpload();
                    }
                    AvrManager avrManager = new AvrManager(mSerial);
                    mSerial.setUartConfig(new UartConfig());
                    ret = avrManager.run(tasks, board, callback);
                    if (callback != null) {
                        callback.onPostUpload(ret);
                    }

                    if (serialIsNull) {
                        mSerial.close();
                    } else {
                        mSerial.setUartConfig(tmpUartConfig); // recover if already open
                        mSerial.clearBuffer();
                        //mSerial.startReadListener();
                    }

                }}}
            }
        });

        mUploadThread.start();
    }

    public void cancelUpload() {
        if (mUploadThread == null) {
            return;
        }
        mUploadThread.interrupt();
    }

    /**
     * Callbacks of program process<br>
     * normal process:<br>
     *  onPreUpload() -> onUploading -> onPostUpload<br>
     * cancel:<br>
     *  onPreUpload() -> onUploading -> onCancel -> onPostUpload<br>
     * error:<br>
     *  onPreUpload   |<br>
     *  onUploading   | -> onError<br>
     *  onPostUpload  |<br>
     * @author keisuke
     *
     */
    public interface UploadCallBack{
        /*
         * Callback methods
         */
        void onPreUpload();
        void onUploading(int value);
        void onPostUpload(boolean success);
        void onCancel();
        void onError(TransferErrors err);
    }

    /**
     * Gets opened or closed status
     * @return true : opened, false : closed
     * @throws RuntimeException
     */
    public boolean isOpened() throws RuntimeException {
        synchronized (LOCK) {
            if(mSerial == null) return false;
            return mSerial.isOpened();
        }
    }

    /**
     * Sets Serial Configuration
     * @param settings
     */
    public void setConfig(UartConfig settings) throws RuntimeException{
        synchronized (LOCK) {
            if(mSerial == null) return;
            mSerial.setUartConfig(settings);
        }
    }

    /**
     * Sets Baud Rate
     * @param baudrate any baud-rate e.g. 9600
     * @return true : successful, false : fail
     */
    public boolean setBaudrate(int baudrate) throws RuntimeException{
        synchronized (LOCK) {
            if(mSerial == null) return false;
            return mSerial.setBaudrate(baudrate);
        }
    }

    /**
     * Sets Data Bits
     * @param dataBits data bits e.g. UartConfig.DATA_BITS8
     * @return true : successful, false : fail
     */
    public boolean setDataBits(int dataBits) throws RuntimeException{
        synchronized (LOCK) {
            if(mSerial == null) return false;
            return mSerial.setDataBits(dataBits);
        }
    }

    /**
     * Sets Parity Bits
     * @param parity parity bits e.g. UartConfig.PARITY_NONE
     * @return true : successful, false : fail
     */
    public boolean setParity(int parity) throws RuntimeException{
        synchronized (LOCK) {
            if(mSerial == null) return false;
            return mSerial.setParity(parity);
        }
    }

    /**
     * Sets Stop bits
     * @param stopBits stop bits e.g. UartConfig.STOP_BITS1
     * @return true : successful, false : fail
     */
    public boolean setStopBits(int stopBits) throws RuntimeException{
        synchronized (LOCK) {
            if(mSerial == null) return false;
            return mSerial.setStopBits(stopBits);
        }
    }

    /**
     * Sets flow control DTR/RTS
     * @param dtrOn true then DTR on
     * @param rtsOn true then RTS on
     * @return true : successful, false : fail
     */
    public boolean setDtrRts(boolean dtrOn, boolean rtsOn) throws RuntimeException{
        synchronized (LOCK) {
            if(mSerial == null) return false;
            return mSerial.setDtrRts(dtrOn, rtsOn);
        }
    }
}
