package com.example.weeddetectionapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.VideoView;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class VideoModule {
    private final Context context;
    private final View rootLayout;
    private final VideoView videoView;
    private final OverlayView overlayView;
    private final ModelHandler modelHandler;

    private Timer analysisTimer;
    private MediaMetadataRetriever retriever;

    public VideoModule(Context context, View layout, ModelHandler handler) {
        this.context = context;
        this.rootLayout = layout;
        this.modelHandler = handler;
        this.videoView = layout.findViewById(R.id.video_view);
        this.overlayView = layout.findViewById(R.id.video_overlay);
    }

    public void startAnalysis(Uri uri) {
        rootLayout.setVisibility(View.VISIBLE);

        videoView.setVideoURI(uri);
        videoView.setOnPreparedListener(mp -> {
            mp.setLooping(true);
            // 视频准备好后再开始计时器
            startTimer();
        });
        videoView.start();

        retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startTimer() {
        if (analysisTimer != null) analysisTimer.cancel();
        analysisTimer = new Timer();
        analysisTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (videoView.isPlaying()) {
                    processFrame();
                }
            }
        }, 0, 1000);
    }

    private void processFrame() {
        try {
            long timeUs = videoView.getCurrentPosition() * 1000L;
            Bitmap frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);

            if (frame != null && modelHandler != null) {
                // 1. 缩放至模型输入大小
                Bitmap inferenceBitmap = Bitmap.createScaledBitmap(frame, 640, 640, true);
                List<ModelHandler.DetectionResult> results = modelHandler.runInference(inferenceBitmap);

                // 2. 坐标映射（关键：将 640x640 映射到 VideoView 的实际像素）
                List<ModelHandler.DetectionResult> mappedResults = mapToVideoView(results, frame);

                new Handler(Looper.getMainLooper()).post(() -> {
                    overlayView.setResults(mappedResults);
                });

                inferenceBitmap.recycle();
                // 注意：由于 frame 是从 retriever 拿到的，不用手动 recycle 以防出错，
                // 但如果内存压力大，可以根据 API 版本处理。
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<ModelHandler.DetectionResult> mapToVideoView(List<ModelHandler.DetectionResult> results, Bitmap frame) {
        float vW = videoView.getWidth();
        float vH = videoView.getHeight();
        float fW = frame.getWidth();
        float fH = frame.getHeight();

        // 计算视频在 VideoView 中的缩放方式（通常是 fitCenter）
        float scale = Math.min(vW / fW, vH / fH);
        float actualW = fW * scale;
        float actualH = fH * scale;
        float xOff = (vW - actualW) / 2f;
        float yOff = (vH - actualH) / 2f;

        List<ModelHandler.DetectionResult> mappedList = new ArrayList<>();
        for (ModelHandler.DetectionResult res : results) {
            RectF box = res.boundingBox;
            float left = (box.left / 640f) * actualW + xOff;
            float top = (box.top / 640f) * actualH + yOff;
            float right = (box.right / 640f) * actualW + xOff;
            float bottom = (box.bottom / 640f) * actualH + yOff;
            mappedList.add(new ModelHandler.DetectionResult(new RectF(left, top, right, bottom), res.confidence, res.className));
        }
        return mappedList;
    }

    public void stop() {
        if (analysisTimer != null) {
            analysisTimer.cancel();
            analysisTimer = null;
        }
        if (videoView != null) {
            videoView.stopPlayback();
        }
        if (retriever != null) {
            try { retriever.release(); } catch (Exception ignored) {}
            retriever = null;
        }
        overlayView.setResults(null);
        rootLayout.setVisibility(View.GONE);
    }
}