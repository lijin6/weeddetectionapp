package com.example.weeddetectionapp;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class EncyclopediaManager {

    private final Context context;
    private final LinearLayout container;

    public EncyclopediaManager(Context context, LinearLayout container) {
        this.context = context;
        this.container = container;
    }

    public void loadAndDisplay() {
        container.removeAllViews();
        try {
            // 1. 读取 Assets 中的 JSON
            InputStream is = context.getAssets().open("weed_data.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);

            // 2. 解析并渲染
            JSONArray array = new JSONArray(json);
            LayoutInflater inflater = LayoutInflater.from(context);

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);

                // 加载卡片布局
                View cardView = inflater.inflate(R.layout.layout_info, container, false);

                // 绑定控件
                TextView tvChinese = cardView.findViewById(R.id.tvChineseName);
                TextView tvLatin = cardView.findViewById(R.id.tvLatinName);
                TextView tvCommon = cardView.findViewById(R.id.tvCommonName);
                TextView tvFeature = cardView.findViewById(R.id.tvFeature);
                ImageView ivWeed = cardView.findViewById(R.id.ivWeedImage); // 对应布局中的新ImageView

                // 设置数据
                tvChinese.setText(obj.getString("chineseName"));
                tvLatin.setText(obj.getString("type"));
                tvCommon.setText("常见俗名：" + obj.getString("commonName"));
                tvFeature.setText("形态特征：" + obj.getString("feature"));

                // 动态设置图片：假设JSON中 imageName 字段对应 res/drawable 里的文件名
                String imageName = obj.optString("imageName", "default_weed");
                int resId = context.getResources().getIdentifier(imageName, "drawable", context.getPackageName());
                if (resId != 0) {
                    ivWeed.setImageResource(resId);
                } else {
                    // 如果没找到图片，可以设置一个默认占位图
                    ivWeed.setImageResource(R.drawable.ic_launcher_background);
                }

                // 3. 点击跳转逻辑
                cardView.setOnClickListener(v -> {
                    Intent intent = new Intent(context, DetailActivity.class);
                    // 将当前点击的杂草数据完整传递给详情页
                    intent.putExtra("weed_data", obj.toString());
                    context.startActivity(intent);
                });

                container.addView(cardView);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}