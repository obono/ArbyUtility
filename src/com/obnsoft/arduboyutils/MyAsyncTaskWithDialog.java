/*
 * Copyright (C) 2015 OBN-soft
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

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;

public class MyAsyncTaskWithDialog extends AsyncTask<Void, Integer, Boolean> {

    public static enum Result {
        SUCCEEDED, FAILED, CANCELLED
    }

    public interface ITask {
        public Boolean  task(ProgressDialog dialog);
        public void     cancel();
        public void     post(Result result);
    }

    public static void execute(Context context, int resId, ITask task) {
        (new MyAsyncTaskWithDialog(context, resId, task)).execute();
    }

    /*-----------------------------------------------------------------------*/

    private Context         mContext;
    private int             mMsgResId;
    private ITask           mTask;
    private ProgressDialog  mDialog;

    private MyAsyncTaskWithDialog(Context context, int resId, ITask task) {
        mContext = context;
        mMsgResId = resId;
        mTask = task;
    }

    @Override
    protected void onPreExecute() {
        mDialog = new ProgressDialog(mContext);
        mDialog.setMessage(mContext.getText(mMsgResId));
        mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mDialog.setIndeterminate(true);
        mDialog.setCancelable(false);
        mDialog.setButton(AlertDialog.BUTTON_NEUTRAL, mContext.getText(android.R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mTask.cancel();
                        cancel(true);
                    }
        });
        mDialog.show();
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        return mTask.task(mDialog);
    }

    @Override
    protected void onCancelled() {
        mDialog.dismiss();
        mTask.post(Result.CANCELLED);
    }

    @Override
    protected void onPostExecute(Boolean result) {
        mDialog.dismiss();
        mTask.post(result ? Result.SUCCEEDED : Result.FAILED);
    }


}
