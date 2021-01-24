package com.example.myapplication;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;

public class MainActivity extends CameraActivity implements OnImageAvailableListener {
    private static int MODEL_IMAGE_INPUT_SIZE = 300;
    private static String LOGGING_TAG = MainActivity.class.getName();
    private static float TEXT_SIZE_DIP = 10;
    private static String modelFilename="detect.tflite";
    private static String labelFilename= "file:///android_asset/labelmap.txt";
    private static int inputSize=300;
    private boolean TF_OD_API_IS_QUANTIZED=false;
    private Integer sensorOrientation;
    private int previewWidth = 0;
    private int previewHeight = 0;
    private TFObjectDetectionAPIModel model;
    private Bitmap modelImgBitmap = null;
    private Bitmap cameraImgRgbBitmap = null;
    private boolean computing = false;
    private Matrix imgTransformMatrix;

    private OverlayView overlayView;

    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected Size getWantedPreviewSize() {
        return null;
    }

    @SuppressLint("WrongViewCast")
    @Override
    public void onPreviewSizeSelected(final Size previewSize, final int rotation) {
        final float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());

        try {
            //create a TF Object Detection model
            model = (TFObjectDetectionAPIModel) TFObjectDetectionAPIModel.create(getAssets(),modelFilename, labelFilename, inputSize, TF_OD_API_IS_QUANTIZED);
            Log.i(LOGGING_TAG, "Model initiated successfully"); //display message when model initiated successfully
            Toast.makeText(getApplicationContext(), "Detector opened", Toast.LENGTH_SHORT).show(); //display toast when detector opened successfully
        } catch(IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "Detector cannot be opened", Toast.LENGTH_SHORT).show(); //display toast when detector cannot be opened correctly
            finish();
        }
        overlayView = (OverlayView) findViewById(R.id.overlay);

        //get rotation
        final int screenOrientation = getWindowManager().getDefaultDisplay().getRotation();
        //Sensor orientation 90, Screen orientation 0
        sensorOrientation = rotation + screenOrientation;
        Log.i(LOGGING_TAG, String.format("Camera rotation: %d, Screen orientation: %d, Sensor orientation: %d", rotation, screenOrientation, sensorOrientation));

        //get preview size width and height
        previewWidth = previewSize.getWidth();
        previewHeight = previewSize.getHeight();
        Log.i(LOGGING_TAG, "preview width: " + previewWidth);
        Log.i(LOGGING_TAG, "preview height: " + previewHeight);

        // create empty bitmap
        modelImgBitmap = Bitmap.createBitmap(MODEL_IMAGE_INPUT_SIZE, MODEL_IMAGE_INPUT_SIZE, Config.ARGB_8888);
        cameraImgRgbBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);

        //create image transform matrix
        imgTransformMatrix = ImageUtilities.getTransformationMatrix(previewWidth, previewHeight, MODEL_IMAGE_INPUT_SIZE, MODEL_IMAGE_INPUT_SIZE, sensorOrientation,true);
        imgTransformMatrix.invert(new Matrix());
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image cameraImg = null;

        try {
            cameraImg = reader.acquireLatestImage();
            if (cameraImg == null) {
                return;
            }
            if (computing) {
                cameraImg.close();
                return;
            }
            computing = true;
            processImgForModelUse(cameraImg);
            cameraImg.close();
        } catch (final Exception ex) {
            if (cameraImg != null) {
                cameraImg.close();
            }
            Log.e(LOGGING_TAG, ex.getMessage());
        }

        runInBackground(new Runnable() {
            @Override
            public void run() {
                List<ImageClassifier.Recognition> results=model.detectObjects(modelImgBitmap);
                overlayView.setdetectionResults(results);
                requestRender();
                computing=false;
            }
        });
    }

    //process image for model use
    private void processImgForModelUse(final Image cameraImg) {
        cameraImgRgbBitmap.setPixels(ImageUtilities.convertYUVToARGB(cameraImg, previewWidth, previewHeight), 0, previewWidth, 0, 0, previewWidth, previewHeight);
        new Canvas(modelImgBitmap).drawBitmap(cameraImgRgbBitmap, imgTransformMatrix, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (model != null) {
            model.close();
        }
    }
}

