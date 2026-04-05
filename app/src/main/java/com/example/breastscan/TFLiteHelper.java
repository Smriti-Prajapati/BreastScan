package com.example.breastscan;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class TFLiteHelper {

    private Interpreter interpreter;

    public TFLiteHelper(Context context) throws Exception {
        interpreter = new Interpreter(loadModelFile(context));
    }

    private ByteBuffer loadModelFile(Context context) throws Exception {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd("image_model.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    public String predict(Bitmap bitmap) {

        // ✅ Resize image
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true);

        // ✅ Allocate buffer
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(4 * 224 * 224 * 3);
        inputBuffer.order(ByteOrder.nativeOrder());

        int[] intValues = new int[224 * 224];
        resized.getPixels(intValues, 0, 224, 0, 0, 224, 224);

        int pixel = 0;
        for (int i = 0; i < 224; i++) {
            for (int j = 0; j < 224; j++) {
                int val = intValues[pixel++];

                // ✅ Normalization [-1, 1]
                inputBuffer.putFloat((((val >> 16) & 0xFF) - 127.5f) / 127.5f);
                inputBuffer.putFloat((((val >> 8) & 0xFF) - 127.5f) / 127.5f);
                inputBuffer.putFloat(((val & 0xFF) - 127.5f) / 127.5f);
            }
        }

        inputBuffer.rewind();

        // OUTPUT
        float[][] output = new float[1][3];
        interpreter.run(inputBuffer, output);

        float[] probs = output[0];

        // ✅ Debug logs
        Log.d("MODEL_OUTPUT", "Benign: " + probs[0] +
                " Malignant: " + probs[1] +
                " Normal: " + probs[2]);

        // ✅ Find max probability
        int maxIndex = 0;
        float maxProb = probs[0];

        for (int i = 1; i < probs.length; i++) {
            if (probs[i] > maxProb) {
                maxProb = probs[i];
                maxIndex = i;
            }
        }

        String[] labels = {"Benign", "Malignant", "Normal"};

        String result;

        float benign = probs[0];
        float malignant = probs[1];
        float normal = probs[2];

        // 🔥 EXTRA ANALYSIS: dark pixel ratio
        int darkPixels = 0;

        for (int val : intValues) {
            int r = (val >> 16) & 0xFF;
            int g = (val >> 8) & 0xFF;
            int b = val & 0xFF;

            int gray = (r + g + b) / 3;

            if (gray < 80) {
                darkPixels++;
            }
        }

        float darkRatio = (float) darkPixels / intValues.length;

        Log.d("IMAGE_ANALYSIS", "Dark Ratio: " + darkRatio);

        // 🔥 FINAL HYBRID DECISION
        if (malignant > 0.05 && malignant > (benign * 0.2)) {
            result = "Malignant";
        }
        else if (darkRatio > 0.35) {
            result = "Malignant";
        }
        else if (normal > 0.3) {
            result = "Normal";
        }
        else {
            result = "Benign";
        }

        // ✅ Final log
        Log.d("FINAL_RESULT", "Predicted: " + result);

        return result;
    }
}