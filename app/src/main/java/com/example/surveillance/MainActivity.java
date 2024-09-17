package com.example.surveillance;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;

import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {
    static {
        if (OpenCVLoader.initLocal()) {
            Log.d("MainActivity: ", "OpenCV loaded successfully");
        } else {
            Log.d("MainActivity: ", "Failed to load OpenCV");
        }
    }
    private Button camera_button;
    private Switch smsSwitch;
    private EditText phoneNumberInput;
    private Button register_face_button;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        smsSwitch = findViewById(R.id.sms_switch);
        phoneNumberInput = findViewById(R.id.phone_number_input);
        camera_button=findViewById(R.id.camera_button);
        register_face_button = findViewById(R.id.register_face);
        SharedPreferences preferences = getSharedPreferences("SurveillancePreferences", MODE_PRIVATE);
        boolean smsEnabled = preferences.getBoolean("smsEnabled", false);
        String phoneNumber = preferences.getString("phoneNumber", "");
        camera_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean("smsEnabled", smsSwitch.isChecked());
                editor.putString("phoneNumber", phoneNumberInput.getText().toString());
                editor.apply();
                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra("smsEnabled", smsSwitch.isChecked());
                intent.putExtra("phoneNumber", phoneNumberInput.getText().toString());
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }
        });
        register_face_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePreferences();
                Intent intent = new Intent(MainActivity.this, FaceRegistrationActivity.class);
                startActivity(intent);
            }
        });


    }

    private void savePreferences() {
        SharedPreferences preferences = getSharedPreferences("SurveillancePreferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("smsEnabled", smsSwitch.isChecked());
        editor.putString("phoneNumber", phoneNumberInput.getText().toString());
        editor.apply();
    }
}