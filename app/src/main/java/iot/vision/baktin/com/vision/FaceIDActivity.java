package iot.vision.baktin.com.vision;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.things.pio.Gpio;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import iot.vision.baktin.com.vision.camera.CameraHandler;

/**
 * Created by kevin.l.arnado on 21/03/2018.
 */

public class FaceIDActivity extends Activity implements  ImageReader.OnImageAvailableListener{

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;
    private static String TAG = FaceIDActivity.class.getCanonicalName();

    private Context context;
    static Bitmap bitmap=null;
    private CameraHandler mCameraHandler;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    private ImageView mImageView;
    private Gpio mReadyLED;
    private AtomicBoolean mReady = new AtomicBoolean(false);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.gvision_activity);
        mImageView = findViewById(R.id.preview);

        context = getApplicationContext();

        //check google play
        requestPermissionThenOpenCamera();

        mBackgroundThread = new HandlerThread("BackgroundThread");
        mBackgroundThread.start();

        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        mBackgroundHandler.post(mInitializeOnBackground);

        setReady(true);
        findViewById(R.id.container).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mReady.get()) {
                    setReady(false);
                    mBackgroundHandler.post(mBackgroundClickHandler);
                } else {
                    Log.i(TAG, "Sorry, processing hasn't finished. Try again in a few seconds");
                }
            }
        });

        Thread t = new Thread(mPicture);
        t.start();
    }

    private  Runnable mPicture = new Runnable() {
        @Override
        public void run() {
            while(true) {
                mBackgroundHandler.post(mBackgroundClickHandler);

                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private Runnable mBackgroundClickHandler = new Runnable() {
        @Override
        public void run() {

            boolean result = mCameraHandler.takePicture();
            if(!result){
                //processRecognition();
            }
        }
    };

    private Runnable mInitializeOnBackground = new Runnable() {
        @Override
        public void run() {
            mCameraHandler = CameraHandler.getInstance();
            mCameraHandler.initializeCamera(
                    context, mBackgroundHandler,
                    FaceIDActivity.this);
            setReady(true);
        }
    };

    private void requestPermissionThenOpenCamera() {
        if(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    public void setReady(boolean ready) {
        mReady.set(ready);
    }

    @Override
    public void onImageAvailable(ImageReader reader) {

        try (Image image = reader.acquireNextImage()) {

            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            Bitmap myBitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length,null);

            bitmap = ImageProcessor.getInstance().processImage(myBitmap,context);
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                mImageView.setImageBitmap(bitmap);

                setReady(true);
            }
        });
    }
}
