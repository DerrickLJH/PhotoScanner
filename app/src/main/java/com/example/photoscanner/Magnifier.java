package com.example.photoscanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;

import androidx.appcompat.widget.AppCompatImageView;

public class Magnifier extends AppCompatImageView {

    private PointF zoomPosition;
    private boolean zooming = false;
    private Matrix matrix;
    private Paint paint;
    private Bitmap bitmap;
    private BitmapShader shader;
    private int sizeOfMagnifier = 200;

    public Magnifier(Context context) {
        super(context);
        init();
    }

    public Magnifier(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public Magnifier(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        zoomPosition = new PointF(0, 0);
        matrix = new Matrix();
        paint = new Paint();

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getAction();

        zoomPosition.x = event.getX();
        zoomPosition.y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                zooming = true;
                this.invalidate();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                zooming = false;
                this.invalidate();
                break;

            default:
                break;
        }

        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!zooming) {
            buildDrawingCache();
        } else {
            bitmap = getDrawingCache();
            shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

            paint = new Paint();
            paint.setShader(shader);
            matrix.reset();
            matrix.postScale(2f, 2f, zoomPosition.x, zoomPosition.y);
            paint.getShader().setLocalMatrix(matrix);
            canvas.drawCircle(zoomPosition.x, zoomPosition.y, sizeOfMagnifier, paint);
        }
    }

}

