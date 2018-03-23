package iot.vision.baktin.com.vision.opencvprocessor;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * Created by kevin.l.arnado on 21/03/2018.
 */

public class OpenCVImageProcessor
{
    private static OpenCVImageProcessor opencvip = null;

    public static OpenCVImageProcessor getInstance()
    {
        if(opencvip==null)
        {
            opencvip = new OpenCVImageProcessor();
        }
        return opencvip;
    }

    public Bitmap processImage(Bitmap image, Context context)
    {
        Bitmap pImage = null;



        return pImage;
    }

}
