package com.obnsoft.arduboyutils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

import com.obnsoft.arduboyutils.MyAsyncTaskWithDialog.Result;
import com.physicaloid.lib.Boards;
import com.physicaloid.lib.Physicaloid;
import com.physicaloid.lib.Physicaloid.ProcessCallBack;
import com.physicaloid.lib.programmer.avr.AvrTask;
import com.physicaloid.lib.programmer.avr.AvrTask.Op;
import com.physicaloid.lib.programmer.avr.TransferErrors;

import android.app.Activity;
import android.app.ProgressDialog;
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

    private BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            handleIntent(intent);
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
        getMenuInflater().inflate(R.menu.main, menu);
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

    /*-----------------------------------------------------------------------*/

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            if (mIsExecuting) {
                executeOperations();
            }
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            mPhysicaloid.clearReadListener();
            mPhysicaloid.close();
        } else if (Intent.ACTION_VIEW.equals(action)) {
            mOperationInfos[2].mFilePath = Utils.getPathFromUri(this, intent.getData());
            controlUiAvalability();
        }
    }

    private void controlUiAvalability() {
        boolean isAnyEnabled = false;
        boolean isAllAvailable = true;

        for (OperationInfo info : mOperationInfos) {
            boolean enabled = info.mIsActive;
            info.mToggleButton.setChecked(enabled);
            info.mToggleButton.setEnabled(enabled && !mIsExecuting);
            info.mButtonPickFile.setEnabled(enabled && !mIsExecuting);
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
    }

    /*-----------------------------------------------------------------------*/

    private void executeOperations() {
        final ArrayList<AvrTask> operations = new ArrayList<AvrTask>();
        try {
            for (OperationInfo info : mOperationInfos) {
                if (info.mToggleButton.isChecked()) {
                    operations.add(new AvrTask(
                            info.mOperation, new File(info.mFilePath)));
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            Utils.showToast(MainActivity.this, R.string.messageExecutingFileNotFound);
            mIsExecuting = false;
            controlUiAvalability();
            return;
        }

        final Handler handler = new Handler();
        MyAsyncTaskWithDialog.ITask task = new MyAsyncTaskWithDialog.ITask() {
            private AvrTask.Op  mCurrentOperation = null;
            private String      mErrorMessage = null;

            @Override
            public Boolean task(final ProgressDialog dialog) {
                ProcessCallBack callback = new ProcessCallBack() {
                    @Override
                    public void onPreProcess() {
                        dialog.setIndeterminate(false);
                        dialog.setProgress(0);
                    }
                    @Override
                    public void onProcessing(AvrTask.Op operation, int value) {
                        dialog.setProgress(value);
                        if (mCurrentOperation != operation) {
                            mCurrentOperation = operation;
                            final int resId;
                            switch (operation) {
                            case DOWNLOAD_FLASH:
                                resId = R.string.messageExecutingDownloadFlash;
                                break;
                            case DOWNLOAD_EEPROM:
                                resId = R.string.messageExecutingDownloadEeprom;
                                break;
                            case UPLOAD_FLASH:
                                resId = R.string.messageExecutingUploadFlash;
                                break;
                            case UPLOAD_EEPROM:
                                resId = R.string.messageExecutingUploadEeprom;
                                break;
                            default:
                                resId = 0;
                                break;
                            }
                            if (resId != 0) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        dialog.setMessage(getText(resId));
                                    }
                                });
                            }
                        }
                    }
                    @Override
                    public void onCancel() {
                        // do nothing
                    }
                    @Override
                    public void onError(TransferErrors err) {
                        mErrorMessage = err.toString();
                    }
                    @Override
                    public void onPostProcess(boolean success) {
                        if (!success && mErrorMessage == null) {
                            mErrorMessage = "Operation(s) was failed";
                        }
                    }
                };
                try {
                    return mPhysicaloid.processTasks(operations, Boards.ARDUINO_LEONARD, callback);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    return false;
                }
            }

            public void cancel() {
                // do nothing
            }

            @Override
            public void post(Result result) {
                switch (result) {
                case SUCCEEDED:
                    for (OperationInfo info : mOperationInfos) {
                        info.mFilePath = null;
                    }
                    Utils.showToast(MainActivity.this, R.string.messageExecutingCompleted);
                    break;
                default:
                case FAILED:
                    if (mErrorMessage == null) {
                        Utils.showToast(MainActivity.this, R.string.messageExecutingFailedUnknwon);
                    } else {
                        Utils.showToast(MainActivity.this,
                                getString(R.string.messageExecutingFailed).concat(mErrorMessage));
                    }
                case CANCELLED:
                    break;
                }
                mIsExecuting = false;
                controlUiAvalability();
            }
        };

        MyAsyncTaskWithDialog.execute(this, R.string.messageExecutingPrepare, task);
    }

}
