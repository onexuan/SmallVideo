package com.hzl.smallvideo.activity;

import android.Manifest;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.VideoView;

import com.hzl.smallvideo.R;
import com.hzl.smallvideo.application.MainApplication;
import com.hzl.smallvideo.listener.CameraPictureListener;
import com.hzl.smallvideo.listener.RecordFinishListener;
import com.hzl.smallvideo.listener.WatermarkCallbackListener;
import com.hzl.smallvideo.manager.RecordManager;
import com.hzl.smallvideo.manager.camera.CameraSurfaceView;
import com.hzl.smallvideo.manager.camera.CaptureButton;
import com.hzl.smallvideo.util.AppUtil;
import com.hzl.smallvideo.util.CommonUtil;
import com.hzl.smallvideo.util.FFmpegUtil;
import com.hzl.smallvideo.util.PermissionsUtils;
import com.hzl.smallvideo.view.WatermarkView;

import java.io.File;
import java.io.IOException;

public class MainActivity extends Activity implements View.OnClickListener {

    private final int REQUEST_CODE_PERMISSIONS = 10;

    private CameraSurfaceView mSurfaceView;
    private CaptureButton mBtnStart;
    private WatermarkView mWatermark;
    private VideoView mVideoView;
    private ImageView mBtnCamera;
    private ImageView mBtnLight;
    private ImageView ivImage;

    //音视频的处理的类
    private RecordManager mRecordManager;

