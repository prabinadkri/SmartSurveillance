package com.example.surveillance;
import static java.security.AccessController.getContext;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.os.Environment;
import android.telephony.SmsManager;
import android.util.Log;
import android.content.res.Resources;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class face_recognition {
    private Interpreter interpreter;

    private int INPUT_SIZE;
    private int height=0;
    private int width=0;
    private GpuDelegate gpuDelegate=null;
    private CascadeClassifier cascadeClassifier;
    private long startTime = -1;
    private boolean onlyIntruders = true;
    private static final long INTRUDER_DETECTION_SMS_THRESHOLD_MS = 10000; // 10 seconds
    private static final long SMS_COOLDOWN_PERIOD_MS = 3600000; // 1 hour

    private String phoneNumber;
    private boolean smsEnabled;

    private long lastSmsSentTime = 0;
    private int intrudercount=0;
    private static final long INTRUDER_TIME_THRESHOLD = 1000; // 3 seconds in milliseconds
    private Map<String, float[]> knownFaces;
    private Context context;
    face_recognition(AssetManager assetManager, Context context,String modelPath,int input_size,String phonenumber,boolean smsenabled) throws IOException{
        phoneNumber=phonenumber;
        this.context = context;
        smsEnabled=smsenabled;
        INPUT_SIZE=input_size;
        Interpreter.Options options= new Interpreter.Options();
        gpuDelegate=new GpuDelegate();
        //options.addDelegate(gpuDelegate);
        options.setNumThreads(2);
        interpreter=new Interpreter(loadModel(assetManager,modelPath),options);
        Log.d("face_recognition","model loaded");
        try{

            InputStream inputStream = context.getResources().openRawResource(R.raw.haarcascade_frontalface_alt);
            File cascadeDir=context.getDir("cascade",Context.MODE_PRIVATE);
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
            knownFaces = new HashMap<>();
            loadSavedFaces();

        }catch (IOException e){
            e.printStackTrace();
        }
    }
    public void registerNewFace(String name, Bitmap faceBitmap) {

        Bitmap scaledFaceBitmap = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, false);
        float[] faceEmbedding = getFaceEmbedding(scaledFaceBitmap);
        Toast.makeText(context, "Registered successfully", Toast.LENGTH_SHORT).show();
        knownFaces.put(name, faceEmbedding);
        saveFaceEmbedding(name, faceEmbedding);
    }
    private float[] getFaceEmbedding(Bitmap faceBitmap) {
        ByteBuffer inputBuffer = convertBitmapToByteBuffer(faceBitmap);
        float[][] embeddingBuffer = new float[1][192]; // Assuming FaceNet outputs 192-dimensional embeddings
        interpreter.run(inputBuffer, embeddingBuffer);
        return embeddingBuffer[0];
    }
    private void saveFaceEmbedding(String name, float[] embedding) {
        try {
            File file = new File(context.getFilesDir(), name + "_embedding.txt");
            FileOutputStream fos = new FileOutputStream(file);
            StringBuilder sb = new StringBuilder();
            for (float value : embedding) {
                sb.append(value).append(",");
            }
            fos.write(sb.toString().getBytes());
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void loadSavedFaces() {
        File directory = context.getFilesDir();
        File[] files = directory.listFiles((dir, name) -> name.endsWith("_embedding.txt"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName().replace("_embedding.txt", "");
                try {
                    float[] embedding = loadFaceEmbedding(file);
                    knownFaces.put(name, embedding);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Toast.makeText(context, name, Toast.LENGTH_SHORT).show();
            }
        }
    }
    private float[] loadFaceEmbedding(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[(int) file.length()];
        fis.read(buffer);
        fis.close();
        String[] values = new String(buffer).trim().split(",");
        float[] embedding = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            embedding[i] = Float.parseFloat(values[i]);
        }
        return embedding;
    }
    public Mat recognizeImage(Mat mat_image) {
        Core.flip(mat_image.t(), mat_image, 1);

        Mat grayscaleImage = new Mat();
        Imgproc.cvtColor(mat_image, grayscaleImage, Imgproc.COLOR_RGBA2GRAY);
        height = grayscaleImage.height();
        width = grayscaleImage.width();
        int absoluteFaceSize = (int) (height * 0.1);
        MatOfRect faces = new MatOfRect(); // Store all faces

        if (cascadeClassifier != null) {
            cascadeClassifier.detectMultiScale(grayscaleImage, faces, 1.1, 2, 2,
                    new Size(absoluteFaceSize, absoluteFaceSize), new Size());
        }

        Rect[] faceArray = faces.toArray();
        onlyIntruders = true; // Reset for each frame

        for (int i = 0; i < faceArray.length; i++) {
            Imgproc.rectangle(mat_image, faceArray[i].tl(), faceArray[i].br(), new Scalar(0, 255, 0, 255), 2);

            Rect roi = new Rect((int) faceArray[i].tl().x, (int) faceArray[i].tl().y,
                    ((int) faceArray[i].br().x) - ((int) faceArray[i].tl().x),
                    ((int) faceArray[i].br().y) - ((int) faceArray[i].tl().y));

            Mat cropped_rgb = new Mat(mat_image, roi);
            Bitmap bitmap = null;
            bitmap = Bitmap.createBitmap(cropped_rgb.cols(), cropped_rgb.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(cropped_rgb, bitmap);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);
            float[] faceEmbedding = getFaceEmbedding(scaledBitmap);
            String recognizedPerson = recognizeFace(faceEmbedding);

            // Draw rectangle and put text on the image

            Imgproc.putText(mat_image, recognizedPerson, new Point(faceArray[i].tl().x, faceArray[i].tl().y - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.9, new Scalar(255, 255, 255, 255), 2);
            // Check if any non-intruder is detected
            if (!recognizedPerson.equals("Intruder")) {
                onlyIntruders = false;
                startTime = -1; // Reset timer
            }
        }

        Core.flip(mat_image.t(), mat_image, 0);

        // Track time for continuous intruder detection
        if (onlyIntruders && faceArray.length > 0) {
            if (startTime == -1) {
                startTime = System.currentTimeMillis(); // Start timer
            } else {
                long elapsedTime = System.currentTimeMillis() - startTime;
                long elapsedsmsTime=System.currentTimeMillis() - lastSmsSentTime;
                if(intrudercount==9 && elapsedsmsTime>=SMS_COOLDOWN_PERIOD_MS)
                {
                    Log.d("face_recognition","sending accesible");
                    sendSmsIfEnabled();
                    lastSmsSentTime=elapsedTime+startTime;
                    intrudercount=0;

                }
                if (elapsedTime >= INTRUDER_TIME_THRESHOLD) {
                    intrudercount++;
                    if(intrudercount%3==0)
                    {
                        savePhoto(mat_image);
                    }
                    // Trigger photo capture and save


                    startTime = -1; // Reset after saving
                }
            }
        } else {
            intrudercount=0;
            startTime = -1; // Reset timer if intruder condition fails
        }

        return mat_image;
    }
    private String recognizeFace(float[] faceEmbedding) {
        float minDistance = Float.MAX_VALUE;
        String recognizedPerson = "Intruder";

        for (Map.Entry<String, float[]> entry : knownFaces.entrySet()) {
            float distance = calculateDistance(faceEmbedding, entry.getValue());
            if (distance < minDistance) {
                minDistance = distance;
                recognizedPerson = entry.getKey();
            }
        }

        // You may want to set a threshold for recognition
        if (minDistance > 0.9) { // Adjust this threshold as needed
            recognizedPerson = "Intruder";
        }

        return recognizedPerson;
    }
    private float calculateDistance(float[] embedding1, float[] embedding2) {
        float sum = 0;
        for (int i = 0; i < embedding1.length; i++) {
            float diff = embedding1[i] - embedding2[i];
            sum += diff * diff;
        }
        return (float) Math.sqrt(sum);
    }
    private void sendSmsIfEnabled() {


        if (smsEnabled && !phoneNumber.isEmpty()) {

                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(phoneNumber, null, "Intruder detected in Surveillance area!", null, null);
            Toast.makeText(context, "SMS sent", Toast.LENGTH_SHORT).show();
                Log.d("face_recognition", "SMS sent to " + phoneNumber);

        }
    }

    private void savePhoto(Mat mat_image) {
        Mat rgbImage = new Mat();
        Imgproc.cvtColor(mat_image, rgbImage, Imgproc.COLOR_BGR2RGB);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String currentDateandTime = sdf.format(new Date());
        String fileName = "Intruder_" + currentDateandTime + ".jpg";

        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File file = new File(path, fileName);

        Imgcodecs.imwrite(file.getAbsolutePath(), rgbImage);
        Log.d("face_recognition", "Image saved: " + file.getAbsolutePath());
    }





    private ByteBuffer convertBitmapToByteBuffer(Bitmap scaledBitmap) {
        ByteBuffer byteBuffer;
        int input_size=INPUT_SIZE;
        byteBuffer=ByteBuffer.allocateDirect(4*1*input_size*input_size*3);
        byteBuffer.order(ByteOrder.nativeOrder());
        int[] intValues=new int[input_size*input_size];
        scaledBitmap.getPixels(intValues,0,scaledBitmap.getWidth(),0,0,scaledBitmap.getWidth(),
                scaledBitmap.getHeight());
        int pixels=0;
        for(int i=0;i<input_size;i++){
            for(int j=0;j<input_size;j++){
                final int val=intValues[pixels++];
                byteBuffer.putFloat((((val>>16)&0xFF))/255.0f);
                byteBuffer.putFloat((((val>>8)&0xFF))/255.0f);
                byteBuffer.putFloat(((val & 0xFF))/255.0f);
            }
        }
        return byteBuffer;
    }

    private MappedByteBuffer loadModel(AssetManager assetManager, String modelPath) throws IOException{
        AssetFileDescriptor assetFileDescriptor=assetManager.openFd(modelPath);
        FileInputStream inputStream= new FileInputStream(assetFileDescriptor.getFileDescriptor());
        FileChannel fileChannel=inputStream.getChannel();
        long startOffset=assetFileDescriptor.getStartOffset();
        long declaredLength=assetFileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startOffset,declaredLength);
    }
}
