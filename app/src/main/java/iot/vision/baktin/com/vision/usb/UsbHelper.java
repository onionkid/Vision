package iot.vision.baktin.com.vision.usb;

import android.content.Context;
import android.util.Log;

import com.github.mjdev.libaums.UsbMassStorageDevice;
import com.github.mjdev.libaums.fs.FileSystem;
import com.github.mjdev.libaums.fs.UsbFile;

import java.io.IOException;

/**
 * Created by kevin.l.arnado on 02/04/2018.
 */

public class UsbHelper
{
    private static String TAG = UsbHelper.class.getCanonicalName();
    private static UsbHelper usbH = null;
    public static UsbHelper getInstance()
    {
        if(usbH==null)
        {
            usbH = new UsbHelper();
        }

        return usbH;
    }

    public UsbFile getRootDir(Context context)
    {
        UsbMassStorageDevice[] devices = UsbMassStorageDevice.getMassStorageDevices(context);
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

        return root;
    }

}
