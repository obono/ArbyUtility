package com.physicaloid.lib.programmer.avr;

import android.util.Log;

import com.obnsoft.arduboyutils.BuildConfig;
import com.physicaloid.lib.framework.SerialCommunicator;
import com.physicaloid.lib.usb.driver.uart.ReadListener;

public class Avr109 extends TransferProtocol {
    private static final String TAG = Avr109.class.getSimpleName();

    private static final boolean DEBUG_NOT_SHOW = !BuildConfig.DEBUG || true;
    private static final boolean DEBUG_SHOW_READ = true && !DEBUG_NOT_SHOW;
    private static final boolean DEBUG_SHOW_WRITE = true && !DEBUG_NOT_SHOW;

    private static final int READ_TIMEOUT_MSEC = 5000;

    private static final byte CMD_SET_ADDRESS = 0x41;
    private static final byte CMD_START_BLOCK_LOAD = 0x42;
    private static final byte CMD_EXIT_BOOTLOADER = 0x45;
    private static final byte CMD_SET_EXTADDRESS = 0x48;
    private static final byte CMD_LEAVE_PROGRAMMING_MODE = 0x4c;
    private static final byte CMD_ENTER_PROGRAMMING_MODE = 0x50;
    private static final byte CMD_RETURN_SOFTWARE_IDENTIFIER = 0x53;
    private static final byte CMD_SELECT_DEVICE_TYPE = 0x54;
    private static final byte CMD_RETURN_SOFTWARE_VERSION = 0x56;
    private static final byte CMD_AUTO_INCREMENT_ADDRESS = 0x61;
    private static final byte CMD_CHECK_BLOCK_SUPPORT = 0x62;
    private static final byte CMD_START_BLOCK_READ = 0x67;
    private static final byte CMD_RETURN_PROGRAMMER_TYPE = 0x70;
    private static final byte CMD_READ_SIGNATURE_BYTES = 0x73;
    private static final byte CMD_RETURN_SUPPORTED_DEVICE_CODES = 0x74;

    private static final byte RSP_TERMINATE = 0x00;
    private static final byte RSP_SUCCESS = 0x0d;
    private static final byte RSP_YES = 0x59;

    private SerialCommunicator  mComm;
    //private AvrConf             mAVRConf;
    private AVRMem              mAVRMem;
    private MyReadListener      mReadListener;

    /*-----------------------------------------------------------------------*/

