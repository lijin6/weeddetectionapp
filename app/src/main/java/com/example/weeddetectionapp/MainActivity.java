package com.example.weeddetectionapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.graphics.Color;
import android.widget.LinearLayout;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // 页面视图
    private View layoutHome, layoutCamera, layoutImage, layoutVideo, layoutCapture;
    private View viewInfoRoot, viewSettingsRoot;
    private BottomNavigationView bottomNav;

    // 模型与模块
    private ModelHandler modelHandler;
    private CameraModule cameraModule;
    private StaticImageModule staticImageModule;
    private VideoModule videoModule;
    private CaptureModule captureModule;

    // 权限相关
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private enum PendingAction { NONE, CAMERA, CAPTURE }
    private PendingAction pendingAction = PendingAction.NONE;

    // 相册选择器（已改为现代 API）
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    switchToView(layoutImage);
                    layoutImage.postDelayed(() -> {
                        if (staticImageModule != null) staticImageModule.detectAndShow(uri);
                    }, 300);
                }
            });

    // 视频选择器
    private final ActivityResultLauncher<String> videoLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    switchToView(layoutVideo);
                    layoutVideo.postDelayed(() -> {
                        if (videoModule != null) videoModule.startAnalysis(uri);
                    }, 500);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. 设置沉浸式状态栏（使用非过时 API）
        setupWindowDisplay();

        setContentView(R.layout.activity_main);

        initViews();

        // 异步加载模型防止阻塞主线程
        new Thread(() -> {
            try {
                // 请确保 assets 中存在该文件
                modelHandler = new ModelHandler(this, "HSGNet.tflite");
                new Handler(Looper.getMainLooper()).post(() -> {
                    initModules();
                    setupMenuActions();
                    setupManagers();
                    setupBackButtons();
                });
            } catch (Exception e) {
                Log.e(TAG, "模型加载失败", e);
            }
        }).start();

        setupNavigation();
        switchToView(layoutHome);
    }

    private void initViews() {
        layoutHome = findViewById(R.id.layout_home);
        layoutCamera = findViewById(R.id.layout_detect_camera);
        layoutImage = findViewById(R.id.layout_detect_image);
        layoutVideo = findViewById(R.id.layout_detect_video);
        layoutCapture = findViewById(R.id.layout_capture);
        viewInfoRoot = findViewById(R.id.view_info_root);
        viewSettingsRoot = findViewById(R.id.view_settings_root);
        bottomNav = findViewById(R.id.bottom_navigation);
    }

    private void initModules() {
        if (modelHandler == null) return;
        cameraModule = new CameraModule(this, layoutCamera, modelHandler);
        staticImageModule = new StaticImageModule(this, layoutImage, modelHandler);
        videoModule = new VideoModule(this, layoutVideo, modelHandler);
        captureModule = new CaptureModule(this, layoutCapture, modelHandler);
    }

    private void setupMenuActions() {
        // 实时相机卡片
        findViewById(R.id.card_main_camera).setOnClickListener(v -> {
            handleCameraAction(PendingAction.CAMERA, layoutCamera, cameraModule);
        });

        // 视频识别卡片
        findViewById(R.id.card_main_video).setOnClickListener(v -> videoLauncher.launch("video/*"));

        // 相册识别卡片
        findViewById(R.id.card_main_gallery).setOnClickListener(v -> galleryLauncher.launch("image/*"));

        // 单张拍照卡片
        findViewById(R.id.card_main_capture).setOnClickListener(v -> {
            handleCameraAction(PendingAction.CAPTURE, layoutCapture, captureModule);
        });
    }

    private void handleCameraAction(PendingAction action, View layout, Object module) {
        if (checkCameraPermission()) {
            switchToView(layout);
            layout.post(() -> {
                if (action == PendingAction.CAMERA) cameraModule.start(this);
                else captureModule.start(this);
            });
        } else {
            pendingAction = action;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    private void setupBackButtons() {
        View.OnClickListener onBackClick = v -> handleBackAction();
        int[] ids = {R.id.btn_camera_back, R.id.btn_image_back, R.id.btn_video_back, R.id.btn_capture_back};
        for (int id : ids) {
            View b = findViewById(id);
            if (b != null) b.setOnClickListener(onBackClick);
        }
    }

    private void setupNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) switchToView(layoutHome);
            else if (id == R.id.nav_detect) switchToView(viewInfoRoot);
            else if (id == R.id.nav_more) switchToView(viewSettingsRoot);
            return true;
        });
    }

    private void setupManagers() {
        // 百科管理器加载
        LinearLayout infoContainer = findViewById(R.id.info_list_container);
        if (infoContainer != null) {
            new EncyclopediaManager(this, infoContainer).loadAndDisplay();
        }

        // 设置管理器加载
        LinearLayout settingsContainer = findViewById(R.id.settings_container);
        if (settingsContainer != null) {
            new SettingsManager(this, settingsContainer).loadSettingsUI();
        }
    }

    private void switchToView(View target) {
        // 切换时停止所有活动模块，释放资源
        if (cameraModule != null) cameraModule.stop();
        if (videoModule != null) videoModule.stop();
        if (captureModule != null) captureModule.stop();

        View[] views = {layoutHome, layoutCamera, layoutImage, layoutVideo, layoutCapture, viewInfoRoot, viewSettingsRoot};
        for (View v : views) if (v != null) v.setVisibility(View.GONE);

        if (target != null) {
            target.setVisibility(View.VISIBLE);
        }
    }

    private void handleBackAction() {
        if (layoutHome.getVisibility() != View.VISIBLE) {
            switchToView(layoutHome);
            bottomNav.setSelectedItemId(R.id.nav_home);
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBackAction();
    }

    private void setupWindowDisplay() {
        // 使用现代 API 替代过时的 setSystemUiVisibility
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // 确保状态栏图标在浅色背景下是可见的（深色图标）
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    // ----------------- 权限处理 -----------------
    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限授予后执行之前待定的动作
                if (pendingAction == PendingAction.CAMERA) {
                    switchToView(layoutCamera);
                    layoutCamera.post(() -> cameraModule.start(this));
                } else if (pendingAction == PendingAction.CAPTURE) {
                    switchToView(layoutCapture);
                    layoutCapture.post(() -> captureModule.start(this));
                }
            } else {
                Toast.makeText(this, "需要摄像头权限才能进行识别", Toast.LENGTH_SHORT).show();
            }
            pendingAction = PendingAction.NONE;
        }
    }
}