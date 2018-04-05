package iot.vision.baktin.com.vision;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileInputStream;
import com.google.android.things.pio.Gpio;
import com.kevalpatel2106.ftp_server.FTPManager;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import iot.vision.baktin.com.vision.camera.CameraHandler;
import iot.vision.baktin.com.vision.facelist.FaceListAdapter;
import iot.vision.baktin.com.vision.facelist.ImageItem;
import iot.vision.baktin.com.vision.opencvprocessor.OpenCVImageProcessor;
import iot.vision.baktin.com.vision.tensor.TensorFlowImageClassifier;
import iot.vision.baktin.com.vision.tensor.VisionHelper;
import iot.vision.baktin.com.vision.usb.UsbHelper;

/**
     * Created by kevin.l.arnado on 21/03/2018.
     */

public class FaceIDActivity extends AppCompatActivity implements  ImageReader.OnImageAvailableListener{

    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;
    private static final int REQUEST_USB_PERMISSION = 300;
    final String ACTION_USB_PERMISSION = "com.example.udevice.USB_PERMISSION";
    private static final int MY_PERMISSIONS_REQUEST_ACCOUNTS = 1;

    private static int MAX_LIST = 20;
    private static int PIC_DELAY = 2000;
    private static String TAG = FaceIDActivity.class.getCanonicalName();

    private Context context;
    static Bitmap bitmap=null;
    private CameraHandler mCameraHandler;

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;

    PendingIntent mPermissionIntent;

    private ImageView mImageView;
    private RelativeLayout mProgressBar;
    private ProgressBar mProgressBarTensor;
    private FloatingActionButton fab;
    private RecyclerView mImageList;
    private TextView mIPAdd;

    private ArrayList<ImageItem> imageItemList;

    private Gpio mReadyLED;
    private AtomicBoolean mReady = new AtomicBoolean(false);

    UsbFile usbGraph= null;
    UsbFile usbLabel =null;
    UsbFile usbOpenCV =null;

    private int progressBarVisible = View.GONE;
    private int progressBarTensorVisible = View.GONE;

    UsbManager usbManager =null;
    UsbDevice usbDevice=null;

    private TensorFlowImageClassifier mTensorFlowClassifier;

    public static final boolean OPENCV_MODE = true;
    public static final boolean TENSOR_MODE = false;

    public static boolean detectMode = TENSOR_MODE;

    //FTP
    private FTPManager mFTPManager;

    static{ System.loadLibrary("opencv_java"); }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gvision_activity);

        //initialize the image view
        mImageView = findViewById(R.id.preview);
        mIPAdd = findViewById(R.id.txtIpAddress);
        mProgressBar = findViewById(R.id.progressLayout);
        mProgressBarTensor = findViewById(R.id.progressTensor);

        fab = findViewById(R.id.fab);

        context = getApplicationContext();

        mImageList = findViewById(R.id.imageList);
        loadRecycler();

        //set on click listener for fab
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Checking for updates...", Snackbar.LENGTH_SHORT)
                        .setAction("Action", null).show();
            }
        });

        initUSBEvent();
        if(checkAndRequestPermissions()) {
            startThreads();
            observer.startWatching(); //START OBSERVING
            mFTPManager = new FTPManager(context);
//            mFTPManager.startServer();


        }
        mIPAdd.setText("");
        //startThreads();
    }

    private void loadRecycler()
    {
        mLayoutManager = new LinearLayoutManager(context);
        mImageList.setLayoutManager(mLayoutManager);
        mImageList.setHasFixedSize(true);
        mImageList.setItemAnimator(new DefaultItemAnimator());

        imageItemList = new ArrayList<ImageItem>();
        mAdapter = new FaceListAdapter(context,imageItemList);
        mImageList.setAdapter(mAdapter);
    }

    private boolean checkAndRequestPermissions() {
        int permissionCAMERA = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA);
        int storagePermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE);
