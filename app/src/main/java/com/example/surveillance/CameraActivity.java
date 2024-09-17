package com.example.surveillance;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.JavaCamera2View;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.IOException;

public class CameraActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final String TAG = "CameraActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 00;
    private static final int SMS_PERMISSION_REQUEST = 300;
    private Mat mRgba;
    private Mat mGray;
    private CameraBridgeViewBase mOpenCvCameraView;
    private face_recognition face_recognition;

    private String phoneNumber = "";
    private boolean smsEnabled = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        smsEnabled = intent.getBooleanExtra("smsEnabled", false);
        phoneNumber = intent.getStringExtra("phoneNumber");
        Log.d(TAG, "onCreate called");
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera);

        mOpenCvCameraView = findViewById(R.id.frame_Surface);
        if (mOpenCvCameraView == null) {
            Log.e(TAG, "Failed to find view with id: frame_Surface");
            finish();
            return;
        }
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        try{
            int inputSize=112;
            face_recognition=new face_recognition(getAssets(),
                    CameraActivity.this,
                    "mobile_face_net.tflite",
                    inputSize,
                    phoneNumber,
                    smsEnabled);
        }catch (IOException e){
            e.printStackTrace();
            Log.d(TAG,"Failed to load model");
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.SEND_SMS}, SMS_PERMISSION_REQUEST);
        }
        if (ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {

            initializeOpenCV();
        } else {
            ActivityCompat.requestPermissions(CameraActivity.this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeOpenCV();

            } else {
                Log.e(TAG, "Camera permission denied");
                finish();
            }
        }
    }

    private void initializeOpenCV() {
        Log.d(TAG, "Initializing OpenCV");
        if (!OpenCVLoader.initDebug()) {
            Log.e(TAG, "OpenCV initialization failed");
            finish();
            return;
        }

        Log.i(TAG, "OpenCV initialized successfully");
        mOpenCvCameraView.enableView();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause called");
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume called");
        if (mOpenCvCameraView != null && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            mOpenCvCameraView.enableView();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy called");
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.i(TAG, "onCameraViewStarted called with width: " + width + ", height: " + height);
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
    }

    @Override
    public void onCameraViewStopped() {
        Log.i(TAG, "onCameraViewStopped called");
        if (mRgba != null) mRgba.release();
        if (mGray != null) mGray.release();
    }

    @Override
    public Mat onCameraFrame(JavaCamera2View.CvCameraViewFrame inputFrame) {
        Log.v(TAG, "onCameraFrame called");
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();
        mRgba=face_recognition.recognizeImage(mRgba);

        return mRgba;
    }
}
