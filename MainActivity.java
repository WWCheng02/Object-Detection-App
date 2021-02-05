package com.example.myapplication;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;



public class MainActivity extends CameraActivity implements OnImageAvailableListener {
    private static int MODEL_IMAGE_INPUT_SIZE = 300;
    private static String LOGGING_TAG = MainActivity.class.getName();
    private static float TEXT_SIZE_DIP = 10;
    private static String modelFilename="detect.tflite";
    private static String labelFilename= "file:///android_asset/labelmap.txt";
    private float MIN_CONFIDENCE_SCORE=0.6f;
    private static int inputSize=300;
    private boolean TF_OD_API_IS_QUANTIZED=false;
    private Integer sensorOrientation;
    private static Size WANTED_PREVIEW_SIZE = new Size(640, 480);
    //private static int previewWidth = 0;
    //private static int previewHeight = 0;
    int previewWidth;
    int previewHeight;
    private TFObjectDetectionAPIModel model;
    private static Bitmap modelImgBitmap = null;
    private static Bitmap cameraImgRgbBitmap = null;
    private static Bitmap copyModelImgBitmap=null;
    private boolean computing = false;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private long timestamp=0;
    private byte[] luminanceCopy;

    private OverlayView overlayView;
    private ObjectsTracker tracker;
    private BoxText text;
    private Logger LOGGER = new Logger();
    //private ImageClassifier classifier;
    ImageClassifier classifier;
    @Override
    protected int getLayoutId() {
        return R.layout.activity_main;
    }

    @Override
    protected Size getWantedPreviewSize() {
        return null;
    }

    @Override
    public void onPreviewSizeSelected(final Size previewSize, final int rotation) {
        final float textSizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());

        text= new BoxText(textSizePx);
        text.setTypeface(Typeface.MONOSPACE);
        tracker=new ObjectsTracker(this);
        int cropSize = MODEL_IMAGE_INPUT_SIZE;
        try {
            //create a TF Object Detection model
            classifier = TFObjectDetectionAPIModel.create(getAssets(),modelFilename, labelFilename, MODEL_IMAGE_INPUT_SIZE, TF_OD_API_IS_QUANTIZED);
            cropSize= MODEL_IMAGE_INPUT_SIZE;
            Log.i(LOGGING_TAG, "Model initiated successfully"); //display message when model initiated successfully
            Toast.makeText(getApplicationContext(), "Detector opened", Toast.LENGTH_SHORT).show(); //display toast when detector opened successfully
        } catch(IOException e) {
            e.printStackTrace();
            Toast.makeText(getApplicationContext(), "Detector cannot be opened", Toast.LENGTH_SHORT).show(); //display toast when detector cannot be opened correctly
            finish();
        }

        //get preview size width and height
        previewWidth = previewSize.getWidth();
        previewHeight = previewSize.getHeight();
        Log.i(LOGGING_TAG, "preview width: " + previewWidth);
        Log.i(LOGGING_TAG, "preview height: " + previewHeight);

        overlayView = (OverlayView) findViewById(R.id.overlay);

        //get rotation
        final int screenOrientation = getWindowManager().getDefaultDisplay().getRotation();
        //Sensor orientation 90, Screen orientation 0
        sensorOrientation = rotation - screenOrientation;
        Log.i(LOGGING_TAG, String.format("Camera rotation: %d, Screen orientation: %d, Sensor orientation: %d", rotation, screenOrientation, sensorOrientation));

        // create empty bitmap
        modelImgBitmap = Bitmap.createBitmap(MODEL_IMAGE_INPUT_SIZE, MODEL_IMAGE_INPUT_SIZE, Config.ARGB_8888);
        cameraImgRgbBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);

        //create image transform matrix
        frameToCropTransform =
                ImageUtilities.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, false);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);
        overlayView = findViewById(R.id.overlay);
        overlayView.addCallback(
                new OverlayView.DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                    }
                });
    }

    @Override
    public void sceneProcessing() {
        ++timestamp;
        final long currTimestamp = timestamp;
        byte[] originalLuminance = getLuminance();
        tracker.onFrame(
                previewWidth,
                previewHeight,
                sensorOrientation);
        overlayView.postInvalidate();

        if (computing) {
            readyForNextImage();
            return;
        }
        computing = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        cameraImgRgbBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);


        if (luminanceCopy == null) {
            luminanceCopy = new byte[originalLuminance.length];
        }
        System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
        readyForNextImage();

        final Canvas canvas = new Canvas(modelImgBitmap);
        canvas.drawBitmap(cameraImgRgbBitmap, frameToCropTransform, null);

        /*Image cameraImg = null;

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
        tracker.onFrame(
                previewWidth,
                previewHeight,
                sensorOrientation);
        overlayView.postInvalidate(); */

        runInBackground(new Runnable() {
            @Override
            public void run() {
                List<ImageClassifier.Recognition> results=classifier.detectObjects(modelImgBitmap); //receive the results from model
                copyModelImgBitmap = Bitmap.createBitmap(modelImgBitmap);
                Canvas canvas=new Canvas(copyModelImgBitmap);
                List<ImageClassifier.Recognition> filteredRecognitions = new LinkedList<>(); //create a list to store results that meet required confidence score


                Paint paint = new Paint();
                paint.setColor(Color.BLUE); //color of text
                paint.setStyle(Paint.Style.STROKE); //set style


                 //draw rectangle for boundary box
                paint.setStrokeWidth(3.0f); // thickness
                //paint.setStyle(Paint.Style.FILL_AND_STROKE);
                /*canvas.drawText(title, box.left, box.bottom, paint); //location of text
                paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, getResources().getDisplayMetrics())); // text size*/

                    for (int i=0; i<results.size(); i++) {
                        final RectF location = results.get(i).getLocation();
                        if (location!=null && results.get(i).getConfidence() >= MIN_CONFIDENCE_SCORE) {
                            //LOGGER.i("Title: " + results.get(i).getTitle()); //text to display
                            canvas.drawRect(location, paint);
                            //overlayView.setdetectionResult(results.get(i));//pass the results for drawing boundary box
                            cropToFrameTransform.mapRect(location);
                            results.get(i).setLocation(location);
                            filteredRecognitions.add(results.get(i));

                        }

                }
                tracker.trackResults(filteredRecognitions);
                overlayView.postInvalidate();
                requestRender();
                computing=false;
            }
        });
    }

}

