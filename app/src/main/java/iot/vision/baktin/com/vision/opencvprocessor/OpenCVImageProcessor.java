package iot.vision.baktin.com.vision.opencvprocessor;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.net.Uri;
import android.renderscript.ScriptGroup;
import android.util.Log;

import com.github.mjdev.libaums.fs.UsbFile;
import com.github.mjdev.libaums.fs.UsbFileOutputStream;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_face;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacv.AndroidFrameConverter;
import org.bytedeco.javacv.FrameConverter;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.face.Face;
import org.opencv.features2d.FastFeatureDetector;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.bytedeco.javacpp.opencv_face.LBPHFaceRecognizer;
import org.bytedeco.javacpp.opencv_face.FaceRecognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import iot.vision.baktin.com.vision.R;
import iot.vision.baktin.com.vision.tensor.Classifier;
import iot.vision.baktin.com.vision.usb.UsbHelper;

/**
 * Created by kevin.l.arnado on 21/03/2018.
 */

public class OpenCVImageProcessor
{
    private static String TAG = OpenCVImageProcessor.class.getCanonicalName();
    private static String DEFAULT_TRAINER ="android.resource://iot.vision.baktin.com.vision/raw/trainer.yml";
    public static String OPENCV_TRAIN = "trainer.yml";
    private static OpenCVImageProcessor opencvip = null;

    private float                  mRelativeFaceSize   = 0.2f;
    private int                    mAbsoluteFaceSize   = 0;
    private FaceRecognizer faceRecognizer = null;
    private String trainerFile = "";


    private CascadeClassifier mClassifier=null;
    private Context context=null;

    public static OpenCVImageProcessor getInstance()
    {
        if(opencvip==null)
        {
            opencvip = new OpenCVImageProcessor();
        }
        return opencvip;
    }

    public void setContext(Context context)
    {
        this.context = context;
    }

    public OpenCVImageProcessor()
    {
//
    }

    public void initOpenCV()
    {
//        InputStream is = this.context.getResources().openRawResource(R.raw.haarcascade_frontalface_default);
//        File cascadeDir = this.context.getDir("cascade", Context.MODE_PRIVATE);
//        File mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");


        try {
//            FileOutputStream os = new FileOutputStream(mCascadeFile);
//
//            byte[] buffer = new byte[4096];
//            int bytesRead;
//            while ((bytesRead = is.read(buffer)) != -1) {
//                os.write(buffer, 0, bytesRead);
//            }
//            is.close();
//            os.close();

            if(false)
            {

//                faceRecognizer = LBPHFaceRecognizer.create();
//                UsbFile trainFile = UsbHelper.getInstance().getRootDir(context).search("trainer.yml");
//
//                String path="";
//                UsbFile current;
//                while((current = trainFile.getParent()) != null)
//                path = current.getName() + "/" + path;
//
//                Log.d(TAG,"TRAINPATH: "+path);
//                //fsTrain.open(path);
//                faceRecognizer.read(path);
            }
            else
            {
                Log.d(TAG,"No trainer file supplied. Using default file.");

                InputStream initialStream = context.getResources().openRawResource(R.raw.trainer);

                if(initialStream!=null)
                    Log.d(TAG,"INPUT STREAM:"+initialStream.toString());
                else
                    Log.d(TAG,"INPUT STREAM NULL");

                byte[] buffer = new byte[initialStream.available()];
                initialStream.read(buffer);

                ByteBuffer bfTrainer = ByteBuffer.wrap(buffer);
                UsbFile root  = UsbHelper.getInstance().getRootDir(context);

                UsbFile trainerFile =root.search("trainer.yml");

                if(trainerFile!=null)
                    trainerFile.delete();

                root.createFile("trainer.yml");
                trainerFile = root.search("trainer.yml");
                trainerFile.write(0,bfTrainer);
                trainerFile.flush();
                trainerFile.close();


                String path="";
                UsbFile current = trainerFile.getParent();
                while(current != null)
                {
                    path = current.getName() + "/" + path;
                    current = current.getParent();
                }
                path = path+trainerFile.getName();

                Log.d(TAG,"BUFFER: "+bfTrainer.array().length);



                Log.d(TAG,"Trainer file: "+ trainerFile.getName());

                faceRecognizer = LBPHFaceRecognizer.create();
                BytePointer bPoint = new BytePointer(bfTrainer);
                if(bPoint!=null)
                {
                    Log.d(TAG,"POINTER NOT NULL:");
                }
                else
                {
                    Log.d(TAG,"POINTER NULL:");
                }
                faceRecognizer.read(bPoint);

            }
//            mClassifier = new CascadeClassifier(mCascadeFile.getAbsolutePath());
        }
        catch (Exception a)
        {
            a.printStackTrace();
        }
    }

    public void setTrainedFile(String filename)
    {
        trainerFile = filename;
    }

    static org.opencv.core.Mat mImage=null;
    public Classifier.Recognition getOpenCVResult(Bitmap bmp)
    {
        Bitmap bmp32 = bmp.copy(Bitmap.Config.ARGB_8888, true);
        Buffer pixels = ByteBuffer.allocate(bmp32.getAllocationByteCount());
        bmp32.copyPixelsToBuffer(pixels);
        mImage = new org.opencv.core.Mat();
        Utils.bitmapToMat(bmp32, mImage);

        Mat nImage = new Mat((Pointer)null) { { address = mImage.getNativeObjAddr(); } };

        IntPointer label = new IntPointer(1);
        DoublePointer confidence = new DoublePointer(1);

        int[] label2 = new int[1];
        double[] confidence2 = new double[1];

        if(nImage == null)
        {
            Log.d(TAG,"IMAGE IS NULL");
        }
        else
        {
            if(faceRecognizer!=null) {
                faceRecognizer.predict(nImage, label, confidence);
            }
            else
            {
                Log.d(TAG,"FACE RECOGNIZER IS NULL. NOT INITIALIZED.");
            }
        }

        int predictedLabel = label.get(0);
        float confidenceLabel = (float)confidence.get(0);

        Log.d(TAG,"Predicted label: " + predictedLabel);

        Classifier.Recognition myResult = new Classifier.Recognition(predictedLabel+"",predictedLabel+"",confidenceLabel);
        //Classifier.Recognition myResult = new Classifier.Recognition(label2[0]+"",label2[0]+"",(float)confidence2[0]);

        return myResult;
    }

}
