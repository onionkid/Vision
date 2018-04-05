package iot.vision.baktin.com.vision;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.Image;
import android.text.TextPaint;
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
import java.util.ArrayList;
import java.util.Vector;

import iot.vision.baktin.com.vision.opencvprocessor.OpenCVImageProcessor;
import iot.vision.baktin.com.vision.tensor.Classifier;
import iot.vision.baktin.com.vision.tensor.TensorFlowImageClassifier;

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
        return drawCircles(getProcessFaces(image, context),image,1.0);
    }

    private SparseArray<Face> getProcessFaces(Bitmap image,Context context)
    {
        FaceDetector detector = new FaceDetector.Builder(context)
                .setTrackingEnabled(false)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .build();

        Detector<Face> safeDetector = new SafeFaceDetector(detector);

        Frame frame = new Frame.Builder().setBitmap(image).build();
        SparseArray<Face> faces = safeDetector.detect(frame);

        Log.d(TAG,"#Faces detected: "+faces.size());

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
                Log.w(TAG, "[Google Vision] Low storage warning.");
            }
        }

        //FaceView overlay = (FaceView) findViewById(R.id.faceView);
        //overlay.setContent(image, faces);


        // Although detector may be used multiple times for different images, it should be released
        // when it is no longer needed in order to free native resources.
        safeDetector.release();

        return faces;
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

    private void drawRectangleOnFace(Face face, Canvas canvas, Classifier.Recognition data, Float scale)
    {
        String name = data.getTitle();
        String confidence = data.getConfidence().toString();

        //paint settings for rectange on face
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setColor(Color.CYAN);


        float left = (float)(face.getPosition().x * scale);
        float top = (float)(face.getPosition().y * scale);
        float right = (float)((face.getPosition().x + face.getWidth()) * scale);
        float bottom = (float)((face.getPosition().y + face.getHeight()) * scale);

        //draw the rectangle on face detected
        if(data.getConfidence()==0f)
        {
            paint.setColor(Color.DKGRAY);
        }

        canvas.drawRect(left,top,right,bottom,paint);

        //convert to human readable percentage
        String conf = String.format("%.2f",Float.parseFloat(confidence)*100)+"%";

        Log.d(TAG,"DETECTED:::"+name+" -> "+conf+" %");
        String faceLabel = name+"  "+conf+"% ";
        float xpos = left;
        float ypos = bottom+15;

        //paint settings for text
        TextPaint txtpaint = new TextPaint();
        txtpaint.setColor(Color.YELLOW);
        txtpaint.setTextAlign(Paint.Align.LEFT);

        //draw the name and confidence level
        canvas.drawText(faceLabel,xpos,ypos,txtpaint);
    }

    public class ProcessorData
    {
        public Bitmap getFace() {
            return face;
        }

        public void setFace(Bitmap face) {
            this.face = face;
        }

        public Classifier.Recognition getRecognition() {
            return recognition;
        }

        public void setRecognition(Classifier.Recognition recognition) {
            this.recognition = recognition;
        }

        private Bitmap face;
        private Classifier.Recognition recognition;
    }

    public class ProcessorResult
    {
        ArrayList<ProcessorData> resultData = new ArrayList<ProcessorData>();
        Bitmap finalImage;
    }

    public ProcessorResult classifyFace(TensorFlowImageClassifier tClassifier, Bitmap image, Context context)
    {
        Log.d(TAG,"[KA]START IMAGE CLASSIFY!!!");
        int w = image.getWidth(), h = image.getHeight();

        Bitmap bmp = image.copy(Bitmap.Config.ARGB_8888,true);
        Canvas canvas = new Canvas(bmp);
        canvas.setBitmap(bmp);

        SparseArray<Face> sFaces = getProcessFaces(image, context);

        ProcessorResult result = new ProcessorResult();

        for(int i=0;i<sFaces.size();i++)
        {
            Face face = sFaces.valueAt(i);

//            Log.d(TAG,"IMAGE X:"+face.getPosition().x);
//            Log.d(TAG,"IMAGE Y:"+face.getPosition().y);
//            Log.d(TAG,"IMAGE W:"+face.getWidth());
//            Log.d(TAG,"IMAGE H:"+face.getHeight());

            try {
                Bitmap faceBitmap = Bitmap.createBitmap(image,
                        Math.abs((int) face.getPosition().x),
                        Math.abs((int) face.getPosition().y),
                        (int) face.getWidth(),
                        (int) face.getHeight());

                Classifier.Recognition recognition = new Classifier.Recognition("", "", 0f);
                Log.d(TAG,"[KA]START TENSOR");
                recognition = tClassifier.getTensorResult(androidGrayScale(faceBitmap));
                Log.d(TAG,"[KA]END TENSOR");

                Log.d(TAG,"[KA]START DRAW");
                drawRectangleOnFace(face, canvas, recognition, 1f);
                Log.d(TAG,"[KA]END DRAW");

                ProcessorData prRes = new ProcessorData();
                prRes.setFace(faceBitmap);
                prRes.setRecognition(recognition);

                result.resultData.add(prRes);
            }catch (Exception a)
            {
                a.printStackTrace();
                Log.d(TAG,"bitmap exception.");
            }
        }
        result.finalImage = bmp;
        Log.d(TAG,"[KA]END IMAGE CLASSIFY!!!");
        return result;
    }

    public Bitmap classifyFace(OpenCVImageProcessor topenCV, Bitmap image, Context context)
    {
        int w = image.getWidth(), h = image.getHeight();

        Bitmap bmp = image.copy(Bitmap.Config.ARGB_8888,true);
        Canvas canvas = new Canvas(bmp);
        canvas.setBitmap(bmp);

        SparseArray<Face> sFaces = getProcessFaces(image, context);

        for(int i=0;i<sFaces.size();i++)
        {
            Face face = sFaces.valueAt(i);

            Log.d(TAG,"IMAGE X:"+face.getPosition().x);
            Log.d(TAG,"IMAGE Y:"+face.getPosition().y);
            Log.d(TAG,"IMAGE W:"+face.getWidth());
            Log.d(TAG,"IMAGE H:"+face.getHeight());

            Bitmap faceBitmap = Bitmap.createBitmap(image,
                    Math.abs((int) face.getPosition().x),
                    Math.abs((int) face.getPosition().y),
                    (int) face.getWidth(),
                    (int) face.getHeight());

            Classifier.Recognition recognition = new Classifier.Recognition("","",0f);
            recognition = topenCV.getOpenCVResult(androidGrayScale(faceBitmap));
            drawRectangleOnFace(face,canvas,recognition,1f);

        }

        return bmp;
    }

    private Bitmap androidGrayScale(final Bitmap bmpOriginal) {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix colorMatrix = new ColorMatrix();
        colorMatrix.setSaturation(0);
        ColorMatrixColorFilter colorMatrixFilter = new ColorMatrixColorFilter(colorMatrix);
        paint.setColorFilter(colorMatrixFilter);
        canvas.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

}
