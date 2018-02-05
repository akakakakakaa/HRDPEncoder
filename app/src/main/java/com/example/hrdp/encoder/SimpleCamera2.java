package com.example.hrdp.encoder;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static android.content.Context.CAMERA_SERVICE;

/**
 * Created by icns on 2018-01-12.
 */

public class SimpleCamera2 {
    protected static final String TAG = "VideoProcessing";
    private Context context;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    protected static final int CAMERACHOICE = CameraCharacteristics.LENS_FACING_BACK;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession session;
    protected ImageReader imageReader;

    protected CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.i(TAG, "CameraDevice.StateCallback onOpened");
            cameraDevice = camera;
            actOnReadyCameraDevice();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.w(TAG, "CameraDevice.StateCallback onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "CameraDevice.StateCallback onError " + error);
        }
    };

    public SimpleCamera2(Context context) {
        this.context = context;
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            Log.i(TAG, "CameraCaptureSession.StateCallback onConfigured");
            SimpleCamera2.this.session = session;
            try {
                session.setRepeatingRequest(createCaptureRequest(), null, null);
            } catch (CameraAccessException e){
                Log.e(TAG, e.getMessage());
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
    };

    protected ImageReader.OnImageAvailableListener onImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            //Log.i(TAG, "onImageAvailable");
            Image img = reader.acquireLatestImage();
            if (img != null)
                processImage(img);
        }
    };

    public void readyCamera()
    {
        CameraManager manager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
        try {
            String pickedCamera = getCamera(manager);
            manager.openCamera(pickedCamera, cameraStateCallback, null);
            imageReader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 10 /* images buffered */);
            imageReader.setOnImageAvailableListener(onImageAvailableListener, null);
            Log.i(TAG, "imageReader created");
        } catch (CameraAccessException e){
            Log.e(TAG, e.getMessage());
        }
    }


    /**
     *  Return the Camera Id which matches the field CAMERACHOICE.
     */
    public String getCamera(CameraManager manager){
        try {
            String[] camIds = manager.getCameraIdList();
            if (camIds.length < 1) {
                Log.d(TAG, "No cameras found");
                return null;
            }
            String id = camIds[0];
            return id;
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
        return null;
    }

    public void actOnReadyCameraDevice()
    {
        try {
            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), sessionStateCallback, null);
        } catch (CameraAccessException e){
            Log.e(TAG, e.getMessage());
        }
    }

    public void closeCamera() {
        try {
            session.abortCaptures();
        } catch (CameraAccessException e){
            Log.e(TAG, e.getMessage());
        }
        session.close();
    }

    /**
     *  Process image data as desired.
     */
    private void processImage(Image image){
        //Process image data
        //Log.d("test","process image");
        mBackgroundHandler.post(new ImageBuffer(image));
        /*
        if(CameraActivity.bound) {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            CameraActivity.service.sendFrame(bytes);
        }
        */
    }

    protected CaptureRequest createCaptureRequest() {
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            builder.addTarget(imageReader.getSurface());
            return builder.build();
        } catch (CameraAccessException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    public class ImageBuffer implements Runnable {
        Image image;

        ImageBuffer(Image image) {
            this.image = image;
        }

        @Override
        public void run() {
            if(CameraActivity.bound) {
                ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
                ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
                ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();
                byte[] yBytes = new byte[yBuffer.remaining()];
                byte[] uBytes = new byte[uBuffer.remaining()];
                byte[] vBytes = new byte[vBuffer.remaining()];
                yBuffer.get(yBytes);
                uBuffer.get(uBytes);
                vBuffer.get(vBytes);
                ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
                try {
                    outputBytes.write(yBytes);
                    outputBytes.write(vBytes);
                    outputBytes.write(uBytes);
                    CameraActivity.service.sendFrame(outputBytes.toByteArray());
                    outputBytes.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                //Log.d("test", "bound");
            }
            image.close();
            //Log.d("test", "end");
        }
    }
}
