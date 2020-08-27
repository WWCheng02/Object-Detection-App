package com.example.myapplication;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Trace;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Vector;
import java.nio.ByteBuffer;
import java.util.*;
/**
 * Wrapper for frozen detection models trained using the Tensorflow Object Detection API:
 * github.com/tensorflow/models/tree/master/research/object_detection
 */
public class TFObjectDetectionAPIModel implements ImageClassifier {
    private static Logger LOGGER = new Logger();

    // max number of detections
    private static int MAX_DETECTIONS = 10;

    // declare values
    private String inputName;
    private int inputSize;

    //number of threads
    private static int NUM_THREADS = 4;

    private static float IMAGE_MEAN = 127.5f;
    private static float IMAGE_STD = 127.5f;

    // Pre-allocated buffers
    private Vector<String> labels = new Vector<String>();
    private int[] intValues;
    private byte[] byteValues;
    //array of output boundary box
    private float[] outputLocations;
    //array of scores
    private float[] outputScores;
    //array of classes of objects
    private float[] outputClasses;
    //array of number of detected boxes
    private float[] outputNumDetections;

    //array of objects names
    private String[] outputNames;

    private boolean logStats = false;

    private ByteBuffer imgData;

    private Interpreter tflite;

    private TFObjectDetectionAPIModel(){

    }

    //load model file
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename) throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public static ImageClassifier create(
            final AssetManager assetManager, // asset manager to be used to load assets
            final String modelFilename, // filepath of tensorflow model
            final String labelFilename, //filepath of class label
            final int inputSize)
            throws IOException {
        final TFObjectDetectionAPIModel model = new TFObjectDetectionAPIModel();

        //get label
        InputStream labelsInput = null;
        String actualFilename = labelFilename.split("file:///android_asset/")[1];
        labelsInput = assetManager.open(actualFilename);
        BufferedReader br = null;
        br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null) {
            LOGGER.w(line);
            model.labels.add(line);
        }
        br.close();

        model.inputSize = inputSize;

        try{
            model.tflite = new Interpreter(loadModelFile(assetManager, modelFilename));
        }
        catch (Exception e){
            throw new RuntimeException(e);
        }



        model.inputName = "image_tensor";

        model.inputSize = inputSize;

        // Pre-allocate buffers.
        model.outputNames = new String[] {"detection_boxes", "detection_scores", "detection_classes", "num_detections"};
        model.intValues = new int[model.inputSize * model.inputSize];
        model.byteValues = new byte[model.inputSize * model.inputSize * 3];
        model.outputScores = new float[MAX_DETECTIONS];
        model.outputLocations = new float[MAX_DETECTIONS * 4];
        model.outputClasses = new float[MAX_DETECTIONS];
        model.outputNumDetections = new float[1];
        return model;
    }

    @Override
    public List<Recognition> recognizeImage(Bitmap bitmap) {
        // Log this method so that it can be analyzed with systrace.
        Trace.beginSection("recognizeImage");

        Trace.beginSection("preprocessBitmap");

        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        for (int i = 0; i < intValues.length; ++i) {
            byteValues[i * 3 + 2] = (byte) (intValues[i] & 0xFF);
            byteValues[i * 3 + 1] = (byte) ((intValues[i] >> 8) & 0xFF);
            byteValues[i * 3 + 0] = (byte) ((intValues[i] >> 16) & 0xFF);
        }
        Trace.endSection(); // preprocessBitmap

        // Copy the input data into TensorFlow
        Trace.beginSection("feed");
        outputLocations= new float[1];
        outputClasses = new float[1];
        outputScores = new float[1];
        Trace.endSection();

        Object[] inputArray = {imgData};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputLocations);
        outputMap.put(1, outputClasses);
        outputMap.put(2, outputScores);
        outputMap.put(3, outputNumDetections);
        Trace.endSection();

        // Run the inference call.
        Trace.beginSection("run");
        tflite.runForMultipleInputsOutputs(inputArray, outputMap);
        Trace.endSection();

        // Copy the output Tensor back into the output array.
        Trace.beginSection("fetch");
        outputLocations = new float[MAX_DETECTIONS * 4];
        outputScores = new float[MAX_DETECTIONS];
        outputClasses = new float[MAX_DETECTIONS];
        outputNumDetections = new float[1];


        // Find the best detections
        final PriorityQueue<Recognition> pq =
                new PriorityQueue<Recognition>(
                        1,
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(final Recognition lhs, final Recognition rhs) {
                                // Intentionally reversed to put high confidence at the head of the queue.
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            }
                        });

        // Scale them back to the input size.
        for (int i = 0; i < outputScores.length; ++i) {
            final RectF detection =
                    new RectF(
                            outputLocations[4 * i + 1] * inputSize,
                            outputLocations[4 * i] * inputSize,
                            outputLocations[4 * i + 3] * inputSize,
                            outputLocations[4 * i + 2] * inputSize);
            pq.add(
                    new Recognition("" + i, labels.get((int) outputClasses[i]), outputScores[i], detection));
        }

        final ArrayList<Recognition> recognitions = new ArrayList<Recognition>();
        for (int i = 0; i < Math.min(pq.size(), MAX_DETECTIONS); ++i) {
            recognitions.add(pq.poll());
        }
        Trace.endSection(); // "recognizeImage"
        return recognitions;
    }

    @Override
    public void enableStatLogging(final boolean logStats) {
    }

    @Override
    public String getStatString() {
        return "";
    }

    @Override
    public void close() {
    }
}
