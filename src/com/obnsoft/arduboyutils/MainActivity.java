package com.obnsoft.arduboyutils;

import com.physicaloid.lib.Boards;
import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.Physicaloid.UploadCallBack;
import com.physicaloid.lib.programmer.avr.AvrTask;
import com.physicaloid.lib.programmer.avr.TransferErrors;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int PICK_FILE_REQUEST = 0;
    private static final String[] SUPPORT_EXTENSIONS =
            new String[] { AvrTask.EXT_ARDUBOY, AvrTask.EXT_HEX, AvrTask.EXT_EEPROM };

    private Physicaloid mPhysicaloid;
    private String      mHexFilePath = null;
    private boolean     mIsExecuting = false;

    private Button      mButtonPickFile;
    private Button      mButtonExecute;
    private Button      mButtonCancel;
    private ProgressBar mProgressBarExecute;
    private TextView    mTextViewMessage;

    private Handler     mHandler = new Handler();
    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            handleIntent(intent);
        }
    };
    private UploadCallBack mUploadCallback = new UploadCallBack() {
        @Override
        public void onPreUpload() {
            appendMessage("Start procedure.\n");
        }
        @Override
        public void onUploading(int value) {
            setUploadProgressFromThread(value);
        }
        @Override
        public void onCancel() {
            appendMessage("Canceled\n");
            controlUiAvalabilityFromThread();
        }
        @Override
        public void onError(TransferErrors err) {
            appendMessage("Error  : " + err.toString() + "\n");
            controlUiAvalabilityFromThread();
        }
        @Override
        public void onPostUpload(boolean success) {
            if(success) {
                setUploadProgress(0);
                appendMessage("Succeeded!!\n");
                mHexFilePath = null;
            } else {
                appendMessage("Failed...\n");
            }
            mIsExecuting = false;
            controlUiAvalabilityFromThread();
        }
    };

    /*-----------------------------------------------------------------------*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        mButtonPickFile = (Button) findViewById(R.id.buttonPickFile);
        mButtonExecute = (Button) findViewById(R.id.buttonExecute);
        mButtonCancel = (Button) findViewById(R.id.buttonCancel);
        mProgressBarExecute = (ProgressBar) findViewById(R.id.pbarUpload);
        mTextViewMessage = (TextView) findViewById(R.id.textViewMessage);

        mPhysicaloid = ((MyApplication) getApplication()).getPhysicaloidInstance();
        Intent intent = getIntent();
        if (intent != null) {
            handleIntent(intent);
        }
        registerReceiver(mUsbReceiver, MyApplication.USB_RECEIVER_FILTER);
        controlUiAvalability();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menuMainConsole:
            startActivity(new Intent(this, ConsoleActivity.class));
            return true;
        case R.id.menuMainAbout:
            ((MyApplication) getApplication()).showVersion(this);
            return true;
        }
        return false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_FILE_REQUEST) {
            if (resultCode == RESULT_OK) {
                mHexFilePath = data.getStringExtra(FilePickerActivity.INTENT_EXTRA_SELECTPATH);
                mButtonExecute.setEnabled(true);
            } else {
                mHexFilePath = null;
                mButtonExecute.setEnabled(false);
            }
            controlUiAvalability();
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        mPhysicaloid.clearReadListener();
        mPhysicaloid.close();
        super.onDestroy();
    }

    /*-----------------------------------------------------------------------*/

    public void onClickPickFile(View v) {
        Intent intent = new Intent(this, FilePickerActivity.class);
        intent.putExtra(FilePickerActivity.INTENT_EXTRA_EXTENSIONS, SUPPORT_EXTENSIONS);
        startActivityForResult(intent, PICK_FILE_REQUEST);
    }

    public void onClickExecute(View v) {
        if (mPhysicaloid.isOpened() || mPhysicaloid.open()) {
            mPhysicaloid.setBaudrate(1200); // Switch Arduboy to AVR mode
            mPhysicaloid.close();
            mIsExecuting = true;
        } else {
            // Error case
            mIsExecuting = false;
        }
        controlUiAvalability();
    }

    public void onClickCancel(View v) {
        if (mIsExecuting) {
            mPhysicaloid.cancelUpload();
        }
    }

    /*-----------------------------------------------------------------------*/

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            if (mIsExecuting) {
                try {
                    setUploadProgress(0);
                    mPhysicaloid.upload(Boards.ARDUINO_LEONARD, mHexFilePath, mUploadCallback);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            mPhysicaloid.clearReadListener();
            mPhysicaloid.close();
        }
    }

    private void appendMessage(final CharSequence text) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mTextViewMessage.append(text);
            }
        });
    }

    private void controlUiAvalability() {
        mButtonPickFile.setEnabled(!mIsExecuting);
        mButtonExecute.setEnabled(mHexFilePath != null && !mIsExecuting);
        mButtonCancel.setEnabled(mIsExecuting);
    }

    private void controlUiAvalabilityFromThread() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                controlUiAvalability();
            }
        });
    }

    private void setUploadProgress(int progress) {
        mProgressBarExecute.setProgress(progress);
    }

    private void setUploadProgressFromThread(final int progress) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                setUploadProgress(progress);
            }
        });
    }

}
