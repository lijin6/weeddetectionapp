package com.example.weeddetectionapp;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * ModelHandler: YOLO 模型加载 + 推理 + NMS 后处理
 */
public class ModelHandler {

    private Interpreter interpreter;

    private static final int INPUT_SIZE = 640;
    private static final int OUTPUT_ROWS = 12; // 4个坐标 + 8个类别
    private static final int OUTPUT_COLS = 8400; // 根据你的模型调整

    private static final String[] LABELS = {
            "蒿属", "冰草属", "藜属", "地肤属",
            "蓟属", "猪毛菜属", "萝卜属", "野决明属"
    };

    private final ByteBuffer inputBuffer;
    private final int[] intValues;
    private final float[][][] outputArray;

    public ModelHandler(Context context, String modelPath) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        interpreter = new Interpreter(loadModelFile(context, modelPath), options);

        inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        intValues = new int[INPUT_SIZE * INPUT_SIZE];
        outputArray = new float[1][OUTPUT_ROWS][OUTPUT_COLS];
    }

    public List<DetectionResult> runInference(Bitmap bitmap) {
        // 1. 缩放到模型输入
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        inputBuffer.rewind();
        resized.getPixels(intValues, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);

        for (int pixel : intValues) {
            inputBuffer.putFloat(((pixel >> 16) & 0xFF) / 255f);
            inputBuffer.putFloat(((pixel >> 8) & 0xFF) / 255f);
            inputBuffer.putFloat((pixel & 0xFF) / 255f);
        }

        if (resized != bitmap) resized.recycle();

        // 2. 推理
        interpreter.run(inputBuffer, outputArray);

        // 3. 后处理
        List<DetectionResult> results = nms(postProcess(outputArray[0]));
        Log.d("AI_DEBUG", "推理完成，最终框数量: " + results.size());
        return results;
    }

    private List<DetectionResult> postProcess(float[][] out) {
        List<DetectionResult> detections = new ArrayList<>();

        for (int i = 0; i < OUTPUT_COLS; i++) {
            float maxConfidence = 0f;
            int classId = -1;

            for (int c = 4; c < 12; c++) {
                if (out[c][i] > maxConfidence) {
                    maxConfidence = out[c][i];
                    classId = c - 4;
                }
            }

            if (maxConfidence > 0.45f) {
                float cx = out[0][i];
                float cy = out[1][i];
                float w = out[2][i];
                float h = out[3][i];

                RectF rect = new RectF(
                        Math.max(0, cx - w / 2f),
                        Math.max(0, cy - h / 2f),
                        Math.min(INPUT_SIZE, cx + w / 2f),
                        Math.min(INPUT_SIZE, cy + h / 2f)
                );

                String label = (classId >= 0 && classId < LABELS.length) ? LABELS[classId] : "未知杂草";
                detections.add(new DetectionResult(rect, maxConfidence, label));
            }
        }
        return detections;
    }

    private List<DetectionResult> nms(List<DetectionResult> detections) {
        detections.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        List<DetectionResult> results = new ArrayList<>();

        while (!detections.isEmpty()) {
            DetectionResult best = detections.remove(0);
            results.add(best);

            detections.removeIf(next -> calculateIoU(best.boundingBox, next.boundingBox) > 0.45f);
        }
        return results;
    }

    private float calculateIoU(RectF a, RectF b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);

        if (left < right && top < bottom) {
            float intersection = (right - left) * (bottom - top);
            float union = (a.width() * a.height()) + (b.width() * b.height()) - intersection;
            return intersection / union;
        }
        return 0f;
    }

    private MappedByteBuffer loadModelFile(Context context, String path) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(path);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel channel = inputStream.getChannel();
        return channel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.getStartOffset(), fileDescriptor.getDeclaredLength());
    }

    public void close() {
        if (interpreter != null) interpreter.close();
    }

    /** 检测结果类 */
    public static class DetectionResult {
        public final RectF boundingBox;
        public final float confidence;
        public final String className;

        public DetectionResult(RectF boundingBox, float confidence, String className) {
            this.boundingBox = boundingBox;
            this.confidence = confidence;
            this.className = className;
        }
    }
}
