package iot.vision.baktin.com.vision.facelist;

import android.graphics.Bitmap;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by kevin.l.arnado on 04/04/2018.
 */

public class ImageItem{
    public ImageItem(Bitmap mImage, String name)
    {
        this.mImage = mImage;
        this.mName  = name;
        DateFormat dFormat = new SimpleDateFormat("MM/dd/yy HH:mm:ss");
        this.mDate = dFormat.format(new Date());
    }

    private Bitmap mImage;
    private String mName;
    private String mDate;

    public Bitmap getmImage() {
        return mImage;
    }

    public String getmName() {
        return mName;
    }

    public String getmDate() {
        return mDate;
    }
}