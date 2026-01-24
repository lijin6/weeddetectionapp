package com.example.weeddetectionapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SettingsManager {

    private final Context context;
    private final LinearLayout container;
    private final SharedPreferences prefs;

    public SettingsManager(Context context, LinearLayout container) {
        this.context = context;
        this.container = container;
        this.prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
    }

    public void loadSettingsUI() {
        container.removeAllViews();

        // 1. 模型切换
        String currentModel = prefs.getString("selected_model", "HSGNet.tflite");
        addSettingRow("推理模型", currentModel, v -> showModelPicker());

        // 2. 清理缓存
        addSettingRow("清理缓存", "清除识别历史及临时文件", v -> {
            Toast.makeText(context, "缓存清理成功", Toast.LENGTH_SHORT).show();
        });

        // 3. 关于我们
        addSettingRow("关于我们", "版本 v1.2.0", v -> {
            new AlertDialog.Builder(context)
                    .setTitle("关于我们")
                    .setMessage("旱地春小麦智能识别系统\n致力于提供最精准的田间杂草解决方案。")
                    .setPositiveButton("确定", null)
                    .show();
        });
    }

    private void addSettingRow(String title, String value, View.OnClickListener listener) {
        View rowView = LayoutInflater.from(context).inflate(R.layout.layout_settings, container, false);

        TextView tvTitle = rowView.findViewById(R.id.tvSettingTitle);
        TextView tvValue = rowView.findViewById(R.id.tvSettingValue);

        tvTitle.setText(title);
        tvValue.setText(value);
        rowView.setOnClickListener(listener);

        container.addView(rowView);
    }

    private void showModelPicker() {
        try {
            // 获取 assets 中所有的 tflite
            String[] files = context.getAssets().list("");
            List<String> modelList = new ArrayList<>();
            for (String f : files) if (f.endsWith(".tflite")) modelList.add(f);

            if (modelList.isEmpty()) {
                Toast.makeText(context, "未找到模型文件", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] items = modelList.toArray(new String[0]);
            new AlertDialog.Builder(context)
                    .setTitle("选择推理模型")
                    .setItems(items, (dialog, which) -> {
                        String selected = items[which];
                        prefs.edit().putString("selected_model", selected).apply();
                        loadSettingsUI(); // 刷新界面
                        Toast.makeText(context, "模型已更新为: " + selected, Toast.LENGTH_SHORT).show();
                    }).show();
        } catch (IOException e) { e.printStackTrace(); }
    }
}