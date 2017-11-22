package com.obnsoft.arduboyutils;

import java.io.UnsupportedEncodingException;

import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.usb.driver.uart.ReadListener;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class ConsoleActivity extends Activity {

    private Physicaloid mPhysicaloid;

    private Button      mButtonWrite;
    private EditText    mEditTextWrite;
    private TextView    mTextViewMessage;

    private Handler     mHandler = new Handler();
    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                finish();
            }
        }
    };
    private ReadListener mReadListener = new ReadListener() {
        @Override
        public void onRead(int size) {
            byte[] buf = new byte[size];
            mPhysicaloid.read(buf, size);
            try {
                String readStr = new String(buf, "US-ASCII");
                appendMessage(readStr);
            } catch (UnsupportedEncodingException e) {
                // do nothing
            }
        }
    };

    /*-----------------------------------------------------------------------*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.console_activity);
        mButtonWrite = (Button) findViewById(R.id.buttonWrite);
        mEditTextWrite = (EditText) findViewById(R.id.editTextWrite);
        mTextViewMessage = (TextView) findViewById(R.id.textViewMessage);

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

        mPhysicaloid = ((MyApplication) getApplication()).getPhysicaloidInstance();
        registerReceiver(mUsbReceiver, MyApplication.USB_RECEIVER_FILTER);
        if (!openDevice()) {
            mButtonWrite.setEnabled(false);
            Utils.showToast(this, R.string.messageDeviceOpenFailed);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        closeDevice();
        super.onDestroy();
    }

    /*-----------------------------------------------------------------------*/

    public void onClickWrite(View v) {
        String str = mEditTextWrite.getText().toString();
        if (str.length() > 0) {
            byte[] buf = str.getBytes();
            mPhysicaloid.write(buf, buf.length);
            mEditTextWrite.setText(null);
        }
    }

    /*-----------------------------------------------------------------------*/

    private boolean openDevice() {
        boolean ret = mPhysicaloid.isOpened() || mPhysicaloid.open();
        if (ret) {
            mPhysicaloid.addReadListener(mReadListener);
        }
        return ret;
    }

    private void closeDevice() {
        mPhysicaloid.clearReadListener();
        mPhysicaloid.close();
    }

    private void appendMessage(final CharSequence text) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mTextViewMessage.append(text);
            }
        });
    }

}