    class MyReadListener implements ReadListener {
        int mReadSize = 0;
        int mReadSizeTarget;
        @Override
        public synchronized void onRead(int size) {
            mReadSize += size;
            if (isEnough()) {
                notifyAll();
            }
        }
        public synchronized void waitData(int size, long timeout) {
            mReadSizeTarget = size;
            if (!isEnough()) {
                try {
                    wait(timeout);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        }
        public boolean isEnough() {
            return (mReadSize >= mReadSizeTarget);
        }
    }

    /*-----------------------------------------------------------------------*/

    @Override
    public void setSerial(SerialCommunicator comm) {
        mComm = comm;
    }

    @Override
    public void setConfig(AvrConf avrConf, AVRMem avrMem) {
        //mAVRConf = avrConf;
        mAVRMem = avrMem;
    }

    @Override
    public int open() {
        return 0; // always success
    }

    @Override
    public void enable() {
        // do nothing
    }

    @Override
    public int initialize() {
        return initiaizeImpl();
    }

    @Override
    public int check_sig_bytes() {
        byte[] sigBytes = sendCmdAndReceive(CMD_READ_SIGNATURE_BYTES, 3);
        if (sigBytes == null) {
            Log.e(TAG, "AVR109.check_sig_bytes(): failed to check signature bytes");
            return -1;
        }
        Log.d(TAG, "AVR109.check_sig_bytes(): signature bytes = ".concat(toHexStr(sigBytes, 3)));
        return 0;
    }

    @Override
    public int paged_read() {
        return pagedReadImpl();
    }

    @Override
    public int paged_write() {
        return pagedWriteImpl();
    }

    @Override
    public void disable() {
        if (!sendCmdAndVerify(CMD_LEAVE_PROGRAMMING_MODE)) {
            Log.e(TAG, "AVR109.disable(): failed to leave programming mode");
        }
        if (!sendCmdAndVerify(CMD_EXIT_BOOTLOADER)) {
            Log.e(TAG, "AVR109.disable(): failed to exit bootloader");
        }
        mComm.clearReadListener();
        mReadListener = null;
    }

    /*-----------------------------------------------------------------------*/

    private int initiaizeImpl() {
        mReadListener = new MyReadListener();
        mComm.addReadListener(mReadListener);

        byte[] softId = sendCmdAndReceive(CMD_RETURN_SOFTWARE_IDENTIFIER, 7);
        if (softId == null) {
            Log.e(TAG, "AVR109.initiaize(): failed to check software identifier");
            return -1;
        }
        Log.d(TAG, "AVR109.initiaize(): software identifier = ".concat(new String(softId)));
        byte[] softVer = sendCmdAndReceive(CMD_RETURN_SOFTWARE_VERSION, 2);
        if (softVer == null) {
            Log.e(TAG, "AVR109.initiaize(): failed to check software version");
            return -1;
        }
        byte programmerType = sendCmdAndReceiveOneByte(CMD_RETURN_PROGRAMMER_TYPE);
        Log.d(TAG, "AVR109.initiaize(): programmer type = ".concat(toHexStr(programmerType)));
        if (sendCmdAndReceiveOneByte(CMD_AUTO_INCREMENT_ADDRESS) != RSP_YES) {
            Log.e(TAG, "AVR109.initiaize(): auto increment address isn't supported");
            return -1;
        }
        byte[] blockSupport = sendCmdAndReceive(CMD_CHECK_BLOCK_SUPPORT, 3);
        if (blockSupport == null || blockSupport[0] != RSP_YES) {
            Log.e(TAG, "AVR109.initiaize(): block transfer isn't supported");
            return -1;
        }
        int bufferSize = byte2uint(blockSupport[1]) << 8 | byte2uint(blockSupport[2]);
        Log.d(TAG, "AVR109.initiaize(): buffer size = " + bufferSize);
        byte deviceCode = checkSupportedDeviceCodes();
        if (deviceCode == 0) {
            Log.e(TAG, "AVR109.initiaize(): failed to check supported device codes");
            return -1;
        }
        if (!selectDeviceType(deviceCode)) {
            Log.e(TAG, "AVR109.initiaize(): failed to select device type");
            return -1;
        }
        if (!sendCmdAndVerify(CMD_ENTER_PROGRAMMING_MODE)) {
            Log.e(TAG, "AVR109.initiaize(): failed to enter programming mode");
            return -1;
        }
        return 0;
    }

    private int pagedWriteImpl() {
        int pageSize = mAVRMem.page_size;
        int totalBytes = Math.min(mAVRMem.buf.length, mAVRMem.size);

        int addr = 0;
        int maxAddr = totalBytes;
        int blockSize;
        boolean useExtAddr = false;
        byte memoryType;

        if ("flash".equals(mAVRMem.desc)) {
            blockSize = pageSize;
            memoryType = 'F';
        } else if ("eeprom".equals(mAVRMem.desc)) {
            blockSize = 1; // Write to eeprom single bytes only
            memoryType = 'E';
        } else {
            Log.e(TAG, "Unknown memory type");
            return -1;
        }

        boolean setAddrRes = (useExtAddr) ? setExtAddress(addr) : setAddress(addr);
        if (!setAddrRes) {
            Log.e(TAG, "Failed to set address");
            return -1;
        }

        byte[] cmd = new byte[4 + blockSize];
        while (addr < maxAddr) {
            if (Thread.interrupted()) {
                report_cancel();
                return 0;
            }
            if ((maxAddr - addr) < blockSize) {
                blockSize = maxAddr - addr;
            }
            cmd[0] = CMD_START_BLOCK_LOAD;
            cmd[1] = (byte) ((blockSize >> 8) & 0xff);
            cmd[2] = (byte) (blockSize & 0xff);
            cmd[3] = memoryType;
            System.arraycopy(mAVRMem.buf, addr, cmd, 4, blockSize);
            if (!sendCmdAndVerify(cmd, 4 + blockSize)) {
                Log.e(TAG, "Failed to block write: addr=0x" + Integer.toHexString(addr));
                return -1;
            }
            addr += blockSize;
            report_progress((int) (addr * 100 / totalBytes));
        }
        return addr;
    }

    private int pagedReadImpl() {
        int pageSize = mAVRMem.page_size;
        int totalBytes = mAVRMem.size;

        int addr = 0;
        int maxAddr = totalBytes;
        int blockSize;
        boolean useExtAddr = false;
        byte memoryType;

        if ("flash".equals(mAVRMem.desc)) {
            blockSize = pageSize;
            memoryType = 'F';
        } else if ("eeprom".equals(mAVRMem.desc)) {
            blockSize = 1; // Read from eeprom single bytes only
            memoryType = 'E';
        } else {
            Log.e(TAG, "Unknown memory type");
            return -1;
        }

        boolean setAddrRes = (useExtAddr) ? setExtAddress(addr) : setAddress(addr);
        if (!setAddrRes) {
            Log.e(TAG, "Failed to set address");
            return -1;
        }

        byte[] cmd = new byte[4];
        mAVRMem.buf = new byte[maxAddr];
        while (addr < maxAddr) {
            if (Thread.interrupted()) {
                report_cancel();
                return 0;
            }
            if ((maxAddr - addr) < blockSize) {
                blockSize = maxAddr - addr;
            }
            cmd[0] = CMD_START_BLOCK_READ;
            cmd[1] = (byte) ((blockSize >> 8) & 0xff);
            cmd[2] = (byte) (blockSize & 0xff);
            cmd[3] = memoryType;
            byte[] data = sendCmdAndReceive(cmd, 4, blockSize);
            if (data == null) {
                Log.e(TAG, "Failed to block read: addr=0x" + Integer.toHexString(addr));
                return -1;
            }
            System.arraycopy(data, 0, mAVRMem.buf, addr, blockSize);
            addr += blockSize;
            report_progress((int) (addr * 100 / totalBytes));
        }
        return addr;
    }

    /*-----------------------------------------------------------------------*/

    private boolean sendCmdAndVerify(byte cmd) {
        return sendCmdAndVerify(new byte[] { cmd }, 1);
    }

    private boolean sendCmdAndVerify(byte[] buf, int length) {
        byte[] recv = new byte[1];
        int done = write(buf, length);
        if (done == length) {
            read(recv, 1);
        }
        return recv[0] == RSP_SUCCESS;
    }

    private boolean setAddress(int addr) {
        byte[] cmd = new byte[] {
                CMD_SET_ADDRESS, (byte) ((addr >> 8) & 0xff), (byte) (addr & 0xff) };
        return sendCmdAndVerify(cmd, 3);
    }

    private boolean setExtAddress(int addr) {
        byte[] cmd = new byte[] {
                CMD_SET_EXTADDRESS, (byte) ((addr >> 16) & 0xff),
                (byte) ((addr >> 8) & 0xff), (byte) (addr & 0xff) };
        return sendCmdAndVerify(cmd, 4);
    }

    private boolean selectDeviceType(byte type) {
        byte[] cmd = new byte[] {
                CMD_SELECT_DEVICE_TYPE, type };
        return sendCmdAndVerify(cmd, 2);
    }

    private byte[] sendCmdAndReceive(byte cmd, int recvLength) {
        return sendCmdAndReceive(new byte[] { cmd }, 1, recvLength);
    }

    private byte[] sendCmdAndReceive(byte[] buf, int length, int recvLength) {
        int done = write(buf, length);
        if (done == length) {
            byte[] recv = new byte[recvLength];
            done = read(recv, recvLength);
            if (done == recvLength) {
                return recv;
            }
        }
        return null;
    }

    private byte sendCmdAndReceiveOneByte(byte cmd) {
        byte[] ret = sendCmdAndReceive(new byte[] { cmd }, 1, 1);
        return (ret != null) ? ret[0] : 0;
    }

    private byte checkSupportedDeviceCodes() {
        byte ret = 0;
        byte[] buf = new byte[] { CMD_RETURN_SUPPORTED_DEVICE_CODES };
        if (write(buf, 1) == 1) {
            while (read(buf, 1) == 1) {
                if (buf[0] == RSP_TERMINATE) {
                    break;
                }
                if (ret == 0) {
                    ret = buf[0];
                }
            }
        }
        return ret;
    }

    private int read(byte[] buf, int length) {
        int retval = 0;
        mReadListener.waitData(length, READ_TIMEOUT_MSEC);
        retval = mComm.read(buf, length);
        if (retval > 0) {
            mReadListener.mReadSize -= retval;
            if (DEBUG_SHOW_READ) {
                Log.d(TAG, "read(" + retval + ") : " + toHexStr(buf, retval));
            }
        }
        return retval;
    }

    private int write(byte[] buf, int length) {
        int retval;
        retval = mComm.write(buf, length);
        if (DEBUG_SHOW_WRITE) {
            if (retval > 0) {
                Log.d(TAG, "write(" + retval + ") : " + toHexStr(buf, retval));
            }
        }
        return retval;
    }

    private int byte2uint(byte b) {
        return (b < 0) ? b + 256 : b;
    }

    private String toHexStr(byte b) {
        return String.format("0x%02x", b);
    }

    private String toHexStr(byte[] b, int length) {
        StringBuffer strBuf = new StringBuffer();
        for (int i = 0; i < length; i++) {
            strBuf.append(toHexStr(b[i])).append(' ');
        }
        strBuf.setLength(strBuf.length() - 1);
        return strBuf.toString();
    }

}
