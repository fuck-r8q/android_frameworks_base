/*
 * Copyright (C) 2008-2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gesture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;


/**
 * A (transparent) view for gesture input that can be placed on top of other
 * widgets. The background of the view is customizable. 
 * 
 * @author liyang@google.com (Yang Li)
 *
 */

public class GesturePad extends View {

    private static final float TOUCH_TOLERANCE = 4;
    public static final int DEFAULT_GESTURE_COLOR = Color.argb(255, 255, 255, 0);
    
    // double buffering 
    private Paint mGesturePaint;
    private Bitmap mBitmap; // with transparent background
    private Canvas mBitmapCanvas;

    // for rendering immediate ink feedback
    private Path mPath;
    private float mX;
    private float mY;

    // current gesture
    private Gesture mCurrentGesture = null;
    
    // gesture event handlers
    ArrayList<GestureListener> mGestureListeners =
                                new ArrayList<GestureListener>();
    private ArrayList<GesturePoint> mPointBuffer = null;

    // fading out effect
    private boolean mIsFadingOut = false;
    private float mFadingAlpha = 1;
    private Handler mHandler = new Handler();
    private Runnable mFadingOut = new Runnable() {
        public void run() {
            mFadingAlpha -= 0.03f;
            if (mFadingAlpha <= 0) {
                mIsFadingOut = false;
                mPath = null;
                mCurrentGesture = null;
                mBitmap.eraseColor(Color.argb(0, 0, 0, 0));
            } else {
                mHandler.postDelayed(this, 100);
            }
            invalidate();
        }
    };

    public GesturePad(Context context) {
        super(context);
        init();
    }
  
    public GesturePad(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public ArrayList<GesturePoint> getCurrentStroke() {
        return this.mPointBuffer;
    }
    
    public Gesture getCurrentGesture() {
        return mCurrentGesture;
    }
    
    /**
     * Set Gesture color
     * @param c
     */
    public void setGestureColor(int c) {
        this.mGesturePaint.setColor(c);
        if (mCurrentGesture != null) {
            mBitmap.eraseColor(Color.argb(0, 0, 0, 0));
            mCurrentGesture.draw(mBitmapCanvas, mGesturePaint);
        }
    }
    
    /**
     * Set the gesture to be shown in the view
     * @param gesture
     */
    public void setCurrentGesture(Gesture gesture) {
        if (this.mCurrentGesture != null) {
            clear(false);
        }
        
        this.mCurrentGesture = gesture;
        
        if (this.mCurrentGesture != null) {
            if (mBitmapCanvas != null) {
                this.mCurrentGesture.draw(mBitmapCanvas, mGesturePaint);
                this.invalidate();
            }
        }
    }
    
    private void init() {
        mGesturePaint = new Paint();
        mGesturePaint.setAntiAlias(true);
        mGesturePaint.setDither(true);
        mGesturePaint.setColor(DEFAULT_GESTURE_COLOR);
        mGesturePaint.setStyle(Paint.Style.STROKE);
        mGesturePaint.setStrokeJoin(Paint.Join.ROUND);
        mGesturePaint.setStrokeCap(Paint.Cap.ROUND);
        mGesturePaint.setStrokeWidth(12);
        mGesturePaint.setMaskFilter(
            new BlurMaskFilter(1, BlurMaskFilter.Blur.NORMAL));
        
        mPath = null;
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // TODO Auto-generated method stub
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) {
            return;
        }
        int width = w > oldw? w : oldw;
        int height = h > oldh? h : oldh;
        mBitmap = Bitmap.createBitmap(
            width, height, Bitmap.Config.ARGB_8888);
        mBitmapCanvas = new Canvas(mBitmap);
        mBitmapCanvas.drawColor(Color.argb(0, 0, 0, 0));
        if (mCurrentGesture != null) {
            mCurrentGesture.draw(mBitmapCanvas, mGesturePaint);
        }
    }

    public void addGestureListener(GestureListener l) {
        this.mGestureListeners.add(l);
    }
  
    public void removeGestureListener(GestureListener l) {
        this.mGestureListeners.remove(l);
    }
  
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // draw double buffer
        Paint paint = new Paint(Paint.DITHER_FLAG);
        if (mIsFadingOut) {
            paint.setAlpha((int)(255 * mFadingAlpha));
            canvas.drawBitmap(mBitmap, 0, 0, paint);
        } else {
            canvas.drawBitmap(mBitmap, 0, 0, paint);
        }
        
        // draw the current stroke
        if (mPath != null) {
            canvas.drawPath(mPath, mGesturePaint);
        }
    }

    /**
     * Clear up the gesture pad
     * @param fadeOut whether the gesture on the pad should fade out gradually
     * or disappear immediately
     */
    public void clear(boolean fadeOut) {
        if (fadeOut) {
            mFadingAlpha = 1;
            mIsFadingOut = true;
            mHandler.removeCallbacks(mFadingOut);
            mHandler.postDelayed(mFadingOut, 100);
        } else {
            mPath = null;
            this.mCurrentGesture = null;
            if (mBitmap != null) {
                mBitmap.eraseColor(Color.argb(0, 0, 0, 0));
                this.invalidate();
            }
        }
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
      
        if(this.isEnabled() == false) { 
            return true;
        }
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touch_start(event);
                invalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                touch_move(event);
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                touch_up(event);
                invalidate();
                break;
        }
        
        return true;
    }
    
    private void touch_start(MotionEvent event) {
        // if there is fading-out effect, stop it.
        if (mIsFadingOut) {
            mIsFadingOut = false;
            mHandler.removeCallbacks(mFadingOut);
            mBitmap.eraseColor(Color.argb(0, 0, 0, 0));
            this.mCurrentGesture = null;
        }
      
        float x = event.getX();
        float y = event.getY();

        mX = x;
        mY = y;
        
        // pass the event to handlers
        int count = mGestureListeners.size();
        for (int i = 0; i < count; i++) {
            GestureListener listener = mGestureListeners.get(i);
            listener.onStartGesture(this, event);
        }

        if (mCurrentGesture == null) {
            mCurrentGesture = new Gesture();
        }
        
        mPointBuffer = new ArrayList<GesturePoint>();
        mPointBuffer.add(new GesturePoint(x, y, event.getEventTime()));

        mPath = new Path();
        mPath.moveTo(x, y);
    }
    
    private void touch_move(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            mPath.quadTo(mX, mY, (x + mX)/2, (y + mY)/2);
            mX = x;
            mY = y;
        }
        
        mPointBuffer.add(new GesturePoint(x, y, event.getEventTime()));

        // pass the event to handlers
        int count = mGestureListeners.size();
        for (int i = 0; i < count; i++) {
            GestureListener listener = mGestureListeners.get(i);
            listener.onGesture(this, event);
        }
    }

    
    private void touch_up(MotionEvent event) {
        // add the stroke to the current gesture
        mCurrentGesture.addStroke(new GestureStroke(mPointBuffer));
        mPointBuffer = null;

        // add the stroke to the double buffer
        mBitmapCanvas.drawPath(mPath, mGesturePaint);
        mPath = null;
        
        // pass the event to handlers
        int count = mGestureListeners.size();
        for (int i = 0; i < count; i++) {
            GestureListener listener = mGestureListeners.get(i);
            listener.onFinishGesture(this, event);
        }
    }
    
}
