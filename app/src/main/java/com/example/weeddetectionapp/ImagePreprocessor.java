package com.example.weeddetectionapp;
import android.graphics.Bitmap;

public class ImagePreprocessor {

    // 图像预处理，调整为模型需要的输入大小
    public static float[][][][] preprocessImage(Bitmap bitmap) {
        // 将图像调整为适合YOLO模型的尺寸
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 640, 640, true);

        // 转换为输入张量（归一化并调整形状）
        int width = resizedBitmap.getWidth();
        int height = resizedBitmap.getHeight();

        float[][][][] input = new float[1][height][width][3];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = resizedBitmap.getPixel(x, y);
                input[0][y][x][0] = ((pixel >> 16) & 0xFF) / 255.0f; // R
                input[0][y][x][1] = ((pixel >> 8) & 0xFF) / 255.0f;  // G
                input[0][y][x][2] = (pixel & 0xFF) / 255.0f;          // B
            }
        }
        return input;
    }
}
