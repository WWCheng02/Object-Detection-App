package com.example.myapplication;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class TFObjectDetectionAPIModel implements ImageClassifier {
    private int inputSize;
    private static final String LOGGING_TAG = TFObjectDetectionAPIModel.class.getName();
    private static final float IMAGE_MEAN = 128f;
    private static final float IMAGE_STD = 128f;
    private static final int NUM_DETECTIONS = 10; //maximum return 10 results

    private boolean isModelQuantized;

    private ByteBuffer imageData;
    private Interpreter tfLite;
    private int[] intValues;
    private float[][][] outputLocation; //array to store location of boundary boxes
    private float[][] outputClass; //array to store classes of boundary box
    private float[][] outputConfidenceScore; //array to store scores of boundary box
    private float[] detectionsNo;//no of boundary box
    private Vector<String> labels = new Vector<String>(); //pre allocated buffers

    private TFObjectDetectionAPIModel() {

    }

    public static ImageClassifier create(AssetManager assetManager, String modelFilename, String labelFilename, int inputSize, boolean isQuantized)
            throws IOException {
        final TFObjectDetectionAPIModel model = new TFObjectDetectionAPIModel();

        //read model file
        String actualFilename = labelFilename.split("file:///android_asset/")[1];
        InputStream labelInput = assetManager.open(actualFilename);
        BufferedReader br = new BufferedReader(new InputStreamReader(labelInput));
        String line;
        while ((line = br.readLine()) != null) {
            Log.w(LOGGING_TAG,line);
            model.labels.add(line);
        }
        br.close();

        model.inputSize = inputSize;

        try {
            model.tfLite = new Interpreter(loadModelFile(assetManager, modelFilename));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        model.isModelQuantized = isQuantized;
        // Pre-allocate buffers.
        int byteNoPerChannel;
        if (isQuantized) {
            byteNoPerChannel = 1; // Quantized
        } else {
            byteNoPerChannel = 4; // Floating point
        }
        model.imageData = ByteBuffer.allocateDirect(1 * model.inputSize * model.inputSize * 3 * byteNoPerChannel);
        model.imageData.order(ByteOrder.nativeOrder());
        model.intValues = new int[model.inputSize * model.inputSize];

        model.outputLocation = new float[1][NUM_DETECTIONS][4]; //to store boundary box location
        model.outputClass = new float[1][NUM_DETECTIONS]; //to store output class name
        model.outputConfidenceScore = new float[1][NUM_DETECTIONS]; //to store confidence score
        model.detectionsNo = new float[1]; //to store detected objects
        return model;
    }

    //load model file
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }



    @Override
    public List<ImageClassifier.Recognition> detectObjects(final Bitmap bitmap) {
        //process image from int to float
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imageData.rewind();
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imageData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imageData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imageData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imageData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imageData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imageData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }

        Object[] inputArray = {imageData};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputLocation);
        outputMap.put(1, outputClass);
        outputMap.put(2, outputConfidenceScore);
        outputMap.put(3, detectionsNo);

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

        final ArrayList<Recognition> recognitions = new ArrayList<>(NUM_DETECTIONS); //to store a list of objects detected
        for (int i = 0; i < NUM_DETECTIONS; ++i) {
            final RectF detection =
                    new RectF(
                            outputLocation[0][i][1] * inputSize,
                            outputLocation[0][i][0] * inputSize,
                            outputLocation[0][i][3] * inputSize,
                            outputLocation[0][i][2] * inputSize);

            //in label file, class labels start from 1 to no of classes + 1
            //outputClass start from 0 to no of classes
            int labelOffset = 1;
            recognitions.add(
                    new Recognition(
                            "" + i, //id
                            labels.get((int) outputClass[0][i] + labelOffset), //class
                            outputConfidenceScore[0][i], //confidence score
                            detection)); //location of boundary box
        }
        return recognitions;
    }

    @Override
    public void enableStatLogging(final boolean logStats) {}

    @Override
    public String getStatString() {
        return "";
    }

    @Override
    public void close() {}

}
