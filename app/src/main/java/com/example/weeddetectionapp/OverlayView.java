package com.example.weeddetectionapp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OverlayView: 绘制推理结果框和类别文字
 * 同步支持缩放时的显示逻辑
 */
public class OverlayView extends View {

    private List<ModelHandler.DetectionResult> results;
    private boolean isVisible = true; // 新增：用于控制缩放时是否显示

    private final Paint boxPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint backgroundPaint = new Paint(); // 新增：文字背景

    private final Map<String, Integer> classColors = new HashMap<>();

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(6f);
        boxPaint.setAntiAlias(true);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36f);
        textPaint.setFakeBoldText(true);
        textPaint.setAntiAlias(true);

        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setAlpha(160); // 半透明背景

        // 类别颜色表
        classColors.put("蒿属", Color.parseColor("#FF5722"));
        classColors.put("冰草属", Color.parseColor("#4CAF50"));
        classColors.put("藜属", Color.parseColor("#03A9F4"));
        classColors.put("地肤属", Color.parseColor("#9C27B0"));
        classColors.put("蓟属", Color.parseColor("#FF9800"));
        classColors.put("猪毛菜属", Color.parseColor("#8BC34A"));
        classColors.put("萝卜属", Color.parseColor("#00BCD4"));
        classColors.put("野决明属", Color.parseColor("#E91E63"));
        classColors.put("未知杂草", Color.RED);
    }

    /**
     * 设置识别结果
     */
    public void setResults(List<ModelHandler.DetectionResult> results) {
        this.results = results;
        invalidate(); // 刷新绘制
    }

    /**
     * 新增：根据缩放状态设置可见性
     * @param visible 当 PhotoView 缩放倍数 > 1 时传入 false
     */
    public void setDetectionVisible(boolean visible) {
        this.isVisible = visible;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // 如果手动隐藏或结果为空，则不绘制
        if (!isVisible || results == null || results.isEmpty()) return;

        for (ModelHandler.DetectionResult res : results) {
            String className = res.className != null ? res.className : "未知杂草";

            int color = classColors.getOrDefault(className, Color.RED);
            boxPaint.setColor(color);
            backgroundPaint.setColor(color);
            backgroundPaint.setAlpha(160);

            // 1. 绘制矩形框
            canvas.drawRect(res.boundingBox, boxPaint);

            // 2. 计算文字位置
            float textWidth = textPaint.measureText(className);
            float textX = res.boundingBox.left;
            float textY = res.boundingBox.top - 10;

            // 避免文字超出屏幕顶部
            if (textY < 40) {
                textY = res.boundingBox.top + 40;
            }

            // 3. 绘制文字背景小标签（增强可读性）
            canvas.drawRect(textX, textY - 35, textX + textWidth + 10, textY + 10, backgroundPaint);

            // 4. 绘制类别文字
            canvas.drawText(className, textX + 5, textY, textPaint);
        }
    }
}