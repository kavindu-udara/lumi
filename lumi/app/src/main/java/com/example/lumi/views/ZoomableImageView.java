package com.example.lumi.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.Matrix;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;

public class ZoomableImageView extends AppCompatImageView {

    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 4f;

    private final Matrix matrix = new Matrix();
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestureDetector;

    private float currentScale = 1f;
    private float lastX;
    private float lastY;
    private boolean dragging;

    public ZoomableImageView(Context context) {
        this(context, null);
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomableImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setScaleType(ScaleType.MATRIX);
        setImageMatrix(matrix);

        scaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                dragging = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragging && currentScale > MIN_SCALE) {
                    float dx = event.getX() - lastX;
                    float dy = event.getY() - lastY;
                    matrix.postTranslate(dx, dy);
                    setImageMatrix(matrix);
                    lastX = event.getX();
                    lastY = event.getY();
                }
                break;
            case MotionEvent.ACTION_UP:
                performClick();
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                break;
            default:
                break;
        }

        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    public void resetZoom() {
        currentScale = 1f;
        applyBaseMatrix();
        setImageMatrix(matrix);
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        post(this::resetZoom);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            resetZoom();
        }
    }

    private void applyBaseMatrix() {
        matrix.reset();

        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        float viewW = getWidth();
        float viewH = getHeight();
        float drawableW = drawable.getIntrinsicWidth();
        float drawableH = drawable.getIntrinsicHeight();
        if (drawableW <= 0f || drawableH <= 0f) {
            return;
        }

        float baseScale = Math.min(viewW / drawableW, viewH / drawableH);
        float dx = (viewW - (drawableW * baseScale)) / 2f;
        float dy = (viewH - (drawableH * baseScale)) / 2f;

        matrix.postScale(baseScale, baseScale);
        matrix.postTranslate(dx, dy);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(@NonNull ScaleGestureDetector detector) {
            float factor = detector.getScaleFactor();
            float nextScale = currentScale * factor;
            if (nextScale < MIN_SCALE) {
                factor = MIN_SCALE / currentScale;
                currentScale = MIN_SCALE;
            } else if (nextScale > MAX_SCALE) {
                factor = MAX_SCALE / currentScale;
                currentScale = MAX_SCALE;
            } else {
                currentScale = nextScale;
            }

            matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
            setImageMatrix(matrix);
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(@NonNull MotionEvent e) {
            if (currentScale > MIN_SCALE) {
                resetZoom();
            } else {
                float target = 2f;
                float factor = target / currentScale;
                currentScale = target;
                matrix.postScale(factor, factor, e.getX(), e.getY());
                setImageMatrix(matrix);
            }
            return true;
        }
    }
}

