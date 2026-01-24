package com.example.weeddetectionapp;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.lifecycle.LifecycleOwner;

import com.github.chrisbanes.photoview.PhotoView;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CaptureModule {
    private static final String TAG = "CaptureModule";
    private static final int MODEL_INPUT_SIZE = 640;

    private final Context context;
    private final View rootLayout;
    private final PreviewView previewView;
    private final OverlayView overlayView;
    private final PhotoView freezeFrameView;
    private final ImageButton btnTake;
    private final Button btnDetail; // 新增：跳转详情按钮
    private final ExecutorService cameraExecutor;
    private final ModelHandler modelHandler;

    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private Bitmap currentBitmap;
    private String currentDetectedName = null;

    public CaptureModule(Context context, View layout, ModelHandler modelHandler) {
        this.context = context;
        this.rootLayout = layout;
        this.modelHandler = modelHandler;

        this.previewView = layout.findViewById(R.id.capture_preview);
        this.overlayView = layout.findViewById(R.id.capture_overlay);
        this.freezeFrameView = layout.findViewById(R.id.freeze_frame_view);
        this.btnTake = layout.findViewById(R.id.btn_capture_take);
        // 注意：XML中拍照界面也需要加这个按钮 ID：btn_view_detail
        this.btnDetail = layout.findViewById(R.id.btn_view_detail);

        this.cameraExecutor = Executors.newSingleThreadExecutor();

        initLogic();
    }

    private void initLogic() {
        // 1. 缩放逻辑
        freezeFrameView.setZoomable(true);
        freezeFrameView.setOnMatrixChangeListener(rect -> {
            float scale = freezeFrameView.getScale();
            boolean isNormal = scale <= 1.05f;
            overlayView.setDetectionVisible(isNormal);
            // 缩放时隐藏按钮
            if (currentDetectedName != null) {
                btnDetail.setVisibility(isNormal ? View.VISIBLE : View.GONE);
            }
        });

        // 2. 返回逻辑
        rootLayout.findViewById(R.id.btn_capture_back).setOnClickListener(v -> stop());

        // 3. 拍照逻辑
        this.btnTake.setOnClickListener(v -> captureToGallery());

        // 4. 详情跳转逻辑 (同步相册模块的修复)
        if (btnDetail != null) {
            btnDetail.setOnClickListener(v -> {
                if (currentDetectedName != null) jumpToDetail(currentDetectedName);
            });
        }
    }

    private void jumpToDetail(String className) {
        new Thread(() -> {
            try {
                InputStream is = context.getAssets().open("weed_data.json");
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONArray array = new JSONArray(sb.toString());
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    if (obj.getString("chineseName").equals(className.trim())) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            Intent intent = new Intent(context, DetailActivity.class);
                            intent.putExtra("weed_data", obj.toString()); // 统一使用 weed_data
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(intent);
                        });
                        return;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Jump Error", e);
            }
        }).start();
    }

    public void start(@NonNull LifecycleOwner lifecycleOwner) {
        rootLayout.setVisibility(View.VISIBLE);
        rootLayout.bringToFront(); // 解决可能的黑屏/层级遮挡
        resetUI();

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(context);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
                btnTake.setEnabled(true);
            } catch (Exception e) {
                Log.e(TAG, "相机启动失败", e);
                Toast.makeText(context, "相机初始化失败，请检查权限", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void captureToGallery() {
        if (imageCapture == null) return;
        btnTake.setEnabled(false);
        Toast.makeText(context, "正在捕捉并识别...", Toast.LENGTH_SHORT).show();

        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "Weed_" + System.currentTimeMillis());
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WeedDetection");
        }

        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(
                context.getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv).build();

        imageCapture.takePicture(options, ContextCompat.getMainExecutor(context), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                Uri uri = results.getSavedUri();
                if (uri != null) processAndIdentify(uri);
            }
            @Override
            public void onError(@NonNull ImageCaptureException e) {
                btnTake.setEnabled(true);
            }
        });
    }

    private void processAndIdentify(Uri uri) {
        cameraExecutor.execute(() -> {
            try {
                Bitmap bitmap;
                try (InputStream is = context.getContentResolver().openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(is);
                }
                if (bitmap == null) return;

                currentBitmap = rotateImageIfRequired(uri, bitmap);

                new Handler(Looper.getMainLooper()).post(() -> {
                    freezeFrameView.setImageBitmap(currentBitmap);
                    freezeFrameView.setVisibility(View.VISIBLE);
                    previewView.setVisibility(View.GONE);
                    overlayView.setVisibility(View.VISIBLE);
                    btnTake.setVisibility(View.GONE); // 拍照后隐藏拍照按钮

                    if (cameraProvider != null) cameraProvider.unbindAll();

                    // 开启识别
                    runInferenceAndMap(currentBitmap);
                });
            } catch (Exception e) {
                Log.e(TAG, "处理失败", e);
            }
        });
    }

    private void runInferenceAndMap(Bitmap bitmap) {
        new Thread(() -> {
            Bitmap input = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true);
            List<ModelHandler.DetectionResult> results = modelHandler.runInference(input);
            input.recycle();

            new Handler(Looper.getMainLooper()).post(() -> {
                finalizeUI(results, bitmap);
            });
        }).start();
    }

    private void finalizeUI(List<ModelHandler.DetectionResult> results, Bitmap bitmap) {
        float vW = freezeFrameView.getWidth();
        float vH = freezeFrameView.getHeight();
        if (vW == 0 || vH == 0) return;

        float bW = bitmap.getWidth();
        float bH = bitmap.getHeight();
        float scale = Math.min(vW / bW, vH / bH);
        float actualImgW = bW * scale;
        float actualImgH = bH * scale;
        float xOff = (vW - actualImgW) / 2f;
        float yOff = (vH - actualImgH) / 2f;

        List<ModelHandler.DetectionResult> mapped = new ArrayList<>();
        for (ModelHandler.DetectionResult res : results) {
            RectF box = res.boundingBox;
            float left = (box.left / 640f) * actualImgW + xOff;
            float top = (box.top / 640f) * actualImgH + yOff;
            float right = (box.right / 640f) * actualImgW + xOff;
            float bottom = (box.bottom / 640f) * actualImgH + yOff;
            mapped.add(new ModelHandler.DetectionResult(new RectF(left, top, right, bottom), res.confidence, res.className));
        }

        overlayView.setResults(mapped);
        if (!mapped.isEmpty()) {
            currentDetectedName = mapped.get(0).className;
            btnDetail.setText("了解更多: " + currentDetectedName);
            btnDetail.setVisibility(View.VISIBLE);
            btnDetail.bringToFront();
        } else {
            currentDetectedName = null;
            btnDetail.setVisibility(View.GONE);
            Toast.makeText(context, "未识别到杂草", Toast.LENGTH_SHORT).show();
        }
    }

    // (rotateImageIfRequired 方法保持不变...)

    private void resetUI() {
        btnTake.setEnabled(false);
        btnTake.setVisibility(View.VISIBLE);
        btnDetail.setVisibility(View.GONE);
        freezeFrameView.setVisibility(View.GONE);
        previewView.setVisibility(View.VISIBLE);
        overlayView.setResults(null);
        freezeFrameView.setScale(1.0f);
        currentDetectedName = null;
    }



    /**
     * 校正图片旋转方向
     * 根据图片的 Exif 信息自动旋转 Bitmap，确保显示和识别坐标一致
     */
    private Bitmap rotateImageIfRequired(Uri uri, Bitmap img) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) return img;

            // 使用 androidx.exifinterface 库读取图片元数据
            androidx.exifinterface.media.ExifInterface ei = new androidx.exifinterface.media.ExifInterface(input);
            int orientation = ei.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);

            Matrix matrix = new Matrix();
            switch (orientation) {
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90:
                    matrix.postRotate(90);
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180:
                    matrix.postRotate(180);
                    break;
                case androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270:
                    matrix.postRotate(270);
                    break;
                default:
                    return img; // 无需旋转
            }

            // 根据矩阵生成旋转后的新 Bitmap
            Bitmap rotated = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);

            // 释放原图内存，防止 OOM
            if (rotated != img) {
                img.recycle();
            }
            return rotated;

        } catch (Exception e) {
            Log.e(TAG, "旋转校正失败: " + e.getMessage());
            return img;
        }
    }
    public void stop() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        rootLayout.setVisibility(View.GONE);
        resetUI();
    }


}