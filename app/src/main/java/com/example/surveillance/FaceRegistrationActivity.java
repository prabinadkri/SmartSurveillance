package com.example.surveillance;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

public class FaceRegistrationActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int CAMERA_PERMISSION_CODE = 100;

    private EditText nameInput;
    private ImageView facePreview;
    private Button captureButton;
    private Button registerButton;
    private Bitmap capturedFace;
    private face_recognition faceRecognition;
    private CascadeClassifier cascadeClassifier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_registration);

        nameInput = findViewById(R.id.name_input);
        facePreview = findViewById(R.id.face_preview);
        captureButton = findViewById(R.id.capture_button);
        registerButton = findViewById(R.id.register_button);

        try{
            faceRecognition = new face_recognition(getAssets(),
                    FaceRegistrationActivity.this,
                    "mobile_face_net.tflite",
                    112,
                    null,
                    false);
        }catch (
    IOException e){
        e.printStackTrace();
        Log.d("registration","Failed to load model");
    }

                captureButton.setOnClickListener(v -> {
                    if (checkCameraPermission()) {
                        openCamera();
                    } else {
                        requestCameraPermission();
                    }
                });

        registerButton.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (!name.isEmpty() && capturedFace != null) {
                faceRecognition.registerNewFace(name, capturedFace);
                //Toast.makeText(this, "Face registered successfully!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Please enter a name and capture a face", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean checkCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
    }

    private void openCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            Bitmap fullImage = (Bitmap) extras.get("data");
            if (fullImage != null) {
                try{

                    InputStream inputStream = this.getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
                    File cascadeDir=this.getDir("cascade", Context.MODE_PRIVATE);
                    File mCascadeFile= new File(cascadeDir,"haarcascade_frontalface_alt");
                    FileOutputStream outputStream=new FileOutputStream(mCascadeFile);
                    byte[] buffer=new byte[4096];
                    int byteRead;
                    while((byteRead=inputStream.read(buffer))!=-1){
                        outputStream.write(buffer,0,byteRead);
                    }
                    inputStream.close();
                    outputStream.close();

                    cascadeClassifier=new CascadeClassifier(mCascadeFile.getAbsolutePath());
                    Log.d("face_recognition","Classifier is loaded");
                    Toast.makeText(this, "Classifier is loaded", Toast.LENGTH_SHORT).show();


                }catch (IOException e){
                    e.printStackTrace();
                }



                Mat matImage = new Mat();
                Utils.bitmapToMat(fullImage, matImage);

                // Convert the image to grayscale
                Mat grayImage = new Mat();
                Imgproc.cvtColor(matImage, grayImage, Imgproc.COLOR_BGR2GRAY);

                // Detect faces
                MatOfRect faces = new MatOfRect();
                cascadeClassifier.detectMultiScale(grayImage, faces);

                // Get detected faces
                Rect[] faceArray = faces.toArray();
                if (faceArray.length > 0) {
                    // Assuming you only want the first detected face
                    Rect faceRect = faceArray[0];
                    Mat faceMat = new Mat(matImage, faceRect);

                    // Convert the face Mat to Bitmap
                    Bitmap croppedFace = Bitmap.createBitmap(faceMat.cols(), faceMat.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(faceMat, croppedFace);

                    // Update the capturedFace with the cropped face
                    capturedFace = croppedFace;
                    facePreview.setImageBitmap(capturedFace);
                }else {
                    Toast.makeText(this, "Try again", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}