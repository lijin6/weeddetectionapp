package com.example.weeddetectionapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.github.chrisbanes.photoview.PhotoView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class StaticImageModule {
    private static final String TAG = "StaticModule";
    private final Context context;
    private final View rootLayout;
    private final PhotoView imageView;
    private final OverlayView overlayView;
    private final Button btnDetail;
    private final ModelHandler modelHandler;

    private String currentDetectedName = null;

    public StaticImageModule(Context context, View layout, ModelHandler handler) {
        this.context = context;
        this.rootLayout = layout;
        this.modelHandler = handler;
        this.imageView = layout.findViewById(R.id.static_image_view);
        this.overlayView = layout.findViewById(R.id.static_overlay);
        this.btnDetail = layout.findViewById(R.id.btn_view_detail);

        initModuleLogic();
    }

    private void initModuleLogic() {
        // 1. PhotoView 缩放监听
        imageView.setZoomable(true);
        imageView.setOnMatrixChangeListener(rect -> {
            float scale = imageView.getScale();
            boolean isNormal = scale <= 1.05f;
            overlayView.setDetectionVisible(isNormal);
            // 只有原大小时显示详情按钮
            if (currentDetectedName != null) {
                btnDetail.setVisibility(isNormal ? View.VISIBLE : View.GONE);
            }
        });

        // 2. 详情按钮点击逻辑
        if (btnDetail != null) {
            btnDetail.setOnClickListener(v -> {
                Log.d(TAG, "==== 按钮被点击，当前名称: " + currentDetectedName + " ====");
                if (currentDetectedName != null) {
                    jumpToDetail(currentDetectedName);
                } else {
                    Toast.makeText(context, "未识别到植物，请重试", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void jumpToDetail(String className) {
        // 使用子线程防止读取 Assets 导致 UI 卡顿
        new Thread(() -> {
            try {
                // 1. 读取 JSON
                String jsonStr = loadJSONFromAsset("weed_data.json");
                if (jsonStr == null || jsonStr.isEmpty()) {
                    Log.e(TAG, "❌ JSON 文件为空或加载失败！");
                    return;
                }

                JSONArray array = new JSONArray(jsonStr);
                JSONObject targetObj = null;

                // 2. 匹配逻辑 (增加 trim 防止空格干扰)
                String target = className.trim();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String chineseName = obj.optString("chineseName", "").trim();

                    Log.d(TAG, "🔍 比对中: [" + chineseName + "] 对比 [" + target + "]");

                    if (chineseName.equals(target)) {
                        targetObj = obj;
                        break;
                    }
                }

                // 3. 执行跳转
                if (targetObj != null) {
                    String finalData = targetObj.toString();
                    Log.i(TAG, "✅ 匹配成功，数据准备就绪，开始跳转...");

                    new Handler(Looper.getMainLooper()).post(() -> {
                        Intent intent = new Intent(context, DetailActivity.class);
                        intent.putExtra("weed_data", finalData);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    });
                } else {
                    Log.e(TAG, "❌ 匹配失败：JSON 中不包含 " + target);
                    new Handler(Looper.getMainLooper()).post(() ->
                            Toast.makeText(context, "百科暂无“" + target + "”的数据", Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                Log.e(TAG, "🔥 跳转逻辑崩溃: ", e);
            }
        }).start();
    }

    /**
     * 稳健的 Assets 读取方法
     */
    private String loadJSONFromAsset(String fileName) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            InputStream is = context.getAssets().open(fileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            reader.close();
            return stringBuilder.toString();
        } catch (Exception e) {
            Log.e(TAG, "Assets 读取异常: " + e.getMessage());
            return null;
        }
    }

    // --- 识别与 UI 刷新逻辑 ---

    public void detectAndShow(Uri imageUri) {
        if (imageUri == null) return;
        rootLayout.setVisibility(View.VISIBLE);
        rootLayout.bringToFront(); // 解决图层遮挡
        resetState();

        new Thread(() -> {
            try (InputStream is = context.getContentResolver().openInputStream(imageUri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                if (bitmap != null) {
                    Bitmap corrected = rotateImageIfRequired(bitmap, imageUri);
                    new Handler(Looper.getMainLooper()).post(() -> {
                        imageView.setImageBitmap(corrected);
                        runInference(corrected);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "图片 Uri 加载失败", e);
            }
        }).start();
    }

    private void runInference(Bitmap bitmap) {
        new Thread(() -> {
            Bitmap input = Bitmap.createScaledBitmap(bitmap, 640, 640, true);
            List<ModelHandler.DetectionResult> results = modelHandler.runInference(input);
            input.recycle();

            new Handler(Looper.getMainLooper()).post(() -> finalizeUI(results, bitmap));
        }).start();
    }

    private void finalizeUI(List<ModelHandler.DetectionResult> results, Bitmap bitmap) {
        List<ModelHandler.DetectionResult> mapped = mapResults(results, bitmap);
        overlayView.setResults(mapped);

        if (mapped != null && !mapped.isEmpty()) {
            currentDetectedName = mapped.get(0).className;
            Log.d(TAG, "识别完成: " + currentDetectedName);
            btnDetail.setText("了解更多: " + currentDetectedName);
            btnDetail.setVisibility(View.VISIBLE);
            btnDetail.bringToFront();
        } else {
            currentDetectedName = null;
            btnDetail.setVisibility(View.GONE);
        }
    }

    private List<ModelHandler.DetectionResult> mapResults(List<ModelHandler.DetectionResult> results, Bitmap bitmap) {
        if (results == null || results.isEmpty() || imageView.getWidth() == 0) return null;
        float vW = imageView.getWidth();
        float vH = imageView.getHeight();
        float bW = bitmap.getWidth();
        float bH = bitmap.getHeight();
        float scale = Math.min(vW / bW, vH / bH);
        float actualImgW = bW * scale;
        float actualImgH = bH * scale;
        float xOff = (vW - actualImgW) / 2f;
        float yOff = (vH - actualImgH) / 2f;

        List<ModelHandler.DetectionResult> mappedList = new ArrayList<>();
        for (ModelHandler.DetectionResult res : results) {
            RectF box = res.boundingBox;
            float left = (box.left / 640f) * actualImgW + xOff;
            float top = (box.top / 640f) * actualImgH + yOff;
            float right = (box.right / 640f) * actualImgW + xOff;
            float bottom = (box.bottom / 640f) * actualImgH + yOff;
            mappedList.add(new ModelHandler.DetectionResult(new RectF(left, top, right, bottom), res.confidence, res.className));
        }
        return mappedList;
    }

    private Bitmap rotateImageIfRequired(Bitmap img, Uri uri) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            ExifInterface ei = new ExifInterface(input);
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            Matrix matrix = new Matrix();
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90: matrix.postRotate(90); break;
                case ExifInterface.ORIENTATION_ROTATE_180: matrix.postRotate(180); break;
                case ExifInterface.ORIENTATION_ROTATE_270: matrix.postRotate(270); break;
                default: return img;
            }
            Bitmap rotated = Bitmap.createBitmap(img, 0, 0, img.getWidth(), img.getHeight(), matrix, true);
            if (rotated != img) img.recycle();
            return rotated;
        } catch (Exception e) { return img; }
    }

    private void resetState() {
        imageView.setScale(1.0f);
        overlayView.setResults(null);
        btnDetail.setVisibility(View.GONE);
        currentDetectedName = null;
    }

    public void clear() {
        imageView.setImageDrawable(null);
        overlayView.setResults(null);
        rootLayout.setVisibility(View.GONE);
    }
}