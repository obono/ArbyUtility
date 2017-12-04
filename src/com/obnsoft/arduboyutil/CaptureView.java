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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class CaptureView extends View {

    private static final int WIDTH = 128;
    private static final int HEIGHT = 64;
    private static final int BITS_PER_BYTE = 8;

    private Bitmap      mBitmap;
    private Bitmap      mBitmapBack;
    private Matrix      mMatrix;
    private Paint       mPaint;
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
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        int scale = Math.min(w / WIDTH, h / HEIGHT);
        mMatrix.setScale(scale, scale);
        mMatrix.postTranslate((w - WIDTH * scale) / 2, (h - HEIGHT * scale) / 2);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mBitmap.isRecycled()) {
            canvas.drawBitmap(mBitmap, mMatrix, mPaint);
        }
    }

    public void reset() {
        mDrawX = 0;
        mDrawY = 0;
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
    }

}
