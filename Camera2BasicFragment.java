package com.example.myapplication;


import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2BasicFragment extends Fragment {

    private static  int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
    private static  int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;
    private static  String FRAGMENT_DIALOG = "dialog";
    private String cameraId; // camera device id
    private AutoFitTextureView textureView;//// autofit texture view
    private CameraCaptureSession captureSession; //camera capture session
    private CameraDevice cameraDevice; //camera device
    private Size previewSize; //size for camera preview
    private Integer sensorOrientation; //rotation degree of camera sensor in display
    private HandlerThread backgroundThread;//thread run the task
    private Handler backgroundHandler; //to run task in background
    private ImageReader previewReader; //image capture
    private CaptureRequest.Builder previewRequestBuilder; //camera preview
    private CaptureRequest previewRequest; //preview request
    private  Semaphore cameraOpenCloseLock = new Semaphore(1);//// prevent app from exiting when closing the camera
    private OnImageAvailableListener imageListener; //receive frame
    private ConnectionListener cameraConnectionListener;
    private static  int MIN_PREVIEW_SIZE = 500;

    //convert screen rotation to JPEG orientation
    private static  SparseIntArray ORIENTATIONS = new SparseIntArray(4);
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private static  String LOGGING_TAG = Camera2BasicFragment.class.getName();
    private static  Size WANTED_PREVIEW_SIZE = new Size(720, 480);

    @Override
    public View onCreateView( LayoutInflater inflater,  ViewGroup container,  Bundle savedInstanceState) {
        return inflater.inflate(R.layout.camera2_fragment, container, false);
    }

    @Override
    public void onViewCreated( View view,  Bundle savedInstanceState) {
        textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    }

    @Override
    public void onActivityCreated( Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();

        //when screen is off then on again, SurfaceTexture is already exist, so only open camera without calling it again
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable( SurfaceTexture texture,  int width,  int height) {
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged( SurfaceTexture texture,  int width,  int height) {
                    configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed( SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated( SurfaceTexture texture) {
                }
            });
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    public void addConnectionListener( ConnectionListener cameraConnectionListener) {
        this.cameraConnectionListener = cameraConnectionListener;
    }

    public void addImageAvailableListener( OnImageAvailableListener imageListener) {
        this.imageListener = imageListener;
    }

    public interface ConnectionListener {
        void onPreviewSizeSelected(Size size, int cameraRotation);
    }

    private static Size selectOptimalSize( Size[] choices) {
        int minSize = Math.max(Math.min(WANTED_PREVIEW_SIZE.getWidth(), WANTED_PREVIEW_SIZE.getHeight()), MIN_PREVIEW_SIZE);
        Log.i(LOGGING_TAG, "Min size: " + minSize);

         List<Size> biggerSize = new ArrayList(); //to store size that is bigger than wanted
         List<Size> smallerSize = new ArrayList<Size>(); //store size that is smaller than wanted
         for (Size option : choices) {
            if (option.equals(WANTED_PREVIEW_SIZE)) {
                return WANTED_PREVIEW_SIZE;
            }

            //if size larger than wanted
            if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
                biggerSize.add(option);
            } else {
                smallerSize.add(option);
            }
        }
        // Pick the smallest bigger than wanted size
        Size sizeChoose = (biggerSize.size() > 0) ? Collections.min(biggerSize, new CompareSizesByArea(  )) : choices[0];

        Log.i(LOGGING_TAG, "Desired size: " + WANTED_PREVIEW_SIZE + ", min size: " + minSize + "x" + minSize);
        Log.i(LOGGING_TAG, "Valid preview sizes: [" + TextUtils.join(", ", biggerSize) + "]");
        Log.i(LOGGING_TAG, "Rejected preview sizes: [" + TextUtils.join(", ", smallerSize) + "]");
        Log.i(LOGGING_TAG, "Chosen preview size: " + sizeChoose);

        //return chosen Size
        return sizeChoose;
    }

    private void showToast( String text) {
         Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
        }
    }

    private void setUpCameraOutputs() {
         CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            //get a list of cameras
            for ( String cameraId : manager.getCameraIdList()) {
                 CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // dun use front camera
                 Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                 StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) {
                    continue;
                }

                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                Log.i(LOGGING_TAG, "Sensor Orientation: " + sensorOrientation);


                previewSize = selectOptimalSize(map.getOutputSizes(SurfaceTexture.class));


                // fit the aspect ratio of TextureView to the size of preview picked
                 int orientation = getResources().getConfiguration().orientation;
                Log.i(LOGGING_TAG, "Resource Orientation: " + orientation);

                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setCameraAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                } else {
                    textureView.setCameraAspectRatio(previewSize.getHeight(), previewSize.getWidth());
                }

                this.cameraId = cameraId;
            }
        } catch ( CameraAccessException ex) {
            Log.e(LOGGING_TAG, "Exception: " + ex.getMessage());
        } catch ( NullPointerException ex) {
            ErrorDialog.newInstance("This device does not support Camera2 API")
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            throw new IllegalStateException(("This device does not support Camera2 API"));
        }

        cameraConnectionListener.onPreviewSizeSelected(previewSize, sensorOrientation);
    }

    //open camera
    private void openCamera( int width,  int height) {
        setUpCameraOutputs();
        configureTransform(width, height);
        Activity activity = getActivity();

        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            if (activity.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened( CameraDevice cameraDevice) {
                        // preview then camera opened
                        cameraOpenCloseLock.release();
                        Camera2BasicFragment.this.cameraDevice = cameraDevice;
                        createCameraPreviewSession();
                    }

                    @Override
                    public void onDisconnected( CameraDevice cameraDevice) {
                        cameraOpenCloseLock.release();
                        cameraDevice.close();
                        Camera2BasicFragment.this.cameraDevice = null;
                    }

                    @Override
                    public void onError( CameraDevice cameraDevice,  int error) {
                        cameraOpenCloseLock.release();
                        cameraDevice.close();
                        Camera2BasicFragment.this.cameraDevice = null;
                         Activity activity = getActivity();
                        if (null != activity) {
                            activity.finish();
                        }
                    }
                }, backgroundHandler);
            } else {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
            }
        } catch ( CameraAccessException ex) {
            Log.e(LOGGING_TAG, "Exception: " + ex.getMessage());
        } catch ( InterruptedException ex) {
            throw new RuntimeException("Interrupted when trying to lock camera opening", ex);
        }
    }

    //Close camera
    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (null != captureSession) {
                captureSession.close();
                captureSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != previewReader) {
                previewReader.close();
                previewReader = null;
            }
        } catch ( InterruptedException e) {
            throw new RuntimeException("Interrupted when trying to lock camera closing.", e);
        }  {
            cameraOpenCloseLock.release();
        }
    }

    //start background thread
    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("ImageListener");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    //stop background thread
    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch ( InterruptedException ex) {
            Log.e(LOGGING_TAG, "Exception: " + ex.getMessage());
        }
    }

    //create new camera preview session
    private void createCameraPreviewSession() {
        try {
             SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;

            // configure default buffer size to wanted camera preview size
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            // output Surface need to start preview
             Surface surface = new Surface(texture);

            // set up CaptureRequest.Builder with the output Surface
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(surface);

            Log.i(LOGGING_TAG, String.format("Opening camera preview: " + previewSize.getWidth() + "x" + previewSize.getHeight()));

            // Create reader for the preview frames
            previewReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);

            previewReader.setOnImageAvailableListener(imageListener, backgroundHandler);
            previewRequestBuilder.addTarget(previewReader.getSurface());

            // create CameraCaptureSession for camera preview
            cameraDevice.createCaptureSession(Arrays.asList(surface, previewReader.getSurface()), getCaptureSessionStateCallback(), null);
        } catch ( CameraAccessException ex) {
            Log.e(LOGGING_TAG, "Exception: " + ex.getMessage());
        }
    }

    private CameraCaptureSession.StateCallback getCaptureSessionStateCallback() {
        return new CameraCaptureSession.StateCallback() {

            @Override
            public void onConfigured( CameraCaptureSession cameraCaptureSession) {
                // camera is closed
                if (null == cameraDevice) {
                    return;
                }

                // session is ready, displaying the preview
                captureSession = cameraCaptureSession;
                try {
                    // Auto focus should be continuous for camera preview
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    // Flash is automatically enabled when necessary
                    previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                    // start displaying the camera preview
                    previewRequest = previewRequestBuilder.build();
                    captureSession.setRepeatingRequest(previewRequest, null, backgroundHandler);
                } catch ( CameraAccessException ex) {
                    Log.e(LOGGING_TAG, "Exception: " + ex.getMessage());
                }
            }

            @Override
            public void onConfigureFailed( CameraCaptureSession cameraCaptureSession) {
                showToast("Failed");
            }
        };
    }

    //configure transformation to TextureView
    private void configureTransform( int viewWidth,  int viewHeight) {
         Activity activity = getActivity();
        if (null == textureView || null == previewSize || null == activity) {
            return;
        }
         int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
         Matrix matrix = new Matrix();
         RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
         RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
         float centerX = viewRect.centerX();
         float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
             float scale =
                    Math.max((float) viewHeight / previewSize.getHeight(), (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private void fixDeviceCameraOrientation(CaptureRequest.Builder previewRequestBuilder) {
        int deviceRotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
        int jpegOrientation = (ORIENTATIONS.get(deviceRotation) + sensorOrientation + 270) % 360;
        previewRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);

    }

    //compare area of sizes
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare( Size lhs,  Size rhs) {
            // cast to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}
