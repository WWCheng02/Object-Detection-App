package com.example.myapplication;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

public class AutoFitTextureView extends TextureView {

    private int ratioWidth = 0;
    private int ratioHeight = 0;

    // context to associate this view
    public AutoFitTextureView(Context context) {
        this(context, null);
    }

    //attributes of the XML tag that is inflating the view
    public AutoFitTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);  //not look for defaults
    }

    //An attribute in the current theme that contains a reference to a style resource that supplies default values for the view
    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    //aspect ratio is the ratio of the camera, eg 4:3, 16:9, square
    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) { // must positive
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        ratioWidth = width;
        ratioHeight = height;
        requestLayout(); //call this when the view cannot fit the bound
    }

    // determine size of view
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec); //MeasureSpec class is used by views to tell their parents how they want to be measured and positioned
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == ratioWidth || 0 == ratioHeight) {
            setMeasuredDimension(width, height); // store measured width and height
        } else {
            if (width < height * ratioWidth / ratioHeight) {
                setMeasuredDimension(width, width * ratioHeight / ratioWidth);
            } else {
                setMeasuredDimension(height * ratioWidth / ratioHeight, height);
            }
        }
    }
}
