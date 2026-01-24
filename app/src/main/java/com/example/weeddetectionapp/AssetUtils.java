package com.example.weeddetectionapp;



import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class AssetUtils {
    public static byte[] loadModelFile(Context context, String modelFileName) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(context.getAssets().open(modelFileName)));

        StringBuilder modelData = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            modelData.append(line);
        }
        return modelData.toString().getBytes();
    }
}
