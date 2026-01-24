package com.example.weeddetectionapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraModule {
    private static final String TAG = "CameraModule";
    private static final int MODEL_INPUT_SIZE = 640;
    private static final long ANALYSIS_INTERVAL_MS = 100; // 提升到10fps左右，更顺滑

    private final Context context;
    private final View rootLayout;
    private final PreviewView previewView;
    private final OverlayView overlayView;
    private final TextView tvStatus;
    private final ModelHandler modelHandler;

    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private long lastAnalysisTime = 0L;

    public CameraModule(Context context, View layout, ModelHandler handler) {
        this.context = context;
        this.rootLayout = layout;
        this.modelHandler = handler;
        this.previewView = layout.findViewById(R.id.camera_preview);
        this.overlayView = layout.findViewById(R.id.camera_overlay);
        this.tvStatus = layout.findViewById(R.id.tv_camera_status);

        // 关键设置：兼容模式+FIT_CENTER，确保预览比例与分析比例对齐
        this.previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        this.previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);
    }

    public void start(@NonNull LifecycleOwner lifecycleOwner) {
        if (cameraExecutor == null || cameraExecutor.isShutdown()) {
            cameraExecutor = Executors.newSingleThreadExecutor();
        }

        rootLayout.setVisibility(View.VISIBLE);
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // 优化1：显式设置目标分辨率，强制 4:3 或类似比例，减少映射误差
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, image -> {
                    long now = System.currentTimeMillis();
                    if (now - lastAnalysisTime >= ANALYSIS_INTERVAL_MS) {
                        lastAnalysisTime = now;
                        processImage(image);
                    } else {
                        image.close();
                    }
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            } catch (Exception e) {
                Log.e(TAG, "相机启动失败", e);
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void processImage(ImageProxy image) {
        try {
            // 优化2：利用 ImageProxy 的旋转信息
            int rotationDegrees = image.getImageInfo().getRotationDegrees();
            Bitmap bitmap = ImageUtils.toBitmap(image); // 假设这是你工具类里的转换

            if (bitmap != null) {
                // 校正旋转
                Bitmap rotatedBitmap = rotateBitmap(bitmap, rotationDegrees);

                // 模型推理
                Bitmap inferenceBitmap = Bitmap.createScaledBitmap(rotatedBitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true);
                List<ModelHandler.DetectionResult> results = modelHandler.runInference(inferenceBitmap);

                // 坐标映射
                List<ModelHandler.DetectionResult> mappedResults = mapToScreen(results, rotatedBitmap);

                // UI更新
                new Handler(Looper.getMainLooper()).post(() -> {
                    overlayView.setResults(mappedResults);
                    if (tvStatus != null && !mappedResults.isEmpty()) {
                        tvStatus.setText("识别目标: " + mappedResults.get(0).className + " (" + mappedResults.size() + ")");
                    }
                });

                // 及时回收，防止实时流内存溢出
                inferenceBitmap.recycle();
                if (rotatedBitmap != bitmap) rotatedBitmap.recycle();
                bitmap.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "推理过程异常", e);
        } finally {
            image.close();
        }
    }

    private List<ModelHandler.DetectionResult> mapToScreen(List<ModelHandler.DetectionResult> results, Bitmap rotatedBitmap) {
        List<ModelHandler.DetectionResult> mappedList = new ArrayList<>();
        float viewW = previewView.getWidth();
        float viewH = previewView.getHeight();

        if (results == null || results.isEmpty() || viewW == 0) return mappedList;

        // 优化3：精准计算 FitCenter 的偏移和缩放
        float imgW = rotatedBitmap.getWidth();
        float imgH = rotatedBitmap.getHeight();

        float scale = Math.min(viewW / imgW, viewH / imgH);
        float actualDisplayedW = imgW * scale;
        float actualDisplayedH = imgH * scale;

        float xOffset = (viewW - actualDisplayedW) / 2f;
        float yOffset = (viewH - actualDisplayedH) / 2f;

        for (ModelHandler.DetectionResult res : results) {
            RectF box = res.boundingBox;

            // 核心修复：强制转 float 运算，并精准对应 FitCenter 渲染区域
            float left = (box.left / (float)MODEL_INPUT_SIZE) * actualDisplayedW + xOffset;
            float top = (box.top / (float)MODEL_INPUT_SIZE) * actualDisplayedH + yOffset;
            float right = (box.right / (float)MODEL_INPUT_SIZE) * actualDisplayedW + xOffset;
            float bottom = (box.bottom / (float)MODEL_INPUT_SIZE) * actualDisplayedH + yOffset;

            mappedList.add(new ModelHandler.DetectionResult(
                    new RectF(left, top, right, bottom),
                    res.confidence,
                    res.className
            ));
        }
        return mappedList;
    }

    private Bitmap rotateBitmap(Bitmap source, int angle) {
        if (angle == 0) return source;
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        Bitmap rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        if (rotated != source) source.recycle();
        return rotated;
    }

    public void stop() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (cameraExecutor != null) {
            cameraExecutor.shutdownNow();
            cameraExecutor = null;
        }
        rootLayout.post(() -> {
            overlayView.setResults(null);
            rootLayout.setVisibility(View.GONE);
        });
    }
}