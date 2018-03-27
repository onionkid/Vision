/*
 * Copyright 2017 The Android Things Samples Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package iot.vision.baktin.com.vision.tensor;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * A classifier specialized to label images using TensorFlow.
 */
public class TensorFlowImageClassifier implements Classifier {



    private static final String TAG = "TFImageClassifier";

    private String[] labels;

    // Pre-allocated buffers.
    private float[] floatValues;
    private int[] intValues;
    private float[] outputs;


    private TensorFlowInferenceInterface inferenceInterface;

    /**
     * Initializes a native TensorFlow session for classifying images.
     *
     * @param context The activity that instantiates this.
     */
    public TensorFlowImageClassifier(Context context) {
        Log.d(TAG, "Loading assets.");
        this.inferenceInterface = new TensorFlowInferenceInterface(
                context.getAssets(),
                VisionHelper.GRAPH_NAME);
        Log.d(TAG, "Completed Loading assets.");
        this.labels = readLabels(context);

        // Pre-allocate buffers.
        intValues = new int[VisionHelper.IMAGE_SIZE * VisionHelper.IMAGE_SIZE];
        floatValues = new float[VisionHelper.IMAGE_SIZE * VisionHelper.IMAGE_SIZE * 3];
        outputs = new float[VisionHelper.NUM_CLASSES];
    }


    public TensorFlowImageClassifier(Context context, InputStream pbFile, InputStream lFile)
    {
        Log.d(TAG, "Loading assets.");
        this.inferenceInterface = new TensorFlowInferenceInterface(pbFile);
        Log.d(TAG, "Completed Loading assets.");
        this.labels = readLabels(lFile);

        // Pre-allocate buffers.
        intValues = new int[VisionHelper.IMAGE_SIZE * VisionHelper.IMAGE_SIZE];
        floatValues = new float[VisionHelper.IMAGE_SIZE * VisionHelper.IMAGE_SIZE * 3];
        outputs = new float[VisionHelper.NUM_CLASSES];
    }

    public static String[] readLabels(Context context) {
        AssetManager assetManager = context.getAssets();
        ArrayList<String> result = new ArrayList<>();
        try (InputStream is = assetManager.open(VisionHelper.LABELS_FILE);
             BufferedReader br = new BufferedReader(new InputStreamReader(is)))
        {
            String line;
            while ((line = br.readLine()) != null) {
                result.add(line);
            }
            return result.toArray(new String[result.size()]);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read labels from " + VisionHelper.LABELS_FILE);
        }
    }

    public static String[] readLabels(InputStream labelFile) {
        ArrayList<String> result = new ArrayList<>();

        try{
            BufferedReader breader = new BufferedReader( new InputStreamReader(labelFile));
            String line;
            while ((line = breader.readLine()) != null) {
                result.add(line);
            }
        }catch (IOException ex1)
        {
            throw new IllegalStateException("Cannot read labels from file. :" + VisionHelper.LABELS_FILE);
        }
        return result.toArray(new String[result.size()]);
    }

    /**
     * Clean up the resources used by the classifier.
     */
    public void destroyClassifier() {
        inferenceInterface.close();
    }


    /**
     * @param image Bitmap containing the image to be classified. The image can be
     *              of any size, but preprocessing might occur to resize it to the
     *              format expected by the classification process, which can be time
     *              and power consuming.
     */
    public List<Classifier.Recognition> doRecognize(Bitmap image) {
        Log.d(TAG, "Start recognition...");
        float[] pixels = VisionHelper.getInstance().getPixels(image, intValues, floatValues);

        // Feed the pixels of the image into the TensorFlow Neural Network
        inferenceInterface.feed(VisionHelper.INPUT_NAME, pixels,
                VisionHelper.NETWORK_STRUCTURE);

        // Run the TensorFlow Neural Network with the provided input
        inferenceInterface.run(VisionHelper.OUTPUT_NAMES);

        // Extract the output from the neural network back into an array of confidence per category
        inferenceInterface.fetch(VisionHelper.OUTPUT_NAME, outputs);

        // Get the results with the highest confidence and map them to their labels
        return VisionHelper.getBestResults(outputs, labels);
    }

    public Recognition getTensorResult(Bitmap image)
    {
        Recognition myResult = new Recognition("-1","Unknown",0f);

        List<Classifier.Recognition> recognitions = doRecognize(image);

        if(!recognitions.isEmpty())
        {
            myResult = recognitions.get(0);
        }

        return myResult;
    }
    
}