    //是否开启闪关灯
    private boolean isLighting;
    //拍照的图片
    private Bitmap bitmap;
    //合成视频的路径
    private String filePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MainApplication.setCurrentActivity(this);
        //设置底部虚拟状态栏为透明，并且可以充满，4.4以上才有
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }
        //权限申请使用
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            final String[] PERMISSIONS;
            PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
            PermissionsUtils.checkAndRequestMorePermissions(this, PERMISSIONS, REQUEST_CODE_PERMISSIONS,
                    new PermissionsUtils.PermissionRequestSuccessCallBack() {
                        @Override
                        public void onHasPermission() {
                            setContentView(R.layout.activity_main);
                            initView();
                        }
                    });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (PermissionsUtils.isPermissionRequestSuccess(grantResults)) {
            setContentView(R.layout.activity_main);
            initView();
        }
    }

    private void initView() {
        mSurfaceView = (CameraSurfaceView) findViewById(R.id.camera_surface);
        mBtnStart = (CaptureButton) findViewById(R.id.btn_start);
        mVideoView = (VideoView) findViewById(R.id.video_view);
        mBtnCamera = (ImageView) findViewById(R.id.btn_camera);
        mBtnLight = (ImageView) findViewById(R.id.btn_light);
        ivImage = (ImageView) findViewById(R.id.iv_image);
        mWatermark = (WatermarkView) findViewById(R.id.watermark);

        mBtnCamera.setOnClickListener(this);
        mBtnLight.setOnClickListener(this);

        mRecordManager = new RecordManager(mSurfaceView);

        mBtnStart.setCaptureListener(new CaptureButton.CaptureListener() {
            @Override
            public void capture() { //进行拍照
                //进行拍照
                mRecordManager.takePicture(new CameraPictureListener() {

                    @Override
                    public void onPictureBitmap(Bitmap btmp) {
                        //完成拍照了，通知控件进行按钮控件的更新
                        mBtnStart.showControllerButtons();
                        //这里得到正确旋转和翻转之后的图片
                        MainActivity.this.bitmap = btmp;
                        ivImage.setImageBitmap(btmp);
                        ivImage.setVisibility(View.VISIBLE);
                        //显示到界面上去，然后刷新camera
                        mRecordManager.onStop();
                        mRecordManager.onResume();
                        //隐藏顶部的按钮
                        mBtnCamera.setVisibility(View.GONE);
                        mBtnLight.setVisibility(View.GONE);
                    }
                });
            }

            @Override
            public void cancel() { //拍照取消
                MainActivity.this.bitmap = null;
                ivImage.setVisibility(View.GONE);
                //显示顶部的按钮
                mBtnCamera.setVisibility(View.VISIBLE);
                mBtnLight.setVisibility(View.VISIBLE);
            }

            @Override
            public void determine() { //拍照确定
                final String filePath = Environment.getExternalStorageDirectory().getPath() + File.separator + System.currentTimeMillis() + ".png";
                AppUtil.saveBitmapToFile(MainActivity.this.bitmap, filePath);
                CommonUtil.showToast("图片保存成功");
                ivImage.setVisibility(View.GONE);
                //显示顶部的按钮
                mBtnCamera.setVisibility(View.VISIBLE);
                mBtnLight.setVisibility(View.VISIBLE);
            }

            @Override
            public void record() { //开始录制
                //开始录制
                mRecordManager.startRecord();
            }

            @Override
            public void rencodEnd(final boolean isShortTime) { //停止录制
                //录制结束
                mRecordManager.stopRecord(new RecordFinishListener() {
                    @Override
                    public void onRecordFinish(String filePath) {
                        if (isShortTime) {
                            //删除录制的小视频
                            new File(filePath).delete();
                            CommonUtil.showToast("录制时间太短");
                        } else {
                            //得到合成的mp4的文件路径
                            MainActivity.this.filePath = filePath;
                            //完成录制了，通知控件进行按钮控件的更新
                            mBtnStart.showControllerButtons();
                            //进行小视频的循环播放
                            startVideo(filePath);
                            //隐藏顶部的按钮
                            mBtnCamera.setVisibility(View.GONE);
                            mBtnLight.setVisibility(View.GONE);
                        }
                    }
                });
            }

            @Override
            public void getRecordResult() { //需要录制
                //addDefaultWatermark();
                CommonUtil.disMissDialog();
                CommonUtil.showToast("视频保存成功");
                //取消视频的播放
                mVideoView.stopPlayback();
                mVideoView.setVisibility(View.GONE);
                //显示顶部的按钮
                mBtnCamera.setVisibility(View.VISIBLE);
                mBtnLight.setVisibility(View.VISIBLE);
            }

            @Override
            public void deleteRecordResult() {//删除录制
                new File(filePath).delete();
                mVideoView.stopPlayback();
                mVideoView.setVisibility(View.GONE);
                //显示顶部的按钮
                mBtnCamera.setVisibility(View.VISIBLE);
                mBtnLight.setVisibility(View.VISIBLE);
            }

            @Override
            public void actionRecord() { //对视频和图片进行操作，主要是添加水印
                //TODO 自定义水印的添加
                //mWatermark.setVisibility(View.VISIBLE);
                CommonUtil.showToast("该功能暂未开放");
            }
        });

        mWatermark.setWatermarkCallbackListener(new WatermarkCallbackListener() {
            @Override
            public void onResult(Bitmap bitmap) {
                mWatermark.setVisibility(View.GONE);
            }

            @Override
            public void onCancel() {
                mWatermark.setVisibility(View.GONE);
            }
        });
    }

    public void addDefaultWatermark() {
        //获取默认的水印信息并且保存为本地的png图片
        final String waterPath = Environment.getExternalStorageDirectory().getPath() + File.separator + "water.png";
        Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher_round);
        AppUtil.saveBitmapToFile(bitmap, waterPath);
        //进行水印的添加
        CommonUtil.showDialog("正在保存视频");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String outH264Path = Environment.getExternalStorageDirectory().getPath() + File.separator + "out.h264";
                final String outMp4Path = Environment.getExternalStorageDirectory().getPath() + File.separator + System.currentTimeMillis() + ".mp4";
                //创建文件
                try {
                    new File(outH264Path).createNewFile();
                    new File(outMp4Path).createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //直接使用命令行的方式来添加水印
                final String filters = String.format("movie=%s[wm];[in][wm]overlay=(main_w-overlay_w)/2:(main_h-overlay_h)/2[out]", waterPath);
                //进行水印的添加
                FFmpegUtil.addWatermark(filters, outH264Path, outMp4Path);

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        //水印添加结束
                        CommonUtil.disMissDialog();
                        CommonUtil.showToast("视频保存成功");
                        //删除没有水印的视频和水印图片
                        new File(waterPath).delete();
                        new File(MainActivity.this.filePath).delete();
                        //取消视频的播放
                        mVideoView.stopPlayback();
                        mVideoView.setVisibility(View.GONE);
                        //显示顶部的按钮
                        mBtnCamera.setVisibility(View.VISIBLE);
                        mBtnLight.setVisibility(View.VISIBLE);
                    }
                });
            }
        }).start();
    }

    public void startVideo(String videoPath) {
        if (mVideoView.isPlaying()) {
            mVideoView.stopPlayback();
        }
        mVideoView.setVisibility(View.VISIBLE);
        mVideoView.setZOrderMediaOverlay(true);
        mVideoView.setVideoPath("file://" + videoPath);
        mVideoView.start();
        mVideoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(true);
                mp.start();
            }
        });
        mVideoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                //不做任何处理
                return true;
            }
        });
    }

    @Override
    public void onClick(View v) {
        if (v == mBtnCamera) { //摄像头旋转
            int cameraType = mRecordManager.changeCamera();
            //如果是前置摄像头就不需要闪光灯，如果是后置的才需要闪关灯
            if (cameraType == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                mBtnLight.setVisibility(View.GONE);
            } else {
                mBtnLight.setVisibility(View.VISIBLE);
            }
            mBtnLight.setImageResource(R.mipmap.light_close);
            isLighting = false;
            mRecordManager.setLightingState(false);
        } else if (v == mBtnLight) {
            if (isLighting) {
                mBtnLight.setImageResource(R.mipmap.light_close);
            } else {
                mBtnLight.setImageResource(R.mipmap.light_open);
            }
            isLighting = !isLighting;
            mRecordManager.setLightingState(isLighting);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mRecordManager.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mRecordManager.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mRecordManager.onDestroy();
    }
}
