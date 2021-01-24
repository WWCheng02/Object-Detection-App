package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.WindowManager;
import android.widget.Toast;

public abstract class CameraActivity extends Activity implements OnImageAvailableListener {
    private static final String LOGGING_TAG = "Application";
    private static final int PERMISSIONS_REQUEST = 1;
    private Handler handler;
    private HandlerThread handlerThread;
    private  int previewHeight=0;
    private  int previewWidth=0;
    private static final Logger LOGGER = new Logger();
    protected abstract int getLayoutId();
    protected abstract Size getWantedPreviewSize();

    //create camera activity
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(null);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //to keep the screen on

        setContentView(R.layout.activity_main);

        //if has permission
        if (hasPermission()) {
            setFragment();
        } else {
            requestPermission(); //no permission then make request
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        if (!isFinishing()) {
            finish();
        }

        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException ex) {
            Log.e(LOGGING_TAG, "Exception: " + ex.getMessage());
        }

        super.onPause();
    }

    protected synchronized void runInBackground(final Runnable runnable) {
        if (handler != null) {
            handler.post(runnable);
        }
    }

    //request permission result
    @Override
    public void onRequestPermissionsResult(final int requestCode, final String[] permissions, final int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) { //if permission granted
                    setFragment();
                } else {
                    requestPermission(); //else request permission
                }
            }
        }
    }

    //if permission granted
    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //check permission for camera and external storage
            return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    // Returns true if the device supports the required hardware level
    private boolean isHardwareLevelSupported(CameraCharacteristics characteristics, int supportedLevel) {
        int deviceLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL); //get device supported hardware level
        //if device level equal to required minimum level
        if (deviceLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return supportedLevel == deviceLevel;
        }
        // deviceLevel is not legacy, can use numerical sort
        return supportedLevel <= deviceLevel;
    }

    private String chooseCamera() {
        final CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE); //get camera service
        try {
            //loop through each camera
            for (final String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // do not use a front facing camera
                final Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                final StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                if (map == null) {
                    continue;
                }

                //if camera2API can be fully supported
                boolean useCamera2API = isHardwareLevelSupported(characteristics, CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL);
                LOGGER.i("Camera API lv2?: %s", useCamera2API);
                return cameraId;
            }
        } catch (CameraAccessException e) {
            LOGGER.e("Not allowed to access camera");
        }
        return null;
    }

    protected void setFragment() {
        Camera2BasicFragment cameraConnectionFragment = new Camera2BasicFragment();
        cameraConnectionFragment.addConnectionListener((final Size size, final int rotation) -> CameraActivity.this.onPreviewSizeSelected(size, rotation));
        cameraConnectionFragment.addImageAvailableListener(this);

        getFragmentManager()
                .beginTransaction()
                .replace(R.id.container, cameraConnectionFragment)
                .commit();
    }

    //request permission for camera and external storage
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) || shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(CameraActivity.this, "Camera and storage permission are needed to run this application", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST);
        }
    }

    public void requestRender() {
        @SuppressLint("WrongViewCast") final OverlayView overlay = (OverlayView) findViewById(R.id.overlay);
        if (overlay != null) {
            overlay.postInvalidate();
        }
    }

    public void addCallback(final OverlayView.DrawCallback callback) {
        @SuppressLint("WrongViewCast") final OverlayView overlay = (OverlayView) findViewById(R.id.overlay);
        if (overlay != null) {
            overlay.addCallback(callback);
        }
    }

    protected abstract void onPreviewSizeSelected(final Size size, final int rotation);
}
