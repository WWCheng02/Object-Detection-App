package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.LinkedList;
import java.util.List;

//A simple View providing a render callback to other classes
public class OverlayView extends View {
    private final List<DrawCallback> callbacks = new LinkedList<DrawCallback>();
    private Paint paint;
    private float resultsViewHeight;
    private List<ImageClassifier.Recognition> results;
    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
    }

    private int INPUT_SIZE=300;

    //interface to define callback of client class
    public interface DrawCallback {
        public void drawCallback(Canvas canvas);
    }

    public void addCallback(DrawCallback callback) {
        callbacks.add(callback);
    }

    @Override
    public synchronized void draw(Canvas canvas) {
        super.draw(canvas);
        for (final DrawCallback callback : callbacks) {
            callback.drawCallback(canvas);
        }
        // when got results
        if (results !=null){
            for (int i=0; i<results.size(); i++){
                if (results.get(i).getConfidence() > 0.5) {
                    RectF box = reCalcSize(results.get(i).getLocation());
                    String title = results.get(i).getTitle() + String.format(" %2.2f", results.get(i).getConfidence()*100) + "%";
                    paint.setColor(Color.BLUE); //color of text
                    paint.setStyle(Paint.Style.STROKE);
                    canvas.drawRect(box, paint);
                    paint.setStrokeWidth(3.0f); // thickness
                    paint.setStyle(Paint.Style.FILL_AND_STROKE);
                    canvas.drawText(title, box.left, box.bottom, paint); //location of text
                    paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics())); // text size
                }
            }
        }
    }

    public void setResults(final List<ImageClassifier.Recognition> results) {
        this.results = results;
        postInvalidate();
    }

    private RectF reCalcSize(RectF rect) {
        int padding = 5;
        float overlayViewHeight = getHeight() - resultsViewHeight;
        float sizeMultiplier = Math.min((float) getWidth() / (float) INPUT_SIZE,
                overlayViewHeight / (float) INPUT_SIZE);

        float offsetX = (getWidth() - INPUT_SIZE * sizeMultiplier) / 2;
        float offsetY = (overlayViewHeight - INPUT_SIZE * sizeMultiplier) / 2 + resultsViewHeight;

        float left = Math.max(padding, sizeMultiplier * rect.left + offsetX);
        float top = Math.max(offsetY + padding, sizeMultiplier * rect.top + offsetY);

        float right = Math.min(rect.right * sizeMultiplier, getWidth() - padding);
        float bottom = Math.min(rect.bottom * sizeMultiplier + offsetY, getHeight() - padding);

        RectF newRect = new RectF(left, top, right, bottom);
        return newRect;
    }

}
