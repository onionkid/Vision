package iot.vision.baktin.com.vision.tensor;

import android.graphics.Bitmap;
import android.media.ThumbnailUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Created by kevin.l.arnado on 26/03/2018.
 */

public class VisionHelper {
    private static VisionHelper myHelper = null;

    public static final int IMAGE_SIZE = 299;
    public static final int IMAGE_MEAN = 128;
    public static final float IMAGE_STD = 128f;


    public static long[] NETWORK_STRUCTURE = {1, IMAGE_SIZE, IMAGE_SIZE, 3};
    public static final int NUM_CLASSES = 10;

    private static final int MAX_BEST_RESULTS = 1;
    private static final float RESULT_CONFIDENCE_THRESHOLD = 0.8f; //prev 0.1f

    public static final String OUTPUT_NAME = "final_result";
    public static final String INPUT_NAME = "Mul";

    public static String[] OUTPUT_NAMES = {OUTPUT_NAME};


    public static String GRAPH_NAME ="kiboi_mobile.pb";
    public static String LABELS_FILE = "kiboi_mobile.txt";

    public static VisionHelper getInstance()
    {
        if(myHelper == null)
        {
            myHelper = new VisionHelper();
        }

        return myHelper;
    }

    public static float[] getPixels(Bitmap bitmap, int[] intValues, float[] floatValues) {
        if (bitmap.getWidth() != IMAGE_SIZE || bitmap.getHeight() != IMAGE_SIZE) {
            // rescale the bitmap if needed
            bitmap = ThumbnailUtils.extractThumbnail(bitmap, IMAGE_SIZE, IMAGE_SIZE);
        }

        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        // Preprocess the image data from 0-255 int to normalized float based
        // on the provided parameters.
        for (int i = 0; i < intValues.length; ++i) {
            final int val = intValues[i];
            floatValues[i * 3] = (((val >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD;
            floatValues[i * 3 + 1] = (((val >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD;
            floatValues[i * 3 + 2] = ((val & 0xFF) - IMAGE_MEAN) / IMAGE_STD;
        }
        return floatValues;
    }

    public static List<Classifier.Recognition> getBestResults(float[] confidenceLevels, String[] labels) {
        // Find the best classifications.
        PriorityQueue<Classifier.Recognition> pq = new PriorityQueue<>(MAX_BEST_RESULTS,
                new Comparator<Classifier.Recognition>() {
                    @Override
                    public int compare(Classifier.Recognition lhs, Classifier.Recognition rhs) {
                        // Intentionally reversed to put high confidence at the head of the queue.
                        return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                    }
                });

        for (int i = 0; i < confidenceLevels.length; ++i) {
            if (confidenceLevels[i] > RESULT_CONFIDENCE_THRESHOLD) {
                pq.add(new Classifier.Recognition("" + i, labels[i], confidenceLevels[i]));
            }
        }

        ArrayList<Classifier.Recognition> recognitions = new ArrayList<Classifier.Recognition>();
        int recognitionsSize = Math.min(pq.size(), MAX_BEST_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }
        return recognitions;
    }
}
