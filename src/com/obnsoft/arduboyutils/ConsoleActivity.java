package com.obnsoft.arduboyutils;

import java.io.UnsupportedEncodingException;

import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.usb.driver.uart.ReadListener;
import com.physicaloid.lib.usb.driver.uart.UartConfig;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

public class ConsoleActivity extends Activity {

    private static final byte[][] WRITE_WITH_CHAR_LIST = {
            new byte[] { '\r' }, new byte[] { '\n' }, new byte[] { '\r', '\n' } };

    private Physicaloid mPhysicaloid;

    private ScrollView  mScrollView;
    private TextView    mTextViewConsole;
    private Spinner     mSpinnerBaudRate;
    private Spinner     mSpinnerWriteWith;
    private CheckBox    mCheckBoxConsoleEcho;
    private CheckBox    mCheckBoxConsoleScroll;
    private CaptureView mCaptureView;

    private int         mBaudRateItemIdx = 2;
    private int         mWriteWithItemIdx = 1;
    private boolean     mIsScreenCapture;

    private Handler     mHandler = new Handler();
    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                finish();
            }
        }
    };

    /*-----------------------------------------------------------------------*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        setContentView(R.layout.console_activity);
        mScrollView = (ScrollView) findViewById(R.id.scrollViewConsole);
        mTextViewConsole = (TextView) findViewById(R.id.textViewConsole);
        mSpinnerBaudRate = (Spinner) findViewById(R.id.spinnerBaudRate);
        mSpinnerWriteWith = (Spinner) findViewById(R.id.spinnerWriteWith);
        mCheckBoxConsoleEcho = (CheckBox) findViewById(R.id.checkBoxConsoleEcho);
        mCheckBoxConsoleScroll = (CheckBox) findViewById(R.id.checkBoxConsoleScroll);
        mCaptureView = (CaptureView) findViewById(R.id.screenCaptureView);

        mSpinnerBaudRate.setSelection(mBaudRateItemIdx);
        mSpinnerBaudRate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mBaudRateItemIdx != position) {
                    mBaudRateItemIdx = position;
                    mPhysicaloid.setBaudrate(getBaudRateFromIndex(position));
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });
        mSpinnerWriteWith.setSelection(mWriteWithItemIdx);
        mSpinnerWriteWith.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mWriteWithItemIdx = position;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });

        mPhysicaloid = ((MyApplication) getApplication()).getPhysicaloidInstance();
        registerReceiver(mUsbReceiver, MyApplication.USB_RECEIVER_FILTER);
        if (!openDevice()) {
            Utils.showToast(this, R.string.messageDeviceOpenFailed);
            finish();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.console, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.menuConsoleCaptureMode).setVisible(!mIsScreenCapture);
        menu.findItem(R.id.menuConsoleMonitorMode).setVisible(mIsScreenCapture);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            break;
        case R.id.menuConsoleCaptureMode:
            mIsScreenCapture = true;
            clearConsoleBuffer();
            mTextViewConsole.setVisibility(View.INVISIBLE);
            mCaptureView.setVisibility(View.VISIBLE);
            invalidateOptionsMenu();
            return true;
        case R.id.menuConsoleMonitorMode:
            mIsScreenCapture = false;
            mTextViewConsole.setVisibility(View.VISIBLE);
            mCaptureView.setVisibility(View.INVISIBLE);
            invalidateOptionsMenu();
            return true;
        case R.id.menuConsoleKeyboard:
            InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            inputMethodManager.toggleSoftInputFromWindow(
                    mTextViewConsole.getApplicationWindowToken(), InputMethodManager.SHOW_IMPLICIT,
                    0);
            return true;
        case R.id.menuConsoleClear:
            clearConsoleBuffer();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int code = event.getUnicodeChar();
        byte[] buf = null;
        if (code == 0x0a) {
            buf = WRITE_WITH_CHAR_LIST[mWriteWithItemIdx];
        } else if (code > 0x00 && code <= 0xff) {
            buf = new byte[] { (byte) code };
        }
        if (buf != null) {
            mPhysicaloid.write(buf, buf.length);
            if (!mIsScreenCapture && mCheckBoxConsoleEcho.isChecked()) {
                appendMessage(String.valueOf((char) code));
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        mCaptureView.onDestroy();
        unregisterReceiver(mUsbReceiver);
        closeDevice();
        super.onDestroy();
    }

    /*-----------------------------------------------------------------------*/

    private boolean openDevice() {
        UartConfig config = new UartConfig(getBaudRateFromIndex(mBaudRateItemIdx),
                UartConfig.DATA_BITS8, UartConfig.STOP_BITS1, UartConfig.PARITY_NONE,
                true, true);
        if (mPhysicaloid.isOpened()) {
            mPhysicaloid.setConfig(config);
        } else if (!mPhysicaloid.open(config)) {
            return false; // open failed.
        }
        return mPhysicaloid.addReadListener(new ReadListener() {
            @Override
            public void onRead(int size) {
                byte[] buf = new byte[size];
                mPhysicaloid.read(buf, size);
                if (mIsScreenCapture) {
                    mCaptureView.appendData(buf);
                } else {
                    try {
                        appendMessage(new String(buf, "ISO-8859-1"));
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void closeDevice() {
        mPhysicaloid.clearReadListener();
        mPhysicaloid.close();
    }

    private int getBaudRateFromIndex(int index) {
        return Integer.parseInt((String) mSpinnerBaudRate.getItemAtPosition(index));
    }

    private void appendMessage(final CharSequence text) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mTextViewConsole.append(text);
                if (mCheckBoxConsoleScroll.isChecked()) {
                    mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                }
            }
        });
    }

    private void clearConsoleBuffer() {
        mTextViewConsole.setText(null);
        mCaptureView.reset();
    }

}
