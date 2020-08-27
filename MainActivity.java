package com.example.myapplication;

import android.annotation.SuppressLint;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;

import com.example.myapplication.ImageClassifier;
import com.example.myapplication.TFObjectDetectionAPIModel;
import com.example.myapplication.OverlayView;
import com.example.myapplication.ImageUtilities;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class MainActivity extends CameraActivity implements OnImageAvailableListener {
    private static int MODEL_IMAGE_INPUT_SIZE = 300;
    private static String LOGGING_TAG = MainActivity.class.getName();
    private static float TEXT_SIZE_DIP = 10;
    private static String modelFilename="detect.tflite";
    private static String labelFilename= "file:///android_asset/labelmap.txt";
    private static int inputSize=300;

    private Integer sensorOrientation;
    private int previewWidth = 0;
    private int previewHeight = 0;
    private TFObjectDetectionAPIModel model;
    private Bitmap imageBitmapForModel = null;
    private Bitmap rgbBitmapForCameraImage = null;
    private boolean computing = false;
    private Matrix imageTransformMatrix;

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
    public void onPreviewSizeChosen(final Size previewSize, final int rotation) {
        final float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                TEXT_SIZE_DIP, getResources().getDisplayMetrics());

        try {
            model = (TFObjectDetectionAPIModel) TFObjectDetectionAPIModel.create(getAssets());
            Log.i(LOGGING_TAG, "Model Initiated successfully.");
            Toast.makeText(getApplicationContext(), "Detector opened", Toast.LENGTH_SHORT).show();
        } catch(IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "Detector cannot be opened", Toast.LENGTH_SHORT).show();
            finish();
        }
        overlayView = (OverlayView) findViewById(R.id.overlay);

        final int screenOrientation = getWindowManager().getDefaultDisplay().getRotation();
        //Sensor orientation: 90, Screen orientation: 0
        sensorOrientation = rotation + screenOrientation;
        Log.i(LOGGING_TAG, String.format("Camera rotation: %d, Screen orientation: %d, Sensor orientation: %d",
                rotation, screenOrientation, sensorOrientation));

        previewWidth = previewSize.getWidth();
        previewHeight = previewSize.getHeight();
        Log.i(LOGGING_TAG, "preview width: " + previewWidth);
        Log.i(LOGGING_TAG, "preview height: " + previewHeight);
        // create empty bitmap
        imageBitmapForModel = Bitmap.createBitmap(MODEL_IMAGE_INPUT_SIZE, MODEL_IMAGE_INPUT_SIZE, Config.ARGB_8888);
        rgbBitmapForCameraImage = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);

        imageTransformMatrix = ImageUtilities.getTransformationMatrix(previewWidth, previewHeight,
                MODEL_IMAGE_INPUT_SIZE, MODEL_IMAGE_INPUT_SIZE, sensorOrientation,true);
        imageTransformMatrix.invert(new Matrix());
    }

    @Override
    public void onImageAvailable(final ImageReader reader) {
        Image imageFromCamera = null;

        try {
            imageFromCamera = reader.acquireLatestImage();
            if (imageFromCamera == null) {
                return;
            }
            if (computing) {
                imageFromCamera.close();
                return;
            }
            computing = true;
            preprocessImageForModel(imageFromCamera);
            imageFromCamera.close();
        } catch (final Exception ex) {
            if (imageFromCamera != null) {
                imageFromCamera.close();
            }
            Log.e(LOGGING_TAG, ex.getMessage());
        }

        runInBackground(new Runnable() {
            @Override
            public void run() {
                List<ImageClassifier.Recognition> results=model.detectObjects(imageBitmapForModel);
                overlayView.setResults(results);
                requestRender();
                computing=false;
            }
        });
    }

    private void preprocessImageForModel(final Image imageFromCamera) {
        rgbBitmapForCameraImage.setPixels(ImageUtilities.convertYUVToARGB(imageFromCamera, previewWidth, previewHeight),
                0, previewWidth, 0, 0, previewWidth, previewHeight);

        new Canvas(imageBitmapForModel).drawBitmap(rgbBitmapForCameraImage, imageTransformMatrix, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (model != null) {
            model.close();
        }
    }
}

