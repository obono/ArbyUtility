package com.obnsoft.arduboyutils;

import java.io.File;
import java.util.ArrayList;

import com.physicaloid.lib.Boards;
import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.Physicaloid.UploadCallBack;
import com.physicaloid.lib.programmer.avr.AvrTask;
import com.physicaloid.lib.programmer.avr.AvrTask.Op;
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
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity {

    private static final String[] SUPPORT_EXTENSIONS_DOWNLOAD_FLASH =
            new String[] { AvrTask.EXT_HEX };
    private static final String[] SUPPORT_EXTENSIONS_UPLOAD_FLASH =
            new String[] { AvrTask.EXT_HEX, AvrTask.EXT_ARDUBOY };
    private static final String[] SUPPORT_EXTENSIONS_EEPROM =
            new String[] { AvrTask.EXT_EEPROM, AvrTask.EXT_HEX };

    private Physicaloid     mPhysicaloid;
    private boolean         mIsExecuting = false;

    private OperationInfo[] mOperationInfos;
    private Button          mButtonExecute;
    private Button          mButtonCancel;
    private ProgressBar     mProgressBarExecute;
    private TextView        mTextViewMessage;

    private Handler         mHandler = new Handler();
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
            if (success) {
                setUploadProgress(0);
                appendMessage("Succeeded!!\n");
                for (OperationInfo info : mOperationInfos) {
                    info.mFilePath = null;
                }
            } else {
                appendMessage("Failed...\n");
            }
            mIsExecuting = false;
            controlUiAvalabilityFromThread();
        }
    };

    /*-----------------------------------------------------------------------*/

    private class OperationInfo {
        private ToggleButton    mToggleButton;
        private Button          mButtonPickFile;
        private TextView        mTextViewOperation;
        private TextView        mTextViewFilename;
        private AvrTask.Op      mOperation;
        private boolean         mIsActive;
        private String          mFilePath;
        private String[]        mSupportExtentions;

        public OperationInfo(int parentId, int labelId, AvrTask.Op operation,
                String[] supportExtentions) {
            RelativeLayout parentView = (RelativeLayout) MainActivity.this.findViewById(parentId);
            mToggleButton = (ToggleButton) parentView.findViewById(R.id.toggleButton);
            mButtonPickFile = (Button) parentView.findViewById(R.id.buttonPickFile);
            mTextViewOperation = (TextView) parentView.findViewById(R.id.textViewOperation);
            mTextViewFilename = (TextView) parentView.findViewById(R.id.textViewFileName);

            mOperation = operation;
            mSupportExtentions = supportExtentions;

            parentView.setTag(this);
            mIsActive = (operation == Op.UPLOAD_FLASH);
            mToggleButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    OperationInfo info = (OperationInfo) ((View) v.getParent()).getTag();
                    info.mIsActive = mToggleButton.isChecked();
                    controlUiAvalability();
                }
            });
            mTextViewOperation.setText(labelId);
        }
    }

    /*-----------------------------------------------------------------------*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        mButtonExecute = (Button) findViewById(R.id.buttonExecute);
        mButtonCancel = (Button) findViewById(R.id.buttonCancel);
        mProgressBarExecute = (ProgressBar) findViewById(R.id.pbarUpload);
        mTextViewMessage = (TextView) findViewById(R.id.textViewMessage);
        mOperationInfos = new OperationInfo[] {
                new OperationInfo(R.id.viewOperationDownloadFlash,
                        R.string.textViewOprationDownloadFlash,
                        Op.DOWNLOAD_FLASH, SUPPORT_EXTENSIONS_DOWNLOAD_FLASH),
                new OperationInfo(R.id.viewOperationDownloadEeprom,
                        R.string.textViewOprationDownloadEeprom,
                        Op.DOWNLOAD_EEPROM, SUPPORT_EXTENSIONS_EEPROM),
                new OperationInfo(R.id.viewOperationUploadFlash,
                        R.string.textViewOprationUploadFlash, Op.UPLOAD_FLASH,
                        SUPPORT_EXTENSIONS_UPLOAD_FLASH),
                new OperationInfo(R.id.viewOperationUploadEeprom,
                        R.string.textViewOprationUploadEeprom,
                        Op.UPLOAD_EEPROM, SUPPORT_EXTENSIONS_EEPROM)
        };

        mPhysicaloid = ((MyApplication) getApplication()).getPhysicaloidInstance();
        Intent intent = getIntent();
        if (intent != null) {
            handleIntent(intent);
        }
        controlUiAvalability();
        registerReceiver(mUsbReceiver, MyApplication.USB_RECEIVER_FILTER);
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
            Utils.showVersion(this);
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
        for (OperationInfo info : mOperationInfos) {
            if (requestCode == info.hashCode()) {
                info.mFilePath = (resultCode == RESULT_OK) ?
                        data.getStringExtra(FilePickerActivity.INTENT_EXTRA_SELECTPATH) : null;
                controlUiAvalability();
            }
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        mPhysicaloid.clearReadListener();
        mPhysicaloid.close();
        Utils.cleanCacheFiles(this);
        super.onDestroy();
    }

    /*-----------------------------------------------------------------------*/

    public void onClickPickFile(View v) {
        OperationInfo info = (OperationInfo) ((View) v.getParent()).getTag();
        AvrTask.Op operation = info.mOperation;
        boolean isOut = (operation == Op.DOWNLOAD_FLASH || operation == Op.DOWNLOAD_EEPROM);
        Intent intent = new Intent(this, FilePickerActivity.class);
        intent.putExtra(FilePickerActivity.INTENT_EXTRA_EXTENSIONS, info.mSupportExtentions);
        intent.putExtra(FilePickerActivity.INTENT_EXTRA_WRITEMODE, isOut);
        startActivityForResult(intent, info.hashCode());
    }

    public void onClickExecute(View v) {
        if (mPhysicaloid.isOpened() || mPhysicaloid.open()) {
            mPhysicaloid.setBaudrate(1200); // Switch Arduboy to AVR mode
            mPhysicaloid.close();
            mIsExecuting = true;
        } else {
            Utils.showToast(this, R.string.messageDeviceOpenFailed);
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
                    ArrayList<AvrTask> tasks = new ArrayList<AvrTask>();
                    for (OperationInfo info : mOperationInfos) {
                        if (info.mToggleButton.isChecked()) {
                            tasks.add(new AvrTask(
                                    info.mOperation, new File(info.mFilePath)));
                        }
                    }
                    setUploadProgress(0);
                    mPhysicaloid.processTasks(tasks, Boards.ARDUINO_LEONARD, mUploadCallback);
                } catch (Exception e) {
                    e.printStackTrace();
                    mIsExecuting = false;
                    controlUiAvalability();
                }
            }
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            mPhysicaloid.clearReadListener();
            mPhysicaloid.close();
        } else if (Intent.ACTION_VIEW.equals(action)) {
            mOperationInfos[2].mFilePath = Utils.getPathFromUri(this, intent.getData());
            controlUiAvalability();
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
        boolean isAnyEnabled = false;
        boolean isAllAvailable = true;

        for (OperationInfo info : mOperationInfos) {
            boolean enabled = info.mIsActive;
            info.mToggleButton.setChecked(enabled);
            info.mButtonPickFile.setEnabled(enabled);
            info.mTextViewOperation.setEnabled(enabled);
            info.mTextViewFilename.setEnabled(enabled);

            String filePath = info.mFilePath;
            if (filePath != null) {
                info.mTextViewFilename.setText((new File(filePath)).getName());
            } else {
                info.mTextViewFilename.setText(R.string.textViewFileNotSpecified);
            }

            isAnyEnabled = isAnyEnabled || enabled;
            if (enabled) {
                isAllAvailable = isAllAvailable && (info.mFilePath != null);
            }
            info.mToggleButton.setEnabled(!mIsExecuting);
        }

        mButtonExecute.setEnabled(isAnyEnabled && isAllAvailable && !mIsExecuting);
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
