package com.obnsoft.arduboyutils;

import java.io.UnsupportedEncodingException;

import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.usb.driver.uart.ReadListener;
import com.physicaloid.lib.usb.driver.uart.UartConfig;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
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

    private static final String PREFS_KEY_BAUD_RATE = "baud_rate";
    private static final String PREFS_KEY_NEW_LINE = "new_line";
    private static final String PREFS_KEY_ECHO = "echo";
    private static final String PREFS_KEY_SCROLL = "scroll";

    private static final int BAUD_RATE_ITEM_IDX_DEFAULT = 2;
    private static final int NEW_LINE_ITEM_IDX_DEFAULT = 1;
    private static final boolean ECHO_DEFAULT = false;
    private static final boolean SCROLL_DEFAULT = true;

    private static final int BYTE_CODE_NEW_LINE = '\n';
    private static final int BYTE_CODE_MIN = 0x01;
    private static final int BYTE_CODE_MAX = 0xff;
    private static final byte[][] NEWLINE_CHAR_LIST = {
            new byte[] { '\r' }, new byte[] { '\n' }, new byte[] { '\r', '\n' } };

    private MyApplication mApp;
    private Physicaloid mPhysicaloid;

    private ScrollView  mScrollView;
    private TextView    mTextViewConsole;
    private Spinner     mSpinnerBaudRate;
    private Spinner     mSpinnerNewLine;
    private CheckBox    mCheckBoxEcho;
    private CheckBox    mCheckBoxScroll;
    private CaptureView mCaptureView;

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
        mSpinnerNewLine = (Spinner) findViewById(R.id.spinnerNewLine);
        mCheckBoxEcho = (CheckBox) findViewById(R.id.checkBoxEcho);
        mCheckBoxScroll = (CheckBox) findViewById(R.id.checkBoxScroll);
        mCaptureView = (CaptureView) findViewById(R.id.screenCaptureView);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        mSpinnerBaudRate.setSelection(
                prefs.getInt(PREFS_KEY_BAUD_RATE, BAUD_RATE_ITEM_IDX_DEFAULT), false);
        mSpinnerBaudRate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (mPhysicaloid != null) {
                    mPhysicaloid.setBaudrate(getBaudRateConfig());
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });

        mSpinnerNewLine.setSelection(
                prefs.getInt(PREFS_KEY_NEW_LINE, NEW_LINE_ITEM_IDX_DEFAULT), false);
        mCheckBoxEcho.setChecked(prefs.getBoolean(PREFS_KEY_ECHO, ECHO_DEFAULT));
        mCheckBoxScroll.setChecked(prefs.getBoolean(PREFS_KEY_SCROLL, SCROLL_DEFAULT));

        mApp = (MyApplication) getApplication();
        mPhysicaloid = mApp.getPhysicaloidInstance();
        registerReceiver(mUsbReceiver, MyApplication.USB_RECEIVER_FILTER);
        if (openDevice()) {
            mApp.acquireWakeLock();
        } else {
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
        if (code == BYTE_CODE_NEW_LINE) {
            buf = NEWLINE_CHAR_LIST[mSpinnerNewLine.getSelectedItemPosition()];
        } else if (code >= BYTE_CODE_MIN && code <= BYTE_CODE_MAX) {
            buf = new byte[] { (byte) code };
        }
        if (buf != null) {
            mPhysicaloid.write(buf, buf.length);
            if (!mIsScreenCapture && mCheckBoxEcho.isChecked()) {
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
        commitCurrentConfig();
        mApp.releaseWakeLock();
        super.onDestroy();
    }

    /*-----------------------------------------------------------------------*/

    private boolean openDevice() {
        UartConfig config = new UartConfig(getBaudRateConfig(),
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

    private int getBaudRateConfig() {
        return Integer.parseInt((String) mSpinnerBaudRate.getSelectedItem());
    }

    private void appendMessage(final CharSequence text) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mTextViewConsole.append(text);
                if (mCheckBoxScroll.isChecked()) {
                    mScrollView.fullScroll(ScrollView.FOCUS_DOWN);
                }
            }
        });
    }

    private void clearConsoleBuffer() {
        mPhysicaloid.clearBuffer();
        mTextViewConsole.setText(null);
        mCaptureView.reset();
    }

    private void commitCurrentConfig() {
        SharedPreferences.Editor editor =
                PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putInt(PREFS_KEY_BAUD_RATE, mSpinnerBaudRate.getSelectedItemPosition());
        editor.putInt(PREFS_KEY_NEW_LINE, mSpinnerNewLine.getSelectedItemPosition());
        editor.putBoolean(PREFS_KEY_ECHO, mCheckBoxEcho.isChecked());
        editor.putBoolean(PREFS_KEY_SCROLL, mCheckBoxScroll.isChecked());
        editor.commit();
    }

}
