package iot.vision.baktin.com.vision;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.Image;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;

import junit.framework.Assert;

import java.nio.ByteBuffer;

/**
 * Created by kevin.l.arnado on 21/03/2018.
 */

public class ImageProcessor {

    private static String TAG = ImageProcessor.class.getCanonicalName();

    private static ImageProcessor myProcessor=null;
    private Bitmap rgbFrameBitmap;
    private Bitmap croppedBitmap;

    public static ImageProcessor getInstance()
    {
        if(myProcessor==null)
        {
            myProcessor = new ImageProcessor();
        }

        return myProcessor;
    }

    public Bitmap processImage(Bitmap image,Context context)
    {
        FaceDetector detector = new FaceDetector.Builder(context)
                .setTrackingEnabled(false)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .build();

        Detector<Face> safeDetector = new SafeFaceDetector(detector);

        Frame frame = new Frame.Builder().setBitmap(image).build();
        SparseArray<Face> faces = safeDetector.detect(frame);

        Log.d(TAG,"[KA]Faces detected: "+faces.size());

        if (!safeDetector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.  Usually this
            // completes before the app is run for the first time.  But if that download has not yet
            // completed, then the above call will not detect any faces.
            //
            // isOperational() can be used to check if the required native library is currently
            // available.  The detector will automatically become operational once the library
            // download completes on device.
            Log.w(TAG, "Face detector dependencies are not yet available.");

            // Check for low storage.  If there is low storage, the native library will not be
            // downloaded, so detection will not become operational.
            IntentFilter lowstorageFilter = new IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW);
            boolean hasLowStorage = context.registerReceiver(null, lowstorageFilter) != null;

            if (hasLowStorage) {
                Log.w(TAG, "low storage");
            }
        }

        //FaceView overlay = (FaceView) findViewById(R.id.faceView);
        //overlay.setContent(image, faces);


        // Although detector may be used multiple times for different images, it should be released
        // when it is no longer needed in order to free native resources.
        safeDetector.release();

        return drawCircles(faces,image,1.0);
    }

    private Bitmap drawCircles(SparseArray<Face> faces,Bitmap image,Double scale) {
        int w = image.getWidth(), h = image.getHeight();

        //Bitmap.Config conf = Bitmap.Config.ARGB_8888; // see other conf types
        Bitmap bmp = image.copy(Bitmap.Config.ARGB_8888,true);//Bitmap.createBitmap(w, h, conf); // this creates a MUTABLE bitmap
        Canvas canvas = new Canvas(bmp);
        canvas.setBitmap(bmp);

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setColor(Color.CYAN);

        for (int i = 0; i < faces.size(); ++i) {
            Face face = faces.valueAt(i);
            canvas.drawRect((float)(face.getPosition().x * scale),
                    (float)(face.getPosition().y * scale),
                    (float)((face.getPosition().x + face.getWidth()) * scale),
                    (float)((face.getPosition().y + face.getHeight()) * scale),
                    paint);
        }

        return bmp;
    }

}