//        int wifiPermission = ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_WIFI_STATE);
//        int internetPermission = ContextCompat.checkSelfPermission(this,Manifest.permission.INTERNET);

        List<String> listPermissionsNeeded = new ArrayList<>();
        if (storagePermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (permissionCAMERA != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.CAMERA);
        }

        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]), MY_PERMISSIONS_REQUEST_ACCOUNTS);
            return false;
        }

        return true;
    }

    public String getLocalIpAddress(){
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return "-";
    }

    private void startThreads()
    {
        mBackgroundThread = new HandlerThread("BackgroundThread");
        mBackgroundThread.start();

        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        mBackgroundHandler.post(mInitializeOnBackground);

        //start thread to take picture every 3 seconds when ready
        Thread t = new Thread(mPicture);
        t.start();
    }

    private void stopThreads()
    {
//        mBackgroundHandler.removeCallbacks(mB);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanUp();
        mFTPManager.stopServer();
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    if(detectMode==OPENCV_MODE)
                        OpenCVImageProcessor.getInstance().setContext(context);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    public void onResume() {;
        super.onResume();
        if(detectMode==OPENCV_MODE) {
            if (!OpenCVLoader.initDebug()) {
                Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
                OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_11, this, mLoaderCallback);
            } else {
                Log.d(TAG, "OpenCV library found inside package. Using it!");
                mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
            }
        }
    }

    private void initUSBEvent()
    {
        Log.d(TAG,"REQUEST USB PERMISSION");
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(mUsbReceiver,filter);
    }

    @Override    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCOUNTS:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission Granted Successfully. Write working code here.
                    Log.d(TAG,"PERMISSIONS GRANTED");
                } else {
                    //You did not accept the request can not use the functionality.
                    Log.d(TAG,"PERMISSIONS NOT GRANTED. REQUEST AGAIN.");
                    checkAndRequestPermissions();
                }
                break;
        }
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
                Log.d(TAG,"Not yet ready.");
            }

            try {
                Thread.sleep(PIC_DELAY);
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

            showProgressBar(true);

            if(detectMode == TENSOR_MODE)
            {
                loadGraphFile();
            }else if(detectMode == OPENCV_MODE)
            {
                loadOpenCVFile();
            }


            showProgressBar(false);

            setReady(true);

        }
    };

    private void showProgressBar(boolean show)
    {
        if(show){
            progressBarVisible = View.VISIBLE;
        }
        else {
            progressBarVisible = View.GONE;
            //show update button
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fab.setVisibility(View.VISIBLE);
                }
            });
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressBar.setVisibility(progressBarVisible);
            }
        });
    }

    public void showProgressBarTensor(boolean show)
    {
        if(show){
            progressBarTensorVisible = View.VISIBLE;
        }
        else {
            progressBarTensorVisible = View.GONE;

        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressBarTensor.setVisibility(progressBarTensorVisible);
            }
        });
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG,"BROADCAST RECEIVER USB");
            if (ACTION_USB_PERMISSION.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {

                    if (device != null) {
                        //setupDevice();
                        Log.d(TAG,"[KA] DEVICE READY USB!!!");
                        //startThreads();
                    }
                }

            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                Log.d(TAG, "USB device attached");

                // determine if connected device is a mass storage devuce
                if (device != null) {
                    setReady(false);
                    loadGraphFile();
                    setReady(true);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                Log.d(TAG, "USB device detached");

                // determine if connected device is a mass storage devuce
                if (device != null) {
                    setReady(false);
                    cleanUp();
                }
            }
        }
    };

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

            showProgressBarTensor(true);

            if(detectMode == TENSOR_MODE)
            {
                //tensor flow classify image

                resData = ImageProcessor.getInstance().classifyFace(mTensorFlowClassifier,myBitmap,context);
                bitmap = resData.finalImage;
            }
            else if(detectMode == OPENCV_MODE)
            {
                bitmap = ImageProcessor.getInstance().classifyFace(OpenCVImageProcessor.getInstance(),myBitmap,context);
            }

        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {

                mImageView.setImageBitmap(bitmap);
                showProgressBarTensor(false);
                if(resData!=null && resData.resultData.size() > 0)
                    updateList(resData.resultData);
                setReady(true);
            }
        });
    }



    private void loadOpenCVFile()
    {
        try {
            usbOpenCV = UsbHelper.getInstance().getRootDir(context).search(OpenCVImageProcessor.OPENCV_TRAIN);

            if(usbOpenCV!=null) {
                Log.d(TAG,"OPENCVTRAININGFILE::"+usbOpenCV.getName());
                Log.d(TAG,"OPENCVTRAININGFILE LENGTH::"+usbOpenCV.getLength());
                Log.d(TAG,"OPENCVTRAININGFILE LAST MOD::"+usbOpenCV.lastModified());
                OpenCVImageProcessor.getInstance().setTrainedFile(usbOpenCV.getName());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        OpenCVImageProcessor.getInstance().initOpenCV();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
            UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
        }
    }

    private void loadGraphFile()
    {
        try {


            UsbFile root = UsbHelper.getInstance().getRootDir(context);
            Log.d(TAG,"ROOT::: "+root.getName());

            usbGraph = root.search(VisionHelper.GRAPH_NAME);
            Log.d(TAG,"GRAPH::"+usbGraph.getName());

            usbLabel = root.search(VisionHelper.LABELS_FILE);
            Log.d(TAG,"LABEL::"+usbLabel.getName());

            if(usbGraph!=null && usbLabel!=null)
            {
                InputStream isGraph = new UsbFileInputStream(usbGraph);
                InputStream isLabel = new UsbFileInputStream(usbLabel);
                //remove old tensor flow first
                if(mTensorFlowClassifier!=null) {
                    mTensorFlowClassifier.destroyClassifier();
                    mTensorFlowClassifier = null;
                }

                Log.d(TAG,"[tensor] initializing classifier.");
                mTensorFlowClassifier = new TensorFlowImageClassifier(context,isGraph,isLabel);
                Log.d(TAG,"[tensor] done initializing classifier.");
            }
            else
            {
                Log.d(TAG,"FILES NULL");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        //init tensorflow once the pb files area loaded

    }

    private void cleanUp()
    {
        mTensorFlowClassifier.destroyClassifier();
        mTensorFlowClassifier = null;
        try {
            usbGraph.close();
            usbLabel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //////////////////// FACE LIST CHORVA
    private FaceListAdapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    static ImageProcessor.ProcessorResult resData=null;


    private void updateList(ArrayList<ImageProcessor.ProcessorData> data)
    {
        for (ImageProcessor.ProcessorData iData: data
             ) {
            if(imageItemList.size()>=MAX_LIST)
            {
                imageItemList.remove(imageItemList.size()-1);
            }
            imageItemList.add(0,new ImageItem(iData.getFace(),iData.getRecognition().getTitle()));
            mAdapter.notifyDataSetChanged();
            mAdapter.notifyItemChanged(0);
        }
    }

    /////////////// FILE OBSERVER
    FileObserver observer = new FileObserver("/mnt/usb/") { // set up a file observer to watch this directory on sd card
        @Override
        public void onEvent(int event, String file) {
            if(event == FileObserver.MODIFY && (file.equals(VisionHelper.GRAPH_NAME) || file.equals(VisionHelper.LABELS_FILE))) {
                Log.d(TAG,"Tensorflow graph file changed. Loading new file...");
                Toast.makeText(getBaseContext(), "Tensorflow graph file changed. Loading new file...", Toast.LENGTH_LONG).show();
            }
        }
    };

    /*
    * Check if target is a mobile device or Android Things
    * */
    private boolean checkDeviceModule(Context context){
        final PackageManager pm = context.getPackageManager();
        boolean isRunningAndroidThings = pm.hasSystemFeature("android.hardware.type.embedded");
        Log.d(TAG, "isRunningAndroidThings: " + isRunningAndroidThings);
        return isRunningAndroidThings;
    }
}
