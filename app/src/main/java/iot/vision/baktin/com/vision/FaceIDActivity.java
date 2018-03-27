package iot.vision.baktin.com.vision;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileInputStream;
import com.google.android.things.pio.Gpio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import iot.vision.baktin.com.vision.camera.CameraHandler;
import iot.vision.baktin.com.vision.tensor.TensorFlowImageClassifier;
import iot.vision.baktin.com.vision.tensor.VisionHelper;

/**
         * Created by kevin.l.arnado on 21/03/2018.
         */

    public class FaceIDActivity extends Activity implements  ImageReader.OnImageAvailableListener{

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;

    private static final int REQUEST_USB_PERMISSION = 300;
    final String ACTION_USB_PERMISSION = "com.example.udevice.USB_PERMISSION";

    private static String TAG = FaceIDActivity.class.getCanonicalName();

    private Context context;
    static Bitmap bitmap=null;
    private CameraHandler mCameraHandler;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    PendingIntent mPermissionIntent;

    private ImageView mImageView;
    private ProgressBar mProgressBar;
    private Gpio mReadyLED;
    private AtomicBoolean mReady = new AtomicBoolean(false);

    File graph;
    File labels;


    UsbManager usbManager =null;
    UsbDevice usbDevice=null;

    private TensorFlowImageClassifier mTensorFlowClassifier;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.gvision_activity);
        mImageView = findViewById(R.id.preview);
        mProgressBar = findViewById(R.id.progressBar);

        context = getApplicationContext();

        //check google play
        requestUSBPermission();
        requestPermissionThenOpenCamera();

        mBackgroundThread = new HandlerThread("BackgroundThread");
        mBackgroundThread.start();

        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        mBackgroundHandler.post(mInitializeOnBackground);


//        findViewById(R.id.container).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                if (mReady.get()) {
//                    setReady(false);
//                    mBackgroundHandler.post(mBackgroundClickHandler);
//                } else {
//                    Log.i(TAG, "Sorry, processing hasn't finished. Try again in a few seconds");
//                }
//            }
//        });
        Thread t = new Thread(mPicture);
        t.start();
    }

    private void requestUSBPermission()
    {
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbReceiver,filter);
    }

    private  Runnable mPicture = new Runnable() {
        @Override
        public void run() {
            while(true) {
                if(mReady.get()) {
                    mBackgroundHandler.post(mBackgroundClickHandler);
                }
                else
                {
                    Log.d(TAG,"[KA] NOT YET READY!!");
                }

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
            Log.d(TAG,"INITIAIZE CAMERA");
//            initUSB();
            //mProgressBar.setVisibility(View.VISIBLE);
            loadGraphFile();
            //mProgressBar.setVisibility(View.INVISIBLE);
            setReady(true);
        }
    };

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {

                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {

                    if (device != null) {
                        //setupDevice();
                        Log.d(TAG,"[KA] DEVICE READY USB!!!");

                    }
                }

            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                Log.d(TAG, "USB device attached");

                // determine if connected device is a mass storage devuce
                if (device != null) {
                    setReady(true);
                    loadGraphFile();
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                Log.d(TAG, "USB device detached");

                // determine if connected device is a mass storage devuce
                if (device != null) {
                    setReady(false);
                }
            }
        }
    };

    public void initUSB() {
        mPermissionIntent = PendingIntent.getBroadcast(context, 0, new
                Intent(ACTION_USB_PERMISSION), 0);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while (deviceIterator.hasNext()) {
            usbDevice = deviceIterator.next();
            usbManager.requestPermission(usbDevice, mPermissionIntent);
            String Model = usbDevice.getDeviceName();
            Log.d(TAG,"USB MODEL: "+Model);
            Log.d(TAG,"USB INTERFACE COUNT"+usbDevice.getInterfaceCount());
        }
    }

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
            setReady(false);
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            Bitmap myBitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.length,null);

            //tensor flow classify image
            bitmap = ImageProcessor.getInstance().classifyFace(mTensorFlowClassifier,myBitmap,context);
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                mImageView.setImageBitmap(bitmap);

                setReady(true);
            }
        });
    }

    private void loadGraphFile()
    {
        UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(context /* Context or Activity */);
        Log.d(TAG,"DEVICES: "+devices.length);
        UsbFile root=null;
        for(UsbMassStorageDevice device: devices) {

            // before interacting with a device you need to call init()!
            try {
                device.init();
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Only uses the first partition on the device
            FileSystem currentFs = device.getPartitions().get(0).getFileSystem();
            Log.d(TAG, "Capacity: " + currentFs.getCapacity());
            Log.d(TAG, "Occupied Space: " + currentFs.getOccupiedSpace());
            Log.d(TAG, "Free Space: " + currentFs.getFreeSpace());
            Log.d(TAG, "Chunk size: " + currentFs.getChunkSize());

            root = device.getPartitions().get(0).getFileSystem().getRootDirectory();
        }

        String sPath = root.getName();
        UsbFile usbGraph= null;
        UsbFile usbLabel =null;
        try {

            for(int i=0;i<root.list().length;i++)
            {
                Log.d(TAG,"FILE LIST:"+root.list()[i]);
            }

            usbGraph = root.search(VisionHelper.GRAPH_NAME);
            Log.d(TAG,"GRAPH::"+usbGraph.getName());

            usbLabel = root.search(VisionHelper.LABELS_FILE);
            Log.d(TAG,"LABEL::"+usbLabel.getName());

        } catch (IOException e) {
            e.printStackTrace();
        }


        //init tensor
        if(usbGraph!=null & usbLabel!=null)
        {
            InputStream isGraph = new UsbFileInputStream(usbGraph);
            InputStream isLabel = new UsbFileInputStream(usbLabel);
            mTensorFlowClassifier = new TensorFlowImageClassifier(context,isGraph,isLabel);
        }
        else
        {
            Log.d(TAG,"FILES NULL");
        }

    }

    private InputStream getGraphStream()
    {
        InputStream isStream = null;

        if(graph!=null)
        {
            try {
                isStream = new FileInputStream(graph);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        return isStream;
    }

    private InputStream getLabelStream()
    {
        InputStream isStream = null;

        if(graph!=null)
        {
            try {
                isStream = new FileInputStream(labels);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        return isStream;
    }


}
