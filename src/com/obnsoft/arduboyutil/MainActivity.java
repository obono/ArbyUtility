/*
 * Copyright (C) 2017 OBONO
 * http://d.hatena.ne.jp/OBONO/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.obnsoft.arduboyutil;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Locale;

import com.obnsoft.arduboyutil.MyAsyncTaskWithDialog.Result;
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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

public class MainActivity extends Activity {

    private static final String PREFS_KEY_LAST_FLASH_NAME = "last_flash_name";

    private static final String[] SUPPORT_EXTENSIONS_DOWNLOAD_FLASH =
            new String[] { AvrTask.EXT_HEX };
    private static final String[] SUPPORT_EXTENSIONS_UPLOAD_FLASH =
            new String[] { AvrTask.EXT_HEX, AvrTask.EXT_ARDUBOY };
    private static final String[] SUPPORT_EXTENSIONS_EEPROM =
            new String[] { AvrTask.EXT_EEPROM, AvrTask.EXT_HEX };

    private static final int OP_DOWNLOAD_FLASH_IDX  = 0;
    private static final int OP_DOWNLOAD_EEPROM_IDX = 1;
    private static final int OP_UPLOAD_FLASH_IDX    = 2;
    private static final int OP_UPLOAD_EEPROM_IDX   = 3;

    private static final int BAUD_RATE_SWITCH_AVR = 1200;
    private static final int WAIT_RESTART_TIMEOUT = 10 * 1000; // 10 seconds

    private MyApplication   mApp;
    private Physicaloid     mPhysicaloid;
    private Handler         mHandler;
    private Runnable        mRunnbaleWaitRestart;
    private boolean         mIsDownloadEepromSpecified = false;
    private boolean         mIsUploadEepromSpecified = false;
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
        private ImageView       mImageViewFileIcon;
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
            mImageViewFileIcon = (ImageView) parentView.findViewById(R.id.imageViewFileIcon);

            mOperation = operation;
            mSupportExtentions = supportExtentions;

            parentView.setTag(this);
            mToggleButton.setOnClickListener(new View.OnClickListener() {
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

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String baseName = prefs.getString(PREFS_KEY_LAST_FLASH_NAME, null);
        if (baseName != null) {
            setDefaultOperationFilePath(baseName);
        }

        mApp = (MyApplication) getApplication();
        mPhysicaloid = mApp.getPhysicaloidInstance();
        Intent intent = getIntent();
        if (intent != null) {
            handleIntent(intent);
        }
        if (!mOperationInfos[OP_UPLOAD_FLASH_IDX].mIsActive &&
                !mOperationInfos[OP_UPLOAD_EEPROM_IDX].mIsActive) {
            mOperationInfos[OP_UPLOAD_FLASH_IDX].mIsActive = true;
            controlUiAvalability();
        }
        registerReceiver(mUsbReceiver, MyApplication.USB_RECEIVER_FILTER);

        mHandler = new Handler();
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
        case R.id.menuMainFind:
            showUrlList();
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
        if (resultCode == RESULT_OK) {
            for (OperationInfo info : mOperationInfos) {
                if (requestCode == info.hashCode()) {
                    setOperationFilePath(info,
                            data.getStringExtra(FilePickerActivity.INTENT_EXTRA_SELECTPATH));
                }
            }
            controlUiAvalability();
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mUsbReceiver);
        mPhysicaloid.clearReadListener();
        mPhysicaloid.close();
        Utils.cleanCacheFiles(this);
        mApp.releaseWakeLock();
        super.onDestroy();
    }

    /*-----------------------------------------------------------------------*/

    public void onClickPickFile(View v) {
        OperationInfo info = (OperationInfo) ((View) v.getParent()).getTag();
        AvrTask.Op operation = info.mOperation;
        boolean isOut = (operation == Op.DOWNLOAD_FLASH || operation == Op.DOWNLOAD_EEPROM);
        boolean isFlash = (operation == Op.DOWNLOAD_FLASH || operation == Op.UPLOAD_FLASH);
        Intent intent = new Intent(this, FilePickerActivity.class);
        intent.putExtra(FilePickerActivity.INTENT_EXTRA_EXTENSIONS, info.mSupportExtentions);
        intent.putExtra(FilePickerActivity.INTENT_EXTRA_WRITEMODE, isOut);
        intent.putExtra(FilePickerActivity.INTENT_EXTRA_DIRECTORY,
                (isFlash ? Utils.FLASH_DIRECTORY : Utils.EEPROM_DIRECTORY).getPath());
        startActivityForResult(intent, info.hashCode());
    }

    public void onClickExecute(View v) {
        if (mPhysicaloid.isOpened() || mPhysicaloid.open()) {
            mApp.acquireWakeLock();
            mIsExecuting = true;
            mPhysicaloid.setBaudrate(BAUD_RATE_SWITCH_AVR); // Switch Arduboy to AVR mode
            mPhysicaloid.close();
            mRunnbaleWaitRestart = new Runnable() {
                @Override
                public void run() {
                    mApp.releaseWakeLock();
                    mIsExecuting = false;
                    Utils.showToast(MainActivity.this, R.string.messageDeviceSwitchFailed);
                    controlUiAvalability();
                }
            };
            mHandler.postDelayed(mRunnbaleWaitRestart, WAIT_RESTART_TIMEOUT);
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
                mHandler.removeCallbacks(mRunnbaleWaitRestart);
                executeOperations();
            }
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            mPhysicaloid.clearReadListener();
            mPhysicaloid.close();
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            String filePath = Utils.getPathFromUri(this, intent.getData());
            if (filePath != null) {
                int index = OP_UPLOAD_FLASH_IDX;
                if (filePath.toLowerCase(Locale.getDefault()).endsWith(AvrTask.EXT_EEPROM)) {
                    index = OP_UPLOAD_EEPROM_IDX;
                }
                mOperationInfos[index].mIsActive = true;
                setOperationFilePath(mOperationInfos[index], filePath);
                controlUiAvalability();
            }
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

            String filePath = info.mFilePath;
            if (filePath != null) {
                String fileName = new File(filePath).getName();
                info.mTextViewFilename.setText(fileName);
                info.mTextViewFilename.setEnabled(enabled);
                info.mImageViewFileIcon.setVisibility(View.VISIBLE);
                info.mImageViewFileIcon.setImageResource(
                        FilePickerActivity.getIconIdFromFileName(fileName));
            } else {
                info.mTextViewFilename.setText(R.string.textViewFileNotSpecified);
                info.mTextViewFilename.setEnabled(false);
                info.mImageViewFileIcon.setVisibility(View.GONE);
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

    private void setDefaultOperationFilePath(String baseName) {
        setOperationFilePath(mOperationInfos[OP_DOWNLOAD_FLASH_IDX],
                new File(Utils.FLASH_DIRECTORY, baseName.concat(AvrTask.EXT_HEX)).getPath());
    }

    private void setOperationFilePath(OperationInfo info, String filePath) {
        info.mFilePath = filePath;
        Op opration = info.mOperation;
        if (opration == Op.DOWNLOAD_EEPROM) {
            mIsDownloadEepromSpecified = true;
            return;
        } else if (opration == Op.UPLOAD_EEPROM) {
            mIsUploadEepromSpecified = true;
            return;
        }
        String baseName = Utils.getBaseFileName(filePath);
        File suggestFile = new File(Utils.EEPROM_DIRECTORY, baseName.concat(AvrTask.EXT_EEPROM));
        String suggestFilePath = suggestFile.getPath();
        if (opration == Op.DOWNLOAD_FLASH && !mIsDownloadEepromSpecified) {
            mOperationInfos[OP_DOWNLOAD_EEPROM_IDX].mFilePath = suggestFilePath;
        } else if (opration == Op.UPLOAD_FLASH && !mIsUploadEepromSpecified
                && suggestFile.exists()) {
            mOperationInfos[OP_UPLOAD_EEPROM_IDX].mFilePath = suggestFilePath;
        }
    }

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
            mApp.releaseWakeLock();
            mIsExecuting = false;
            controlUiAvalability();
            return;
        }

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
                                mHandler.post(new Runnable() {
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
                            mErrorMessage = getString(R.string.messageExecutingFailedDefault);
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
                    String baseName = null;
                    for (OperationInfo info : mOperationInfos) {
                        if (info.mIsActive) {
                            switch (info.mOperation) {
                            case DOWNLOAD_FLASH:
                            default:
                                break;
                            case DOWNLOAD_EEPROM:
                                mIsDownloadEepromSpecified = false;
                                break;
                            case UPLOAD_FLASH:
                                baseName = Utils.getBaseFileName(info.mFilePath);
                                break;
                            case UPLOAD_EEPROM:
                                mIsUploadEepromSpecified = false;
                                break;
                            }
                            info.mIsActive = false;
                            info.mFilePath = null;
                        }
                    }
                    if (baseName != null) {
                        commitLastFlashName(baseName);
                        setDefaultOperationFilePath(baseName);
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
                mApp.releaseWakeLock();
                mIsExecuting = false;
                controlUiAvalability();
            }
        };

        MyAsyncTaskWithDialog.execute(this, false, R.string.messageExecutingPrepare, task);
    }

    private void showUrlList() {
        final String[] items = getResources().getStringArray(R.array.bookmarkArray);
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    Uri uri = Uri.parse(items[which]);
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        Utils.showListDialog(this, R.drawable.ic_menu_find, R.string.menuFind, items, listener);
    }

    private void commitLastFlashName(String baseName) {
        SharedPreferences.Editor editor =
                PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(PREFS_KEY_LAST_FLASH_NAME, baseName);
        editor.commit();
    }

}
