/*
 * Copyright (C) 2012 OBN-soft
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
 * 
 * 
 * This source code was modified by OBONO in November 2017.
 */

package com.obnsoft.arduboyutils;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;

import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class FilePickerActivity extends ListActivity {

    public static final String INTENT_EXTRA_DIRECTORY = "directory";
    public static final String INTENT_EXTRA_TOPDIRECTORY = "topDirectory";
    public static final String INTENT_EXTRA_EXTENSIONS = "extensions";
    public static final String INTENT_EXTRA_WRITEMODE = "writeMode";
    public static final String INTENT_EXTRA_SELECTPATH = "selectPath";

    private String mDirTop;
    private String mDirCurrent;
    private String[] mExtensions;
    private boolean mWriteMode = false;
    private int mPosNewEntry;
    private ArrayList<String> mStackPath = new ArrayList<String>();
    private FilePickerAdapter mAdapter;

    private int mResIdDir = R.drawable.ic_folder;
    private int mResIdFile = R.drawable.ic_file;
    private int mResIdNew = R.drawable.ic_newfile;
    private int mResIdNewMsg = R.string.messageNewFile;

    /*-----------------------------------------------------------------------*/

    class FilePickerAdapter extends ArrayAdapter<File> {

        private Context mContext;

        class FilePickerViewHolder {
            public ImageView imageView;
            public TextView textView;
        }

        public FilePickerAdapter(Context context) {
            super(context, 0);
            mContext = context;
        }

        public void setTargetDirectory(String path) {
            clear();
            File dir = new File(path);
            if (dir != null) {
                File[] files = dir.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        if (file.isHidden()) {
                            return false;
                        } else if (file.isDirectory()) {
                            return true;
                        }
                        return isExtensionMatched(file);
                    }
                });
                if (files != null) {
                    for (File file : files) {
                        add(file);
                    }
                    sort(new Comparator<File>() {
                        @Override
                        public int compare(File a, File b) {
                            if (a.isDirectory() != b.isDirectory()) {
                                return (a.isDirectory()) ? -1 : 1;
                            }
                            return a.getName().compareToIgnoreCase(b.getName());
                        }
                    });
                }
            }
            if (mWriteMode) {
                mPosNewEntry = getCount();
                add(null);
            }
            notifyDataSetChanged();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            FilePickerViewHolder holder;
            if (convertView == null) {
                LinearLayout ll = new LinearLayout(mContext);
                ll.setGravity(Gravity.CENTER_VERTICAL);
                holder = new FilePickerViewHolder();
                holder.imageView = new ImageView(mContext);
                holder.textView = new TextView(mContext);
                ll.addView(holder.imageView);
                ll.addView(holder.textView);
                ll.setTag(holder);
                convertView = ll;
            } else {
                holder = (FilePickerViewHolder) convertView.getTag();
            }
            File file = (File) getItem(position);
            holder.textView.setSingleLine(true);
            holder.textView.setTextAppearance(mContext, android.R.style.TextAppearance_Large);
            if (mWriteMode && position == mPosNewEntry) {
                holder.textView.setText(
                        (mResIdNewMsg == 0) ? "(New File)" : getText(mResIdNewMsg));
                holder.imageView.setImageResource(mResIdNew);
            } else {
                holder.textView.setText(file.getName());
                holder.imageView.setImageResource(file.isDirectory() ? mResIdDir : mResIdFile);
            }
            return convertView;
        }
    }

    /*-----------------------------------------------------------------------*/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.file_picker_activity);

        Intent intent = getIntent();
        String path = null;
        if (intent != null) {
            path = intent.getStringExtra(INTENT_EXTRA_DIRECTORY);
            mDirTop = intent.getStringExtra(INTENT_EXTRA_TOPDIRECTORY);
            mExtensions = intent.getStringArrayExtra(INTENT_EXTRA_EXTENSIONS);
            mWriteMode = intent.getBooleanExtra(INTENT_EXTRA_WRITEMODE, false);
        }

        /*  Check top directory.  */
        if (mDirTop == null) {
            mDirTop = Environment.getExternalStorageDirectory().getPath();
        }
        if (!mDirTop.endsWith(File.separator)) {
            mDirTop += File.separator;
        }

        /*  Check current directory.  */
        if (path == null) {
            path = mDirTop;
        } else {
            if (!path.endsWith(File.separator)) {
                path += File.separator;
            }
            if (!path.startsWith(mDirTop)) {
                path = mDirTop;
            }
        }

        /*  Check extension.  */
        if (mExtensions != null) {
            for (int i = 0; i < mExtensions.length; i++) {
                mExtensions[i] = tuneExtension(mExtensions[i]);
            }
        }

        mAdapter = new FilePickerAdapter(this);
        setListAdapter(mAdapter);
        setCurrentDirectory(path);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        File file = (File) mAdapter.getItem(position);
        if (mWriteMode && position == mPosNewEntry) {
            onNewFileRequested(mDirCurrent, (mExtensions != null) ? mExtensions[0] : null);
        } else if (file.isDirectory()) {
            mStackPath.add(mDirCurrent);
            setCurrentDirectory(file.getPath() + File.separator);
        } else {
            onFileSelected(file.getPath());
        }
    }

    @Override
    public void onBackPressed() {
        String path = getLastDirectory();
        if (path == null) {
            setResult(RESULT_CANCELED);
            super.onBackPressed();
        } else {
            mStackPath.remove(mStackPath.size() - 1);
            setCurrentDirectory(path);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.file_picker, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menuFilePickerBack:
            onBackPressed();
            return true;
        case R.id.menuFilePickerGoUpper:
            goToUpperDirectory();
            return true;
        }
        return false;
    }

    /*-----------------------------------------------------------------------*/

    public void setResourceId(int dirId, int fileId, int newId, int newMsgId) {
        if (dirId != 0)     mResIdDir = dirId;
        if (fileId != 0)    mResIdFile = fileId;
        if (newId != 0)     mResIdNew = newId;
        if (newMsgId != 0)  mResIdNewMsg = newMsgId;
    }

    public void setCurrentDirectory(String path) {
        mDirCurrent = path;
        mAdapter.setTargetDirectory(path);
        getListView().smoothScrollBy(0, 0); // Stop momentum scrolling
        onCurrentDirectoryChanged(path);
    }

    public void onCurrentDirectoryChanged(String path) {
        TextView tv = (TextView) findViewById(R.id.textViewCurrentDirectory);
        tv.setText(getTrimmedCurrentDirectory(path));
    }

    public void onFileSelected(String path) {
        setResultAndFinish(path);
    }

    public void onNewFileRequested(final String directory, final String extension) {
        final EditText editText = new EditText(this);
        editText.setSingleLine();
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                String fileName = editText.getText().toString().trim();
                if (fileName.length() == 0 || fileName.startsWith(".") ||
                        fileName.contains(File.separator) || fileName.contains(File.pathSeparator)) {
                    Utils.showToast(FilePickerActivity.this, R.string.messageInvalid);
                    return;
                }
                String newPath = directory.concat(fileName);
                if (extension != null && !newPath.endsWith(extension)) {
                    newPath = newPath.concat(extension);
                }
                if ((new File(newPath)).exists()) {
                    onFileSelected(newPath);
                } else {
                    setResultAndFinish(newPath);
                }
            }
        };
        Utils.showCustomDialog(this, android.R.drawable.ic_input_add,
                R.string.messageNewFile, editText, listener);
    }

    public void goToUpperDirectory() {
        String path = getUpperDirectory();
        if (path != null) {
            mStackPath.add(mDirCurrent);
            setCurrentDirectory(path);
        }
    }

    public String getTopDirectory() {
        return mDirTop;
    }

    public String getCurrentDirectory() {
        return mDirCurrent;
    }

    public String[] getExtensions() {
        return mExtensions;
    }

    public boolean isWriteMode() {
        return mWriteMode;
    }

    public String getTrimmedCurrentDirectory(String path) {
        if (path != null && path.startsWith(mDirTop)) {
            return path.substring(mDirTop.length());
        }
        return null;
    }

    public String getLastDirectory() {
        int size = mStackPath.size();
        return (size > 0) ? mStackPath.get(size - 1) : null;
    }

    public String getUpperDirectory() {
        if (mDirCurrent.equals(mDirTop)) {
            return null;
        }
        int start = mDirCurrent.length() - 1;
        if (mDirCurrent.endsWith(File.separator)) {
            start--;
        }
        int index = mDirCurrent.lastIndexOf(File.separatorChar, start);
        return (index >= 0) ? mDirCurrent.substring(0, index + 1) : null;
    }

    public void setResultAndFinish(String path) {
        Intent intent = new Intent();
        intent.putExtra(INTENT_EXTRA_SELECTPATH, path);
        setResult(RESULT_OK, intent);
        finish();
    }

    /*-----------------------------------------------------------------------*/

    private String tuneExtension(String extension) {
        if (extension != null) {
            extension = extension.toLowerCase(Locale.getDefault());
            if (!extension.startsWith(".")) {
                extension = ".".concat(extension);
            }
        }
        return extension;
    }

    private boolean isExtensionMatched(File file) {
        if (mExtensions == null) {
            return true;
        }
        String name = file.getName().toLowerCase(Locale.getDefault());
        for (String extension : mExtensions) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

}
