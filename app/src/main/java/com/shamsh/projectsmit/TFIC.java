package com.shamsh.projectsmit;

import android.annotation.SuppressLint;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;


public class TFIC implements Classifier {

    private final int MAX_RESULTS = 3;
    private final int BATCH_SIZE = 1;
    private final int PIXEL_SIZE = 3;
    private final float THRESHOLD = 0.1f;

    private Interpreter interpreter;
    private int inputSize;
    private List<String> labelList;

    private TFIC() {

    }

    static Classifier create(AssetManager assetManager,
                             String modelPath,
                             String labelPath,
                             int inputSize) throws IOException {

        TFIC classifier = new TFIC();
        classifier.interpreter = new Interpreter(classifier.loadModelFile(assetManager, modelPath));
        classifier.labelList = classifier.loadLabelList(assetManager, labelPath);
        classifier.inputSize = inputSize;

        return classifier;
    }

    @Override
    public List<Recognition> recognizeImage(Bitmap bitmap) {
        ByteBuffer byteBuffer = changeBit2ByteBuf(bitmap);
        float[][] result = new float[1][labelList.size()];
        interpreter.run(byteBuffer, result);
        return getHighLowResult(result);
    }

    private ByteBuffer changeBit2ByteBuf(Bitmap bitmap) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * inputSize * inputSize * PIXEL_SIZE * 4); // Multiply by 4 for float32
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues = new int[inputSize * inputSize];
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        int pixel = 0;
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                final int val = intValues[pixel++];
                byteBuffer.putFloat((float) ((val >> 16) & 0xFF) / 255.0f);
                byteBuffer.putFloat((float) ((val >> 8) & 0xFF) / 255.0f);
                byteBuffer.putFloat((float) (val & 0xFF) / 255.0f);
            }
        }
        return byteBuffer;
    }

    @SuppressLint("DefaultLocale")
    private List<Recognition> getHighLowResult(float[][] labelProbArray) {

        PriorityQueue<Recognition> pq =
                new PriorityQueue<>(
                        MAX_RESULTS,
                        new Comparator<Recognition>() {
                            @Override
                            public int compare(Recognition lhs, Recognition rhs) {
                                // Reversed to put high confidence at the head of the queue.
                                return Float.compare(rhs.getConfidence(), lhs.getConfidence());
                            }
                        });

        for (int i = 0; i < labelList.size(); ++i) {
            float confidence = labelProbArray[0][i];
            if (confidence > THRESHOLD) {
                pq.add(new Recognition("" + i,
                        labelList.size() > i ? labelList.get(i) : "unknown",
                        confidence));
            }
        }

        final ArrayList<Recognition> recognitions = new ArrayList<>();
        int recognitionsSize = Math.min(pq.size(), MAX_RESULTS);
        for (int i = 0; i < recognitionsSize; ++i) {
            recognitions.add(pq.poll());
        }

        return recognitions;
    }

    @Override
    public void close() {
        interpreter.close();
        interpreter = null;
    }

    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private List<String> loadLabelList(AssetManager assetManager, String labelPath) throws IOException {
        List<String> labelList = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open(labelPath)));
        String line;
        while ((line = reader.readLine()) != null) {
            labelList.add(line);
        }
        reader.close();
        return labelList;
    }
}
