package com.example.weeddetectionapp;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;

public class DetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        // 1. 获取传递过来的数据
        String jsonData = getIntent().getStringExtra("weed_data");

        if (jsonData != null) {
            try {
                JSONObject obj = new JSONObject(jsonData);

                // 2. 初始化控件
                ImageView ivDetail = findViewById(R.id.ivDetailImage);
                TextView tvTitle = findViewById(R.id.tvDetailTitle);
                TextView tvLatin = findViewById(R.id.tvDetailLatin);
                TextView tvContent = findViewById(R.id.tvDetailContent);

                // 3. 填充数据
                tvTitle.setText(obj.getString("chineseName"));
                tvLatin.setText(obj.getString("type"));

                // 这里可以组合更多详细字段
                String detailInfo = "【俗名】\n" + obj.getString("commonName") + "\n\n" +
                        "【特征描述】\n" + obj.getString("feature");
                tvContent.setText(detailInfo);

                // 4. 设置图片
                String imageName = obj.optString("imageName", "default_weed");
                int resId = getResources().getIdentifier(imageName, "drawable", getPackageName());
                if (resId != 0) {
                    ivDetail.setImageResource(resId);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 设置简单的返回按钮逻辑（如果你的布局里有返回键）
        if (findViewById(R.id.btnBack) != null) {
            findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        }
    }
}