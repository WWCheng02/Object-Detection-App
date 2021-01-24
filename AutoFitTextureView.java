package com.example.myapplication;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

public class AutoFitTextureView extends TextureView {

    private int ratioWidth = 0;
    private int ratioHeight = 0;

    // context of the texture view
    public AutoFitTextureView(Context context) {
        this(context, null);
    }

    //The attributes of the XML tag of the extended view
    public AutoFitTextureView(Context context, AttributeSet attributes) {
        this(context, attributes, 0);  
    }

    //The attributes in theme, including references to style resources, provide default values​​for the view
    public AutoFitTextureView(Context context, AttributeSet attributes, int defaultStyle) {
        super(context, attributes, defaultStyle);
    }

    //aspect ratio is the ratio of the camera, eg 4:3, 16:9, square
    public void setCameraAspectRatio(int cameraWidth, int cameraHeight) {
        if (cameraWidth < 0 || cameraHeight < 0) { // the size of camera screen must positive
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        ratioWidth = cameraWidth;
        ratioHeight = cameraHeight;
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
