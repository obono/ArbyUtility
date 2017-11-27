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

/*
 * This code has built in knowledge of avrdude.
 * Thanks to avrdude coders
 *  Brian S. Dean, Joerg Wunsch, Eric Weddington, Jan-Hinnerk Reichert,
 *  Alex Shepherd, Martin Thomas, Theodore A. Roth, Michael Holzt
 *  Colin O'Flynn, Thomas Fischl, David Hoerl, Michal Ludvig,
 *  Darell Tan, Wolfgang Moser, Ville Voipio, Hannes Weisbach,
 *  Doug Springer, Brett Hagman
 *  and all contributers.
 */

package com.physicaloid.lib.programmer.avr;

import android.util.Log;

import com.obnsoft.arduboyutils.BuildConfig;
import com.physicaloid.lib.Boards;
import com.physicaloid.lib.Physicaloid.ProcessCallBack;
import com.physicaloid.lib.framework.SerialCommunicator;
import com.physicaloid.lib.programmer.avr.AvrTask.Op;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class AvrManager {

    private static final String TAG = AvrManager.class.getSimpleName();
    private static final boolean DEBUG_SHOW_HEXDUMP = BuildConfig.DEBUG && false;

    private TransferProtocol    mProg;
    private SerialCommunicator  mComm;
    private AvrConf             mAVRConf;
    private AVRMem              mAVRMemFlash;
    private AVRMem              mAVRMemEeprom;

    public AvrManager(SerialCommunicator serial) {
        mComm = serial;
    }

    public void setSerial(SerialCommunicator serial) {
        mComm = serial;
    }

    public boolean run(AvrTask task, Boards board, ProcessCallBack callback) {
        if (task != null) {
            ArrayList<AvrTask> tasks = new ArrayList<AvrTask>();
            tasks.add(task);
            return run(tasks, board, callback);
        }
        return true;
    }

    public boolean run(List<AvrTask> tasks, Boards board, ProcessCallBack callback) {
        if (board != Boards.ARDUINO_LEONARD) {
            if (callback != null) {
                callback.onError(TransferErrors.AVR_CHIPTYPE);
            }
            return false;
        }
        mProg = new Avr109();
        mProg.setSerial(mComm);
        mProg.setCallback(callback);

        /////////////////////////////////////////////////////////////////
        // AVRタイプの定数セット
        /////////////////////////////////////////////////////////////////
        try {
            setConfig(board); // .hexを読む前に実行すること(AVRMemがnewされない)
        } catch (Exception e) {
            if (callback != null) {
                callback.onError(TransferErrors.AVR_CHIPTYPE);
            }
            return false;
        }

        /////////////////////////////////////////////////////////////////
        // 事前チェック
        /////////////////////////////////////////////////////////////////
        mProg.setConfig(mAVRConf, mAVRMemFlash);
        mProg.open();
        mProg.enable();
        int initOK = mProg.initialize();
        if (initOK < 0) {
            Log.e(TAG, "initialization failed (" + initOK + ")");
            if (callback != null) {
                callback.onError(TransferErrors.CHIP_INIT);
            }
            return false;
        }

        int sigOK = mProg.check_sig_bytes();
        if (sigOK != 0) {
            Log.e(TAG, "check signature failed (" + sigOK + ")");
            if (callback != null) {
                callback.onError(TransferErrors.SIGNATURE);
            }
            return false;
        }

        /////////////////////////////////////////////////////////////////
        // ファイル読み込み
        /////////////////////////////////////////////////////////////////
        for (AvrTask task : tasks) {
            try {
                int result = -1;
                switch (task.getOperation()) {
                case UPLOAD_FLASH:
                    getFileToBuf(mAVRMemFlash, task.getInputStream(), task.isHex());
                    mProg.setOperation(Op.UPLOAD_FLASH);
                    mProg.setConfig(mAVRConf, mAVRMemFlash);
                    result = mProg.paged_write();
                    break;
                case UPLOAD_EEPROM:
                    getFileToBuf(mAVRMemEeprom, task.getInputStream(), task.isHex());
                    mProg.setOperation(Op.UPLOAD_EEPROM);
                    mProg.setConfig(mAVRConf, mAVRMemEeprom);
                    result = mProg.paged_write();
                    break;
                case DOWNLOAD_FLASH:
                    mProg.setOperation(Op.DOWNLOAD_FLASH);
                    mProg.setConfig(mAVRConf, mAVRMemFlash);
                    result = mProg.paged_read();
                    if (result > 0) {
                        putFileFromBuf(mAVRMemFlash, task.getOutputStream(), task.isHex());
                    }
                    break;
                case DOWNLOAD_EEPROM:
                    mProg.setOperation(Op.DOWNLOAD_EEPROM);
                    mProg.setConfig(mAVRConf, mAVRMemEeprom);
                    result = mProg.paged_read();
                    if (result > 0) {
                        putFileFromBuf(mAVRMemEeprom, task.getOutputStream(), task.isHex());
                    }
                    break;
                default:
                    break;
                }
                if (result == 0) {
                    return false; // canceled
                } else if (result < 0) {
                    Log.e(TAG, "operarion failed (" + task.getOperation().name() + ")");
                    if (callback != null) {
                        callback.onError(TransferErrors.OPERATION);
                    }
                    return false;
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onError(TransferErrors.FILE_OPEN);
                }
                return false;
            }
        }

        /////////////////////////////////////////////////////////////////
        // 後始末
        /////////////////////////////////////////////////////////////////
        mProg.disable();
        return true;
    }

    /**
     * Sets AVR configs
     * @param board
     * @throws InterruptedException
     */
    private void setConfig(Boards board) throws InterruptedException {
        mAVRConf = new AvrConf(board);
        mAVRMemFlash = new AVRMem(mAVRConf.flash);
        mAVRMemEeprom = new AVRMem(mAVRConf.eeprom);
    }

    /**
     * Converts a file to byte arrays
     * @param avrMem
     * @param in
     * @param isHex
     * @throws FileNotFoundException
     * @throws IOException
     * @throws Exception
     */
    private void getFileToBuf(AVRMem avrMem, InputStream in, boolean isHex)
            throws FileNotFoundException, IOException, Exception {

        long byteLength;
        if (isHex) {
            IntelHexFileToBuf intelHex = new IntelHexFileToBuf();
            intelHex.parse(in);
            byteLength = intelHex.getByteLength();
            avrMem.buf = new byte[(int) byteLength];
            intelHex.getHexData(avrMem.buf);
        } else {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            byte[] buf = new byte[1024 * 1024];
            int len;
            while ((len = in.read(buf)) >= 0) {
                bout.write(buf, 0, len);
            }
            bout.close();
            avrMem.buf = bout.toByteArray();
            byteLength = avrMem.buf.length;
        }
        in.close();

        if (DEBUG_SHOW_HEXDUMP) {
            showHexDump(avrMem.buf, byteLength);
        }
    }

    /**
     * Output byte arrays to a file
     * @param avrMem
     * @param out
     * @param isHex 
     * @throws FileNotFoundException
     * @throws IOException
     * @throws Exception
     */
    private void putFileFromBuf(AVRMem avrMem, OutputStream out, boolean isHex)
            throws FileNotFoundException, IOException, Exception {

        int byteLength = avrMem.buf.length;
        if (isHex) {
            IntelHexFileToBuf.convert(avrMem.buf, out);
        } else {
            out.write(avrMem.buf);
        }
        out.close();

        if (DEBUG_SHOW_HEXDUMP) {
            showHexDump(avrMem.buf, byteLength);
        }
    }

    /**
     * Dump byte arrays
     * @param ary
     * @param byteLength
     */
    private void showHexDump(byte[] ary, long byteLength) {
        StringBuffer strBuf = new StringBuffer();
        strBuf.append("Hex Dump [0:16]: ");
        for (int i = 0; i < 16; i++) {
            strBuf.append(String.format("%02x ", ary[i]));
        }
        Log.d(TAG, strBuf.toString());

        strBuf.setLength(0);
        strBuf.append("Hex Dump [").append(byteLength - 16).append(':').append(byteLength)
                .append("]: ");
        for (int i = (int) (byteLength - 16); i < byteLength; i++) {
            strBuf.append(String.format("%02x ", ary[i]));
        }
        Log.d(TAG, strBuf.toString());
    }
}
