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
import java.io.FileOutputStream;
import java.util.Calendar;

import com.obnsoft.arduboyutil.MyAsyncTaskWithDialog.Result;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.MediaScannerConnection;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;

public class CaptureView extends View implements OnClickListener {

    private static final int WIDTH = 128;
    private static final int HEIGHT = 64;
    private static final int BITS_PER_BYTE = 8;
    private static final String SHOT_FILE_NAME_FORMAT = "yyyyMMddhhmmss'.png'";

    private Bitmap      mBitmap;
    private Bitmap      mBitmapBack;
    private Matrix      mMatrix;
    private Paint       mPaint;
    private boolean     mIsAvailable;
    private int         mDrawX;
    private int         mDrawY;

    public CaptureView(Context context) {
        this(context, null);
    }

    public CaptureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CaptureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mBitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.RGB_565);
        mBitmapBack = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.RGB_565);
        mMatrix = new Matrix();
        mPaint = new Paint(0); // No ANTI_ALIAS_FLAG, No FILTER_BITMAP_FLAG
        setFocusable(false);
        this.setOnClickListener(this);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int scale = Math.min(w / WIDTH, h / HEIGHT);
        mMatrix.setScale(scale, scale);
        mMatrix.postTranslate((w - WIDTH * scale) / 2, (h - HEIGHT * scale) / 2);
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mBitmap.isRecycled()) {
            canvas.drawBitmap(mBitmap, mMatrix, mPaint);
        }
    }

    @Override
    public synchronized void onClick(View v) {
        if (!mIsAvailable || mBitmap.isRecycled()) {
            return;
        }
        final Context context = getContext();
        final Bitmap bitmapWork = mBitmap.copy(Bitmap.Config.RGB_565, false);
        mIsAvailable = false;
        String fileName = DateFormat.format(
                SHOT_FILE_NAME_FORMAT, Calendar.getInstance()).toString();
        final File file = new File(Utils.SHOT_DIRECTORY, fileName);
        MyAsyncTaskWithDialog.ITask task = new MyAsyncTaskWithDialog.ITask() {
            @Override
            public Boolean task(ProgressDialog dialog) {
                try {
                    FileOutputStream out = new FileOutputStream(file);
                    bitmapWork.compress(CompressFormat.PNG, 0, out);
                    out.close();
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            };
            @Override
            public void cancel() {
            }
            @Override
            public void post(Result result) {
                switch (result) {
                case SUCCEEDED:
                    MediaScannerConnection.scanFile(context,
                            new String[] { file.getAbsolutePath() }, null, null);
                    String message = String.format(
                            context.getString(R.string.messageSavedScreenShot), file.getName());
                    Utils.showToast(context, message);
                    break;
                case FAILED:
                case CANCELLED:
                default:
                    break;
                }
            }
        };
        MyAsyncTaskWithDialog.execute(context, true, R.string.messageSavingScreenShot, task);
    }

    public synchronized void reset() {
        mIsAvailable = false;
        mDrawX = 0;
        mDrawY = 0;
        if (!mBitmap.isRecycled()) {
            mBitmap.eraseColor(Color.BLACK);
            postInvalidate();
        }
    }

    public synchronized void appendData(byte[] data) {
        if (mBitmapBack.isRecycled()) {
            return;
        }
        int dataSize = data.length;
        for (int i = 0; i < dataSize; i++) {
            int val = data[i];
            for (int y = 0; y < BITS_PER_BYTE; y++) {
                mBitmapBack.setPixel(mDrawX, mDrawY + y, Color.WHITE * (val & 1));
                val >>= 1;
            }
            if (++mDrawX >= WIDTH) {
                mDrawX = 0;
                mDrawY += BITS_PER_BYTE;
                if (mDrawY >= HEIGHT) {
                    mIsAvailable = true;
                    mDrawY = 0;
                    Bitmap tmp = mBitmap;
                    mBitmap = mBitmapBack;
                    mBitmapBack = tmp;
                    postInvalidate();
                }
            }
        }
    }

    public synchronized void onDestroy() {
        mBitmap.recycle();
        mBitmapBack.recycle();
        mIsAvailable = false;
    }

}
