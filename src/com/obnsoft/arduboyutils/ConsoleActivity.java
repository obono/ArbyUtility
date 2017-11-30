package com.obnsoft.arduboyutils;

import java.io.UnsupportedEncodingException;

import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.usb.driver.uart.ReadListener;
import com.physicaloid.lib.usb.driver.uart.UartConfig;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

public class ConsoleActivity extends Activity {

    private static final String[] WRITE_WITH_CHAR_LIST = { null, "\r", "\n", "\r\n" };
    private static final int DEVICE_SCREEN_WIDTH = 128;
    private static final int DEVICE_SCREEN_HEIGHT = 64;
    private static final int BITS_PER_BYTE = 8;
    private static final int DEVICE_SCREEN_BUFFER_SIZE =
            DEVICE_SCREEN_WIDTH * DEVICE_SCREEN_HEIGHT / BITS_PER_BYTE;

    private Physicaloid mPhysicaloid;

    private Button      mButtonWrite;
    private EditText    mEditTextWrite;
    private ScrollView  mScrollView;
    private TextView    mTextViewConsole;
    private Spinner     mSpinnerBaudRate;
    private Spinner     mSpinnerWriteWith;
    private CheckBox    mCheckBoxConsoleEcho;
    private CheckBox    mCheckBoxConsoleScroll;
    private ImageView   mImageViewScreen;

    private int         mBaudRateItemIdx = 2;
    private String      mWriteWithChar;
    private boolean     mIsScreenCapture;
    private Bitmap      mBitmapScreen;
    private byte[]      mBitmapBuffer = new byte[DEVICE_SCREEN_BUFFER_SIZE];
    private int         mBitmapBufferPos;

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
        mButtonWrite = (Button) findViewById(R.id.buttonWrite);
        mEditTextWrite = (EditText) findViewById(R.id.editTextWrite);
        mScrollView = (ScrollView) findViewById(R.id.scrollViewConsole);
        mTextViewConsole = (TextView) findViewById(R.id.textViewConsole);
        mSpinnerBaudRate = (Spinner) findViewById(R.id.spinnerBaudRate);
        mSpinnerWriteWith = (Spinner) findViewById(R.id.spinnerWriteWith);
        mCheckBoxConsoleEcho = (CheckBox) findViewById(R.id.checkBoxConsoleEcho);
        mCheckBoxConsoleScroll = (CheckBox) findViewById(R.id.checkBoxConsoleScroll);
        mImageViewScreen = (ImageView) findViewById(R.id.imageViewSceeen);

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        mEditTextWrite.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    onClickWrite(v);
                    return true;
                }
                return false;
            }
        });
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
        mSpinnerWriteWith.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mWriteWithChar = WRITE_WITH_CHAR_LIST[position];
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        });
        mBitmapScreen = Bitmap.createBitmap(128, 64, Bitmap.Config.RGB_565);
        mImageViewScreen.setImageBitmap(mBitmapScreen);

        mPhysicaloid = ((MyApplication) getApplication()).getPhysicaloidInstance();
        registerReceiver(mUsbReceiver, MyApplication.USB_RECEIVER_FILTER);
        if (!openDevice()) {
            mButtonWrite.setEnabled(false);
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
            mImageViewScreen.setVisibility(View.VISIBLE);
            invalidateOptionsMenu();
            return true;
        case R.id.menuConsoleMonitorMode:
            mIsScreenCapture = false;
            mTextViewConsole.setVisibility(View.VISIBLE);
            mImageViewScreen.setVisibility(View.INVISIBLE);
            invalidateOptionsMenu();
            return true;
        case R.id.menuConsoleClear:
            clearConsoleBuffer();
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        closeDevice();
        mBitmapScreen.recycle();
        super.onDestroy();
    }

    /*-----------------------------------------------------------------------*/

    public void onClickWrite(View v) {
        String str = mEditTextWrite.getText().toString();
        if (mWriteWithChar != null) {
            str = str.concat(mWriteWithChar);
        }
        if (str.length() > 0) {
            if (mCheckBoxConsoleEcho.isChecked()) {
                appendMessage(str);
            }
            byte[] buf = str.getBytes();
            mPhysicaloid.write(buf, buf.length);
            mEditTextWrite.setText(null);
        }
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
                    appendScreenBuffer(buf);
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

    private void appendScreenBuffer(byte[] data) {
        int dataSize = data.length;
        for (int dataPos = 0; dataPos < dataSize; ) {
            int size = Math.min(dataSize - dataPos, DEVICE_SCREEN_BUFFER_SIZE - mBitmapBufferPos);
            System.arraycopy(data, dataPos, mBitmapBuffer, mBitmapBufferPos, size);
            mBitmapBufferPos += size;
            if (mBitmapBufferPos == DEVICE_SCREEN_BUFFER_SIZE) {
                updateScreenImage();
                mBitmapBufferPos = 0;
            }
            dataPos += size;
        }
    }

    private void updateScreenImage() {
        int pos = 0;
        for (int dy = 0; dy < DEVICE_SCREEN_HEIGHT; dy += BITS_PER_BYTE) {
            for (int x = 0; x < DEVICE_SCREEN_WIDTH; x++) {
                int val = mBitmapBuffer[pos++];
                for (int y = 0; y < BITS_PER_BYTE; y++) {
                    int color = ((val & 1) != 0) ? Color.WHITE : Color.BLACK;
                    mBitmapScreen.setPixel(x, dy + y, color);
                    val >>= 1;
                }
            }
        }
        mImageViewScreen.postInvalidate();
    }

    private void clearConsoleBuffer() {
        mTextViewConsole.setText(null);
        mBitmapBufferPos = 0;
    }

}
